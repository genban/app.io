package controllers

import play.api.i18n.{Langs, MessagesApi}
import play.api.mvc._
import security._

/**
 * @author zepeng.li@gmail.com
 */
object PermCheck {

  def apply(
    resource: String,
    onDenied: (CheckedResource, CheckedAction, RequestHeader) => Result
  )(
    implicit
    langs: Langs,
    messagesApi: MessagesApi
  ): ActionFunction[MaybeUserRequest, UserRequest] = {
    apply(_.Anything, onDenied)(
      CheckedResource(resource), langs, messagesApi
    )
  }

  def apply(
    action: CheckedActions => CheckedAction,
    onDenied: (CheckedResource, CheckedAction, RequestHeader) => Result
    = (_, _, _) => Results.NotFound
  )(
    implicit
    resource: CheckedResource,
    langs: Langs,
    messagesApi: MessagesApi
  ): ActionBuilder[UserRequest] =
    MaybeUserAction andThen
      AuthCheck andThen
      PermissionChecker(action, onDenied, resource)
}