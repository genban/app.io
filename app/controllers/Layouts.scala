package controllers

import helpers._
import models.Groups
import models.sys.SysConfigs
import play.api.i18n.Messages
import play.api.libs.json.Json
import views.html

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

/**
 * @author zepeng.li@gmail.com
 */
object Layouts extends Logging {

  import html.layouts._

  val layout_admin  = Layout(base_admin.getClass.getCanonicalName)
  val layout_normal = Layout(base_normal.getClass.getCanonicalName)

  def toJson(implicit messages: Messages) =
    Json.prettyPrint(
      Json.obj(
        layout_admin.layout -> messages("layout.admin"),
        layout_normal.layout -> messages("layout.normal"),
        "" -> messages("layout.nothing")
      )
    )

  def init(
    implicit
    _groups: Groups,
    _sysConfig: SysConfigs,
    ec: ExecutionContext
  ): Future[Boolean] = {
    _groups
      .setLayoutIfEmpty(_groups._internalGroups.AnyoneId, layout_admin)
      .andThen {
      case Success(true) => Logger.debug("Initialized layout of Anyone")
    }
  }
}