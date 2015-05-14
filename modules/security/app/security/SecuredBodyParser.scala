package security

import models.User
import play.api.mvc._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class SecuredBodyParser[A](
  action: (CheckedActions) => CheckedAction,
  onUnauthorized: RequestHeader => Result = req => Results.NotFound,
  onPermDenied: RequestHeader => Result = req => Results.NotFound,
  onBaseException: RequestHeader => Result = req => Results.NotFound
)(bodyParser: RequestHeader => User => Future[BodyParser[A]])(
  implicit val resource: CheckedResource
) extends PermissionCheckedBodyParser[A] {

  override def parser(req: RequestHeader)(
    implicit user: User
  ): Future[BodyParser[A]] = bodyParser(req)(user)
}