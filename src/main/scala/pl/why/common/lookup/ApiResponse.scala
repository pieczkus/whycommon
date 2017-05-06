package pl.why.common.lookup

import pl.why.common.{BaseJsonProtocol, ErrorMessage}
import spray.json._

case class ApiResponse[T](response: Option[T] = None)

trait ApiResponseJsonProtocol extends BaseJsonProtocol {
  implicit val errorMessageFormat: RootJsonFormat[ErrorMessage] = jsonFormat3(ErrorMessage.apply)

  implicit def apiRespFormat[T: JsonFormat]: RootJsonFormat[ApiResponse[T]] = jsonFormat1(ApiResponse.apply[T])
}
