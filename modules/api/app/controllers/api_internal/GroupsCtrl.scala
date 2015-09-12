package controllers.api_internal

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import controllers.RateLimitConfig
import elasticsearch._
import helpers._
import models._
import models.sys.SysConfigs
import play.api.i18n._
import play.api.libs.json._
import play.api.mvc.Controller
import protocols.JsonProtocol._
import protocols._
import security._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
class GroupsCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val userActionRequired: UserActionRequired,
  val es: ElasticSearch,
  val sysConfig: SysConfigs,
  val _groups: Groups
)
  extends Secured(GroupsCtrl)
  with Controller
  with LinkHeader
  with BasicPlayComponents
  with UserActionComponents
  with DefaultPlayExecutor
  with RateLimitConfig
  with I18nSupport
  with Logging {

  def index(ids: Seq[UUID], q: Option[String], p: Pager) =
    UserAction(_.Index).async { implicit req =>
      if (ids.nonEmpty)
        _groups.find(ids).map { grps =>
          Ok(Json.toJson(grps))
        }
      else
        (es.Search(q, p) in _groups future()).map { page =>
          Ok(page).withHeaders(
            linkHeader(page, routes.GroupsCtrl.index(Nil, q, _))
          )
        }
    }

  def show(id: UUID) =
    UserAction(_.Show).async { implicit req =>
      _groups.find(id).map {
        NotModifiedOrElse { grp =>
          Ok(Json.toJson(grp))
        }
      }.recover {
        case e: BaseException => NotFound
      }
    }

  def create =
    UserAction(_.Save).async { implicit req =>
      BindJson.and(
        Json.obj(
          "id" -> UUIDs.timeBased(),
          "is_internal" -> false
        )
      ).as[Group] {
        success => for {
          saved <- success.save
          _resp <- es.Index(saved) into _groups
        } yield
          Created(_resp._1)
            .withHeaders(
              LOCATION -> routes.GroupsCtrl.show(saved.id).url
            )
      }
    }

  def destroy(id: UUID) =
    UserAction(_.Destroy).async { implicit req =>
      (for {
        ___ <- es.Delete(id) from _groups
        grp <- _groups.find(id)
        ___ <- _groups.remove(id)
      } yield grp).map { _ =>
        NoContent
      }.recover {
        case e: Group.NotWritable =>
          MethodNotAllowed(JsonMessage(e))
        case e: Group.NotEmpty    =>
          MethodNotAllowed(JsonMessage(e))
        case e: Group.NotFound    =>
          NotFound
      }
    }

  def save(id: UUID) =
    UserAction(_.Save).async { implicit req =>
      BindJson().as[Group] { grp =>
        (for {
          _____ <- _groups.find(id)
          saved <- grp.save
          _resp <- es.Update(saved) in _groups
        } yield _resp._1).map {
          Ok(_)
        }.recover {
          case e: BaseException => NotFound
        }
      }
    }

  def users(id: UUID, pager: Pager) =
    UserAction(_.Show).async { implicit req =>
      (for {
        page <- _groups.children(id, pager)
        usrs <- _users.find(page)
      } yield (page, usrs)).map { case (page, usrs) =>
        Ok(Json.toJson(usrs))
          .withHeaders(linkHeader(page, routes.GroupsCtrl.users(id, _)))
      }.recover {
        case e: BaseException => NotFound
      }
    }

  def addUser(id: UUID) =
    UserAction(_.Save).async { implicit req =>
      BodyIsJsObject { obj =>
        obj.validate[UUID](User.idReads).map(_users.find)
          .orElse(
            obj.validate[String](User.emailReads).map(_users.find)
          ).map {
          _.flatMap { user =>
            if (user.groups.contains(id)) Future.successful {
              Ok(Json.toJson(user))
            }
            else _groups.addChild(id, user.id).map { _ =>
              Created(Json.toJson(user))
            }
          }
        }.fold(
            failure => Future.successful {
              UnprocessableEntity(JsonClientErrors(failure))
            },
            success => success
          )
          .recover {
          case e: User.NotFound => NotFound(JsonMessage(e))
        }
      }
    }

  def delUser(id: UUID, uid: UUID) =
    UserAction(_.Destroy).async { implicit req =>
      _groups.delChild(id, uid).map { _ => NoContent }
    }

  def layouts(ids: Seq[UUID]) =
    UserAction(_.Index).async { implicit req =>
      _groups.findLayouts(ids).map { layouts =>
        val map = layouts.collect {
          case (gid, layout) if layout.isDefined =>
            gid.toString -> layout.get
        }.toMap
        Ok(Json.toJson(map))
      }
    }

  def setLayout(gid: UUID) =
    UserAction(_.Save).async { implicit req =>
      BindJson().as[Layout] { success =>
        _groups.setLayout(gid, success).map { saved =>
          Ok(Json.toJson(saved))
        }
      }.recover {
        case e: BaseException => NotFound(JsonMessage(e))
      }
    }
}

object GroupsCtrl extends Secured(Group)