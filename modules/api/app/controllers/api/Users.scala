package controllers.api

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import elasticsearch.ES
import helpers._
import models._
import models.json._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object Users
  extends SecuredController(User)
  with LinkHeader {

  def groups(id: UUID, options: Option[String]) =
    PermCheck(_.Show).async { implicit req =>
      (for {
        user <- User.find(id)
        grps <- Group.find(
          options match {
            case Some("internal") => Set.empty union user.int_groups
            case Some("external") => user.ext_groups
            case _                => user.groups
          }
        )
      } yield grps).map { grps =>
        Ok(Json.toJson(grps.filter(_.id != InternalGroups.AnyoneId)))
      }.recover {
        case e: BaseException => NotFound
      }
    }

  def index(ids: Seq[UUID], q: Option[String], p: Pager) =
    PermCheck(_.Index).async { implicit req =>
      if (ids.nonEmpty)
        User.find(ids).map { usrs =>
          Ok(JsArray(usrs.map(_.toJson)))
        }
      else
        (ES.Search(q, p) in User future()).map { page =>
          Ok(page).withHeaders(
            linkHeader(page, routes.Users.index(Nil, q, _))
          )
        }
    }

  def create =
    PermCheck(_.Save).async { implicit req =>
      BindJson.and(
        Json.obj(
          "id" -> UUIDs.timeBased(),
          "int_groups" -> 0,
          "ext_groups" -> JsArray()
        )
      ).as[JsUser] {
        success => (for {
          saved <- success.toUser.save
          _resp <- ES.Index(saved) into User
        } yield (saved, _resp)).map { case (saved, _resp) =>
          Created(_resp._1)
            .withHeaders(
              LOCATION -> routes.Groups.show(saved.id).url
            )
        }.recover {
          case e: User.EmailTaken => MethodNotAllowed(JsonMessage(e))
        }
      }
    }

  def dropIndexIfEmpty: Future[Boolean] = for {
    _empty <- User.isEmpty
    result <-
    if (_empty) {
      Logger.info(s"Clean elasticsearch index $moduleName")
      (ES.Delete from User).map(_ => true)
    }
    else
      Future.successful(false)
  } yield result

}