package controllers.api

import java.util.UUID

import elasticsearch._
import helpers._
import models.AccessControl.NotFound
import models._
import play.api.i18n._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json._
import protocols.JsonProtocol._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
class AccessControls(
  val basicPlayApi: BasicPlayApi
)
  extends SecuredController(AccessControl)
  with BasicPlayComponents
  with I18nSupport
  with LinkHeader {

  def index(q: Option[String], p: Pager) =
    PermCheck(_.Index).async { implicit req =>
      (ES.Search(q, p) in AccessControl future()).map { page =>
        Ok(page).withHeaders(
          linkHeader(page, routes.Groups.index(Nil, q, _))
        )
      }
    }

  def show(id: UUID, res: String, act: String) =
    PermCheck(_.Show).async { implicit req =>
      AccessControl.find(id, res, act).map { ac =>
        Ok(Json.toJson(ac))
      }.recover {
        case e: BaseException => NotFound
      }
    }

  def create =
    PermCheck(_.Create).async { implicit req =>
      BindJson().as[AccessControl] { success =>
        AccessControl.find(success).map { found =>
          Ok(Json.toJson(found))
        }.recoverWith { case e: NotFound =>
          (for {
            exists <-
            if (!success.is_group) User.exists(success.principal)
            else Group.exists(success.principal)

            saved <- success.save
            _resp <- ES.Index(saved) into AccessControl
          } yield (saved, _resp)).map { case (saved, _resp) =>

            Created(_resp._1)
              .withHeaders(
                LOCATION -> routes.AccessControls.show(
                  saved.principal, saved.resource, saved.action
                ).url
              )
          }.recover {
            case e: User.NotFound  => BadRequest(JsonMessage(e))
            case e: Group.NotFound => BadRequest(JsonMessage(e))
          }
        }
      }
    }

  def destroy(id: UUID, res: String, act: String) =
    PermCheck(_.Destroy).async { implicit req =>
      (for {
        __ <- ES.Delete(AccessControl.genId(res, act, id)) from AccessControl
        ac <- AccessControl.find(id, res, act)
        __ <- AccessControl.remove(id, res, act)
      } yield res).map { _ =>
        NoContent
      }.recover {
        case e: AccessControl.NotFound => NotFound
      }
    }

  def save(id: UUID, res: String, act: String) =
    PermCheck(_.Save).async { implicit req =>
      BindJson().as[AccessControl] { ac =>
        (for {
          _____ <- AccessControl.find(id, res, act)
          saved <- ac.save
          _resp <- ES.Update(saved) in AccessControl
        } yield _resp._1).map {
          Ok(_)
        }.recover {
          case e: BaseException => NotFound
        }

      }
    }

  def dropIndexIfEmpty: Future[Boolean] = for {
    _empty <- AccessControl.isEmpty
    result <-
    if (_empty) {
      Logger.info(s"Clean elasticsearch index $moduleName")
      (ES.Delete from AccessControl).map(_ => true)
    }
    else
      Future.successful(false)
  } yield result
}

object AccessControls extends SecuredController(AccessControl)