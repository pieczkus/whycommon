package pl.why.common.lookup

import spray.json._
import akka.http.scaladsl.model._
import akka.http.scaladsl.Http
import akka.stream._
import akka.http.scaladsl.unmarshalling._
import pl.why.common.CommonActor

import scala.concurrent.Future

case class ServiceLookupResult(name: String, uriOpt: Option[String])

trait ServiceConsumer extends ApiResponseJsonProtocol {
  me: CommonActor =>

  private val configPrefix = "service.uri."
  implicit val httpMater = ActorMaterializer()
  lazy val http = Http(context.system)

  def lookupService(serviceName: String): Future[ServiceLookupResult] = {
    val serviceUrl = context.system.settings.config.getString(configPrefix + serviceName)
    if (serviceUrl != null && serviceUrl != "") {
      log.info("Successfully looked up service {}, uri is: {}", serviceName, serviceUrl)
      Future.successful(ServiceLookupResult(serviceName, Some(serviceUrl)))
    } else {
      log.error("Non successful service lookup for service {}", serviceName)
      Future.failed(new IllegalArgumentException(s"No such service configured $serviceName"))
    }
  }

  def executeHttpRequest[T: RootJsonFormat](request: HttpRequest): Future[T] = {
    import context.dispatcher
    for {
      resp <- http.singleRequest(request).flatMap(successOnly(request))
      entity <- Unmarshal(resp.entity).to[ApiResponse[T]]
      if entity.response.isDefined
    } yield entity.response.get
  }

  def successOnly(req: HttpRequest)(resp: HttpResponse): Future[HttpResponse] = {
    if (resp.status.isSuccess) Future.successful(resp)
    else Future.failed(new RuntimeException(s"Non successful http status code of ${resp.status} received for request to uri: ${req.uri}"))
  }

}
