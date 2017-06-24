package pl.why.common

import akka.actor.{ActorLogging, ActorSystem, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion.Passivate
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.persistence.{PersistentActor, RecoveryCompleted, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}

import scala.reflect.ClassTag


trait EntityCommand {

  def entityId: String
}

trait EntityEvent extends Serializable with DataModelWriter {
  def entityType: String
}

object PersistentEntity {

  case object StopEntity

  case class GetState(id: String) extends EntityCommand {
    def entityId: String = id
  }

  case class MarkAsDeleted(id: String) extends EntityCommand {
    def entityId: String = id
  }

  class PersistentEntityIdExtractor(maxShards: Int) {
    val extractEntityId: ShardRegion.ExtractEntityId = {
      case ec: EntityCommand => (ec.entityId, ec)
    }

    val extractShardId: ShardRegion.ExtractShardId = {
      case ec: EntityCommand =>
        (math.abs(ec.entityId.hashCode) % maxShards).toString
    }
  }

  object PersistentEntityIdExtractor {


    def apply(system: ActorSystem): PersistentEntityIdExtractor = {
      val maxShards = system.settings.config.getInt("maxShards")
      new PersistentEntityIdExtractor(maxShards)
    }
  }

}

abstract class PersistentEntity[FO <: EntityFieldsObject[String, FO] : ClassTag] extends PersistentActor with ActorLogging {

  import PersistentEntity._

  import concurrent.duration._

  val id: String = self.path.name
  val entityType: String = getClass.getSimpleName
  var state: FO = initialState
  var eventsSinceLastSnapshot = 0

  context.setReceiveTimeout(5.minute)

  override def persistenceId = id

  def receiveRecover: PartialFunction[Any, Unit] = standardRecover orElse customRecover

  def standardRecover: Receive = {

    //For any entity event, just call handleEvent
    case ev: EntityEvent =>
      log.info("Recovering persisted event: {}", ev)
      handleEvent(ev)
      eventsSinceLastSnapshot += 1

    case SnapshotOffer(_, snapshot: FO) =>
      log.info("Recovering entity with a snapshot: {}", snapshot)
      state = snapshot

    case RecoveryCompleted =>
      log.debug("Recovery completed for {} entity with id {}", entityType, id)

  }

  def customRecover: Receive = PartialFunction.empty

  def receiveCommand: PartialFunction[Any, Unit] = standardCommandHandling orElse additionalCommandHandling

  private def standardCommandHandling: Receive = {

    //Have been idle too long, time to start the passivation process
    case ReceiveTimeout =>
      log.info("{} entity with id {} is being passivated due to inactivity", entityType, id)
      context.parent ! Passivate(stopMessage = StopEntity)

    //Finishes the two part passivation process by stopping the entity
    case StopEntity =>
      log.info("{} entity with id {} is now being stopped due to inactivity", entityType, id)
      context stop self

    //Don't allow actions on deleted entities or a non-create request
    //when in the initial state
    case any if !isAcceptingCommand(any) =>
      log.warning("Not allowing action {} on a deleted entity or an entity in the initial state with id {}", any, id)
      sender() ! stateResponse()

    //Standard request to get the current state of the entity instance
    case GetState(_) =>
      sender ! stateResponse()

    //Standard handling logic for a request to mark the entity instance  as deleted
    case MarkAsDeleted =>
      //Only if a delete event is defined do we perform the delete.  This
      //allows some entities to not support deletion
      newDeleteEvent match {
        case None =>
          log.info("The entity type {} does not support deletion, ignoring delete request", entityType)
          sender ! stateResponse()

        case Some(event) =>
          persist(event)(handleEventAndRespond(respectDeleted = false))
      }

    case s: SaveSnapshotSuccess =>
      log.info("Successfully saved a new snapshot for entity {} and id {}", entityType, id)

    case f: SaveSnapshotFailure =>
      log.error(f.cause, "Failed to save a snapshot for entity {} and id {}, reason was {}", entityType)
  }

  def isAcceptingCommand(cmd: Any): Boolean =
    !state.deleted &&
      !(state == initialState && !isCreateMessage(cmd))

  def additionalCommandHandling: Receive

  def newDeleteEvent: Option[EntityEvent] = None

  def isCreateMessage(cmd: Any): Boolean

  def initialState: FO

  private def stateResponse(respectDeleted: Boolean = true) = {
    if (state == initialState) {
      EmptyResult
    } else if (respectDeleted && state.deleted) {
      EmptyResult
    } else {
      FullResult(state)
    }
  }

  def handleEvent(event: EntityEvent): Unit

  def handleEventAndRespond(respectDeleted: Boolean = true)(event: EntityEvent): Unit = {
    handleEvent(event)
    if (snapshotAfterCount.isDefined) {
      eventsSinceLastSnapshot += 1
      maybeSnapshot()
    }
    sender() ! stateResponse(respectDeleted)
  }

  def snapshotAfterCount: Option[Int] = Some(50)

  private def maybeSnapshot(): Unit = {
    snapshotAfterCount.
      filter(i => eventsSinceLastSnapshot >= i).
      foreach { i =>
        log.info("Taking snapshot because event count {} is > snapshot event limit of {}", eventsSinceLastSnapshot, i)
        saveSnapshot(state)
        eventsSinceLastSnapshot = 0
      }
  }

}

abstract class Aggregate[FO <: EntityFieldsObject[String, FO], E <: PersistentEntity[FO] : ClassTag] extends CommonActor {

  val idExtractor = PersistentEntity.PersistentEntityIdExtractor(context.system)
  val entityShardRegion =
    ClusterSharding(context.system).start(
      typeName = entityName,
      entityProps = entityProps,
      settings = ClusterShardingSettings(context.system),
      extractEntityId = idExtractor.extractEntityId,
      extractShardId = idExtractor.extractShardId
    )

  def entityProps: Props

  private def entityName = {
    val entityTag = implicitly[ClassTag[E]]
    entityTag.runtimeClass.getSimpleName
  }

  def forwardCommand(id: String, command: Any) =
    entityShardRegion.forward(command)
}


trait EntityFieldsObject[K, FO] extends Serializable {
  def assignId(id: K): FO

  def id: K

  def deleted: Boolean

  def markDeleted: FO
}
