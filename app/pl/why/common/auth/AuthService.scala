package pl.why.common.auth

import javax.inject.Inject

import akka.NotUsed
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{Flow, Sink, Source}
import pl.why.common.auth.AuthService.{ValidationResult, VerifyToken}
import pl.why.common.CommonActor
import pl.why.common.lookup.ServiceConsumer
import play.api.Configuration

import scala.concurrent.Future
import scala.util.{Failure, Success}


object AuthService {

  val Name = "auth-service"

  case class VerifyToken(token: String)

  case class ValidationResult(valid: Boolean)

}

class AuthService @Inject()(configuration: Configuration) extends CommonActor with ServiceConsumer with AuthJsonProtocol {

  private val authHost = configuration.underlying.getString("services.authentication.host")
  private val authPort = configuration.underlying.getInt("services.authentication.port")
  private val authPath = configuration.underlying.getString("services.authentication.authpath")

  private val authorizeClientFlow = Http()(context.system).cachedHostConnectionPool[String](authHost, authPort)

  val authorizeFlow: Flow[String, ValidationResult, NotUsed] = Flow[String]
    .map(toAuthorizeRequest)
    .via(authorizeClientFlow)
    .mapAsync(1) {
      case (Success(response), token) =>
        if (response.status.isSuccess()) {
          Unmarshal(response.entity).to[ValidationResult]
        } else {
          response.discardEntityBytes()
          val message = s"Non successful token verification: $token"
          log.error(message)
          Future.failed(new IllegalStateException(message))
        }
      case (Failure(ex), token) =>
        val message = s"Non successful token verification: $token failed with $ex"
        log.error(message)
        Future.failed(new IllegalStateException(message))
    }

  override def receive: Receive = {
    case VerifyToken(token) =>
      val caller = sender()
      Source.single(token).via(authorizeFlow).runWith(Sink.actorRef(caller, None))

  }

  private def toAuthorizeRequest(token: String): (HttpRequest, String) = {
    val requestUri = Uri().withPath(Uri.Path(authPath))
    HttpRequest(HttpMethods.POST, requestUri).withHeaders(
      RawHeader("Authorization", "Bearer " + token)) -> token
  }

}