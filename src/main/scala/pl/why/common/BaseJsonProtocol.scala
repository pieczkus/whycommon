package pl.why.common

import java.util.Date

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

trait BaseJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {

  implicit object DateFormat extends JsonFormat[Date] {
    def write(date: Date): JsValue = JsNumber(date.getTime)

    def read(json: JsValue): Date = json match {
      case JsNumber(epoch) => new Date(epoch.toLong)
      case unknown => deserializationError(s"Expected JsString, got $unknown")
    }
  }

}
