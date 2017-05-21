package pl.why.common.auth

import javax.inject.Inject

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, Uri}
import pl.why.common.auth.AuthService.{ValidationResult, VerifyToken}
import pl.why.common.CommonActor
import pl.why.common.lookup.ServiceConsumer
import play.api.Configuration

object AuthService {

  val Name = "auth-service"

  case class VerifyToken(token: String)

  case class ValidationResult(valid: Boolean)

}

class AuthService @Inject()(configuration: Configuration) extends CommonActor with ServiceConsumer with AuthJsonProtocol {

  private val authUri = configuration.underlying.getString("services.authentication")
  import akka.pattern.pipe
  import context.dispatcher

  override def receive: Receive = {
    case VerifyToken(token) =>
      val requestUri = Uri(authUri).withPath(Uri.Path("/v1/auth/authorize"))
      val s = sender()
      executeHttpRequest[ValidationResult](
        HttpRequest(HttpMethods.POST, requestUri).withHeaders(
          RawHeader("Authorization", "Bearer " + token),
          RawHeader("Csrf-Token", "nocheck"))).pipeTo(s)

  }
}