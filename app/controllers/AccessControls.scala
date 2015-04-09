package controllers

import java.util.UUID

import controllers.session.UserAction
import helpers._
import models._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{Messages => MSG}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.mvc.{Controller, Result}
import security.PermCheck
import views.{AlertLevel, html}

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object AccessControls extends Controller with Logging {

  override val module_name: String = "controllers.access_controls"

  implicit val access_control_writes = Json.writes[AccessControl]

  val AccessControlFM = Form[AccessControl](
    mapping(
      "resource" -> nonEmptyText(6, 255),
      "action" -> nonEmptyText(6, 255),
      "principal" -> of[UUID],
      "is_group" -> boolean,
      "granted" -> boolean
    )(AccessControl.apply)(AccessControl.unapply)
  )

  def index(pager: Pager) = (UserAction >> PermCheck(
    module_name, "index",
    onDenied = req => Forbidden
  )).async { implicit req =>
    index0(pager, AccessControlFM)
  }

  def save(
    principal: UUID, resource: String, action: String, is_group: Boolean
  ) = (UserAction >> PermCheck(
    module_name, "save",
    onDenied = req => Forbidden
  )).async { implicit req =>
    val form = Form(single("value" -> boolean))
    form.bindFromRequest().fold(
      failure => Future.successful(Forbidden(failure.errorsAsJson)),
      success => AccessControl(
        resource, action, principal, is_group, success
      ).save.map { ac => Ok(Json.obj("value" -> ac.granted)) }
    )
  }

  def destroy(
    principal: UUID, resource: String, action: String, is_group: Boolean
  ) = (UserAction >> PermCheck(
    module_name, "destroy",
    onDenied = req => Forbidden
  )).async { implicit req =>
    AccessControl.remove(principal, resource, action, is_group).map { _ =>
      RedirectToPreviousURI
        .getOrElse(
          Redirect(routes.AccessControls.index())
        ).flashing(
          AlertLevel.Success -> MSG(s"$module_name.entry.deleted")
        )
    }
  }

  def create(pager: Pager) = (UserAction >> PermCheck(
    module_name, "create",
    onDenied = req => Forbidden
  )).async { implicit req =>
    val bound = AccessControlFM.bindFromRequest()
    bound.fold(
      failure => index0(pager, bound),
      success => success.save.flatMap { saved =>
        index0(
          pager, AccessControlFM,
          AlertLevel.Success -> MSG(s"$module_name.entry.created")
        )
      }
    )
  }

  private def index0(
    pager: Pager,
    fm: Form[AccessControl],
    flash: (String, String)*
  )(implicit req: UserRequest[_]): Future[Result] = {
    (for {
      list <- AccessControl.list(pager)
      usrs <- User.find(list.filter(!_.is_group).map(_.principal))
      grps <- Group.find(list.filter(_.is_group).map(_.principal))
    } yield (list, usrs, grps)).map { case (l, u, g) =>
      Ok(
        html.access_controls.index(Page(pager, l), u, g, fm)
      ).flashing(flash: _*)
    }.recover {
      case e: BaseException => NotFound
    }
  }
}