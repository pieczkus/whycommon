package pl.why.common.auth

import javax.inject.{Inject, Named}

import akka.actor.ActorRef
import akka.util.Timeout
import pl.why.common.auth.AuthService.{ValidationResult, VerifyToken}
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

case class AuthorizedRequest[A](request: Request[A]) extends WrappedRequest(request)

object AuthorizedAction {
  final val API_KEY_HEADER = "Why-Key"
}

class AuthorizedAction @Inject()(@Named("auth-service") authService: ActorRef, parser: BodyParsers.Default)(implicit ec: ExecutionContext)
  extends ActionBuilderImpl(parser) {

  import akka.pattern.ask
  import scala.concurrent.duration._

  implicit val timeout = Timeout(5.seconds)

  override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]): Future[Result] = {
    request.headers.get("Authorization") match {
      case Some(token) => (authService ? VerifyToken(token.replace("Bearer ", ""))).mapTo[ValidationResult].flatMap {
        case vr: ValidationResult if vr.valid =>
          val req = AuthorizedRequest(request)
          block(req)
        case _ => Future.successful(Results.Unauthorized)
      }
      case None => Future.successful(Results.Unauthorized)
    }
  }

}
