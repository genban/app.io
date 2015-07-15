package controllers.api

import java.util.UUID

import elasticsearch._
import helpers._
import models._
import play.api.i18n._
import play.api.libs.json._
import play.api.mvc.Controller
import protocols.JsonProtocol._

/**
 * @author zepeng.li@gmail.com
 */
class AccessControlsCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val permCheckRequired: PermCheckRequired,
  val es: ElasticSearch,
  val _groups: Groups
)
  extends Secured(AccessControlsCtrl)
  with Controller
  with LinkHeader
  with BasicPlayComponents
  with PermCheckComponents
  with DefaultPlayExecutor
  with I18nSupport
  with Logging {

  ESIndexCleaner(_accessControls).dropIndexIfEmpty

  def index(q: Option[String], p: Pager) =
    PermCheck(_.Index).async { implicit req =>
      (es.Search(q, p) in _accessControls future()).map { page =>
        Ok(page).withHeaders(
          linkHeader(page, routes.AccessControlsCtrl.index(q, _))
        )
      }
    }

  def show(id: UUID, res: String, act: String) =
    PermCheck(_.Show).async { implicit req =>
      _accessControls.find(id, res, act).map { ac =>
        Ok(Json.toJson(ac))
      }.recover {
        case e: BaseException => NotFound
      }
    }

  def create =
    PermCheck(_.Create).async { implicit req =>
      BindJson().as[AccessControl] { success =>
        _accessControls.find(success).map { found =>
          Ok(Json.toJson(found))
        }.recoverWith {
          case e: AccessControl.NotFound =>
            (for {
              exists <-
              if (!success.is_group) _users.exists(success.principal)
              else _groups.exists(success.principal)

              saved <- success.save
              _resp <- es.Index(saved) into _accessControls
            } yield (saved, _resp)).map { case (saved, _resp) =>

              Created(_resp._1)
                .withHeaders(
                  LOCATION -> routes.AccessControlsCtrl.show(
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
        __ <- es.Delete(AccessControl.genId(res, act, id)) from _accessControls
        ac <- _accessControls.find(id, res, act)
        __ <- _accessControls.remove(id, res, act)
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
          _____ <- _accessControls.find(id, res, act)
          saved <- ac.save
          _resp <- es.Update(saved) in _accessControls
        } yield _resp._1).map {
          Ok(_)
        }.recover {
          case e: BaseException => NotFound
        }

      }
    }
}

object AccessControlsCtrl extends Secured(AccessControl)