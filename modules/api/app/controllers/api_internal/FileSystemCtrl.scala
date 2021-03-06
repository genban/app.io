package controllers.api_internal

import java.util.UUID

import controllers._
import helpers._
import models.cfs._
import models.misc._
import play.api.i18n._
import play.api.mvc._
import protocols.JsonProtocol.JsonMessage
import protocols._
import security.FileSystemAccessControl._
import security._
import services._

import scala.language.postfixOps
import scala.util.Failure

/**
 * @author zepeng.li@gmail.com
 */
class FileSystemCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val userActionRequired: UserActionRequired,
  val bandwidth: BandwidthService,
  val _cfs: CassandraFileSystem
) extends CassandraFileSystemCanonicalNamed
  with CheckedModuleName
  with Controller
  with LinkHeader
  with BasicPlayComponents
  with AuthenticateBySessionComponents
  with UserActionRequiredComponents
  with UserActionComponents[FileSystemCtrl.AccessDef]
  with FileSystemCtrl.AccessDef
  with ExceptionHandlers
  with DefaultPlayExecutor
  with AppConfigComponents
  with RateLimitConfigComponents
  with BandwidthConfigComponents
  with I18nLoggingComponents
  with I18nSupport {

  def index(path: Path, pager: Pager) =
    UserAction(_.P03).async { implicit req =>
      (path match {
        case Path.root =>
          _cfs.listRoot(pager)
        case _         => for {
          curr <- _cfs.dir(path) if curr.rx.?
          page <- curr.list(pager)
        } yield page
      }).map { page =>
        Ok(page).withHeaders(
          linkHeader(page, routes.FileSystemCtrl.index(path, _))
        )
      }.recover {
        case e: FileSystemAccessControl.Denied => Forbidden(JsonMessage(e))
      }
    }

  def test(path: Path) =
    UserAction(_.P02).async { implicit req =>
      FlowJs().bindFromQueryString.test(path)
    }

  def create(path: Path) =
    UserUploadingToCFS(_.P01)(_ => path) { implicit req =>
      (req.body.file("file").map(_.ref) match {
        case Some(file) => FlowJs().bindFromRequestBody.upload(file, path)()
        case None       => throw CFSBodyParser.MissingFile()
      }).andThen {
        case Failure(e: CFSBodyParser.MissingFile) => Logger.warn(e.reason, e)
      }.recover {
        case e: CFSBodyParser.MissingFile => Results.BadRequest(JsonMessage(e))
      }
    }

  def destroy(path: Path, clear: Boolean) =
    UserAction(_.P06).async { implicit req =>
      (path.filename match {
        case Some(_) => for {
          file <- _cfs.file(path) if file.w.?
          ____ <- file.delete()
        } yield Unit
        case None    => for {
          dir <- _cfs.dir(path) if dir.w.?
          ___ <- {
            if (clear) dir.clear(checker = _.w.?)
            else dir.delete(recursive = true, checker = _.w.?)
          }
        } yield Unit
      }).map {
        _ => NoContent
      }.recover {
        case e: FileSystemAccessControl.Denied => Forbidden(JsonMessage(e))
      }
    }

  def updatePermission(path: Path, gid: Option[UUID], pos: Int) =
    UserAction(_.P16).async(BodyParsers.parse.empty) { implicit req =>
      for {
        inode <- _cfs.inode(path)
        _ <- gid match {
          case Some(g) =>
            val perm = inode.ext_permission(g).toggle(pos)
            if (perm.isEmpty) inode.deletePerm(g)
            else inode.savePerm(g, perm)
          case None    =>
            inode.savePerm(inode.permission.toggle(pos))
        }
      } yield {
        Ok
      }
    }

  def deletePermission(path: Path, gid: UUID) =
    UserAction(_.P16).async { implicit req =>
      for {
        inode <- _cfs.inode(path)
        _ <- inode.deletePerm(gid)
      } yield {
        Ok
      }
    }
}

object FileSystemCtrl
  extends CassandraFileSystemCanonicalNamed
    with PermissionCheckable {

  import ModulesAccessControl._

  trait AccessDef extends BasicAccessDef {

    /** Save Permission */
    val P16 = Access.Pos(16)

    def values = Seq(P01, P02, P03, P06, P16)
  }

  object AccessDef extends AccessDef
}