package pl.why.common.resumable

import akka.actor.Props
import pl.why.common._
import pl.why.common.proto.Common
import pl.why.common.resumable.ResumableProjectionEntity.Command.{CreateResumableProjection, UpdateOffset}
import pl.why.common.resumable.ResumableProjectionEntity.Event.{OffsetUpdated, ResumableProjectionCreated}
import com.trueaccord.scalapb.GeneratedMessage


case class ResumableOffset(projectionId: String, uuid: String, deleted: Boolean = false) extends EntityFieldsObject[String, ResumableOffset] {
  override def assignId(id: String): ResumableOffset = copy(projectionId = id)

  override def id: String = projectionId

  override def markDeleted: ResumableOffset = copy(deleted = true)
}

object ResumableOffset {
  val empty = ResumableOffset("", "")
}

object ResumableProjectionEntity {
  val EntityType = "resumable-projection"

  def props: Props = Props[ResumableProjectionEntity]

  object Command {

    case class CreateResumableProjection(ro: ResumableOffset) extends EntityCommand {
      def entityId: String = ro.projectionId
    }

    case class UpdateOffset(ro: ResumableOffset) extends EntityCommand {
      override def entityId: String = ro.projectionId
    }

  }

  object Event {

    trait ResumableProjectionEvent extends EntityEvent {
      def entityType = EntityType
    }

    case class ResumableProjectionCreated(ro: ResumableOffset) extends ResumableProjectionEvent {
      override def toDataModel: Common.ResumableProjectionCreated = {
        Common.ResumableProjectionCreated(
          Some(Common.ResumableOffset(ro.projectionId, ro.uuid, ro.deleted)))
      }
    }

    object ResumableProjectionCreated extends DataModelReader {
      def fromDataModel: PartialFunction[GeneratedMessage, ResumableProjectionCreated] = {
        case dc: Common.ResumableProjectionCreated =>
          val d = dc.offset.get
          ResumableProjectionCreated(ResumableOffset(d.projectionId, d.uuid, d.deleted))
      }
    }

    case class OffsetUpdated(ro: ResumableOffset) extends ResumableProjectionEvent {
      override def toDataModel: Common.OffsetUpdated = {
        Common.OffsetUpdated(
          Some(Common.ResumableOffset(ro.projectionId, ro.uuid, ro.deleted)))
      }
    }

    object ResumableProjectionUpdated extends DataModelReader {
      def fromDataModel: PartialFunction[GeneratedMessage, OffsetUpdated] = {
        case dc: Common.OffsetUpdated =>
          val d = dc.offset.get
          OffsetUpdated(ResumableOffset(d.projectionId, d.uuid, d.deleted))
      }
    }

  }

}

class ResumableProjectionEntity extends PersistentEntity[ResumableOffset] {
  override def additionalCommandHandling: Receive = {
    case CreateResumableProjection(ro) =>
      persist(ResumableProjectionCreated(ro)) {
        handleEventAndRespond()
      }

    case UpdateOffset(ro) =>
      persist(OffsetUpdated(ro)) {
        handleEventAndRespond()
      }
  }

  override def isCreateMessage(cmd: Any): Boolean = cmd match {
    case CreateResumableProjection(_) => true
    case _ => false
  }

  override def initialState: ResumableOffset = ResumableOffset.empty

  override def handleEvent(event: EntityEvent): Unit = event match {
    case ResumableProjectionCreated(ro) =>
      state = ro

    case OffsetUpdated(ro) =>
      state = ro
  }
}
