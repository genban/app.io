package controllers.api_internal

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import controllers.RateLimitConfig
import elasticsearch._
import helpers._
import models._
import play.api.i18n._
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.mvc.Controller
import protocols.JsonProtocol._
import protocols._
import security._

/**
 * @author zepeng.li@gmail.com
 */
class UsersCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val userActionRequired: UserActionRequired,
  val es: ElasticSearch,
  val _groups: Groups
)
  extends Secured(UsersCtrl)
  with Controller
  with LinkHeader
  with BasicPlayComponents
  with InternalGroupsComponents
  with UserActionComponents
  with DefaultPlayExecutor
  with RateLimitConfig
  with I18nSupport
  with Logging {

  ESIndexCleaner(_users).dropIndexIfEmpty

  def show(id: UUID) =
    UserAction(_.Show).async { implicit req =>
      _users.find(id).map { user =>
        Ok(Json.toJson(user))
      }.recover {
        case e: User.NotFound => NotFound
      }
    }

  def groups(id: UUID, options: Option[String]) =
    UserAction(_.Show).async { implicit req =>
      (for {
        user <- _users.find(id)
        grps <- _groups.find(
          options match {
            case Some("internal") => user.internal_groups
            case Some("external") => user.external_groups
            case _                => user.groups
          }
        )
      } yield grps).map { grps =>
        Ok(Json.toJson(grps.filter(_.id != _internalGroups.AnyoneId)))
      }.recover {
        case e: BaseException => NotFound
      }
    }

  def index(ids: Seq[UUID], q: Option[String], p: Pager) =
    UserAction(_.Index).async { implicit req =>
      if (ids.nonEmpty)
        _users.find(ids).map { usrs =>
          Ok(Json.toJson(usrs))
        }
      else
        (es.Search(q, p) in _users future()).map { page =>
          Ok(page).withHeaders(
            linkHeader(page, routes.UsersCtrl.index(Nil, q, _))
          )
        }
    }

  def create =
    UserAction(_.Save).async { implicit req =>
      BindJson().as[UserInfo] {
        success => (for {
          saved <- User().copy(
            id = success.id.getOrElse(UUIDs.timeBased),
            email = success.email,
            name = success.name.getOrElse("")
          ).save
          _resp <- es.Index(saved) into _users
        } yield (saved, _resp)).map { case (saved, _resp) =>
          Created(_resp._1)
            .withHeaders(
              LOCATION -> routes.GroupsCtrl.show(saved.id).url
            )
        }.recover {
          case e: User.EmailTaken => MethodNotAllowed(JsonMessage(e))
        }
      }
    }

  case class UserInfo(id: Option[UUID], email: String, name: Option[String])

  object UserInfo {

    implicit val jsonReads: Reads[UserInfo] =
      (JsonReads.optionalIdReads('uid) ~
        User.emailReads ~
        JsonReads.optionalNameReads())(UserInfo.apply _)
  }

}

object UsersCtrl extends Secured(User)