package controllers

import controllers.api.Secured
import helpers._
import models.cfs._
import play.api.i18n._
import play.api.mvc.Controller
import views._

/**
 * @author zepeng.li@gmail.com
 */
class FileSystemCtrl(
  implicit
  val _basicPlayApi: BasicPlayApi,
  val _permCheckRequired: PermCheckRequired,
  val _cfs: CassandraFileSystem
)
  extends Secured(FileSystemCtrl)
  with Controller
  with BasicPlayComponents
  with PermCheckComponents
  with DefaultPlayExecutor
  with I18nSupport {

  def index(path: Path, pager: Pager) =
    PermCheck(_.Index).apply { implicit req =>
      Ok(html.file_system.index(path, pager))
    }

  def show(path: Path) =
    PermCheck(_.Show).async { implicit req =>
      (for {
        home <- _cfs.home(req.user)
        file <- home.file(path)
      } yield file).map { file =>
        Ok(html.file_system.show(path, file))
      }.recover {
        case e: BaseException => NotFound(e.reason)
      }
    }
}

object FileSystemCtrl
  extends Secured(CassandraFileSystem)
  with CanonicalNameBasedMessages
  with ViewMessages