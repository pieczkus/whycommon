package pl.why.common

import java.util.UUID

import akka.NotUsed
import akka.actor.ActorRef
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query._
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import akka.util.Timeout
import pl.why.common.resumable.ResumableOffset
import pl.why.common.resumable.ResumableProjectionManager.{FindLatestOffset, StoreLatestOffset}
import spray.json.JsonFormat

import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.util.control.NonFatal

trait ReadModelObject extends AnyRef {
  def id: String
}

object ViewBuilder {

  sealed trait IndexAction

  case class UpdateAction(id: String, params: Map[String, Any]) extends IndexAction

  case class EnvelopeAndAction(env: EventEnvelope, action: IndexAction)

  case class EnvelopeAndFunction(env: EventEnvelope, f: () => Future[Any])

  case class InsertAction(id: String, rm: ReadModelObject) extends IndexAction

  case class NoAction(id: String) extends IndexAction

  case class DeferredCreate(flow: Flow[EnvelopeAndAction, EnvelopeAndAction, akka.NotUsed]) extends IndexAction

  case class LatestOffsetResult(offset: Option[String])

}

abstract class ViewBuilder[RM <: ReadModelObject : ClassTag](resumableProjectionManager: ActorRef) extends CommonActor with ElasticSearchSupport {

  import ViewBuilder._
  import akka.pattern.{ask, pipe}
  import context.dispatcher

  import scala.concurrent.duration._

  val journal: CassandraReadJournal = PersistenceQuery(context.system).
    readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  val decider: Supervision.Decider = {
    case NonFatal(ex) =>
      log.error(ex, "Got non fatal exception in ViewBuilder flow")
      Supervision.Resume
    case ex =>
      log.error(ex, "Got fatal exception in ViewBuilder flow, stream will be stopped")
      Supervision.Stop
  }

  implicit val materializer = ActorMaterializer(
    ActorMaterializerSettings(context.system).
      withSupervisionStrategy(decider)
  )

  implicit val rmFormats: JsonFormat[RM]
  implicit val timeout: Timeout = 3.seconds

  def projectionId: String

  (resumableProjectionManager ? FindLatestOffset(projectionId))
    .mapTo[ServiceResult[ResumableOffset]]
    .map {
      case FullResult(flo) => LatestOffsetResult(Some(flo.uuid))
      case _ => LatestOffsetResult(None)
    }
    .pipeTo(self)

  def actionFor(id: String, env: EventEnvelope): IndexAction

  val eventsFlow: Flow[EventEnvelope, Boolean, NotUsed] =
    Flow[EventEnvelope].
      map { env =>
        val id = env.persistenceId.toLowerCase()
        EnvelopeAndAction(env, actionFor(id, env))
      }.flatMapConcat {
      case ea@EnvelopeAndAction(env, cr: DeferredCreate) =>
        Source.single(ea).via(cr.flow)

      case ea: EnvelopeAndAction =>
        Source.single(ea).via(Flow[EnvelopeAndAction])
    }.collect {
      case EnvelopeAndAction(env, InsertAction(id, rm: RM)) =>
        EnvelopeAndFunction(env, () => index(id, rm))

      case EnvelopeAndAction(env, u: UpdateAction) =>
        EnvelopeAndFunction(env, () => updateIndex(u.id, u.params))

      case EnvelopeAndAction(env, NoAction(id)) =>
        EnvelopeAndFunction(env, () => updateIndex(id, Map.empty[String, Any]))
    }.mapAsync(1) {
      case EnvelopeAndFunction(env, f) => f.apply.map(_ => env)
    }.mapAsync(1)(env =>
      (resumableProjectionManager ? StoreLatestOffset(projectionId, env.offset.asInstanceOf[TimeBasedUUID].value.toString)).mapTo[ServiceResult[ResumableOffset]]
        .map {
          case FullResult(_) => true
          case _ => false
        }
    )

  def receive: PartialFunction[Any, Unit] = {
    case LatestOffsetResult(o) =>
      var offset: Offset = NoOffset
      val uuid = o.getOrElse("")
      if (uuid == "") {
        clearIndex
      } else {
        offset = TimeBasedUUID(UUID.fromString(uuid))
      }

      log.info("Starting up view builder for entity {} with offset of {}", entityType, offset)
      val eventsSource = journal.eventsByTag(entityType, offset)
      eventsSource.via(eventsFlow).runWith(Sink.ignore)
  }

}
