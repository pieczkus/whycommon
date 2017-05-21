package pl.why.common

import akka.actor.{Actor, ActorLogging}

import scala.concurrent.Future

trait CommonActor extends Actor with ActorLogging {

  import akka.pattern.pipe
  import context.dispatcher

  private val toFailure: PartialFunction[Throwable, ServiceResult[Nothing]] = {
    case ex => Failure(FailureType.Service, ServiceResult.UnexpectedFailure, Some(ex))
  }

  def pipeResponse[T](f: Future[T]): Unit =
    f.map {
      case o: Option[_] => ServiceResult.fromOption(o)
      case f: Failure => f
      case other => FullResult(other)
    }.recover(toFailure).pipeTo(sender())
}
