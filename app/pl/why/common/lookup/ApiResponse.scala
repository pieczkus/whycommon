package pl.why.common.lookup

import pl.why.common.{BaseJsonProtocol, ErrorMessage}
import spray.json._

trait ApiResponseJsonProtocol extends BaseJsonProtocol {

  implicit val errorMessageFormat: RootJsonFormat[ErrorMessage] = jsonFormat3(ErrorMessage.apply)
}
