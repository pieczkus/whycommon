package pl.why.common.resumable

import akka.actor.Props
import pl.why.common.Aggregate
import pl.why.common.PersistentEntity.GetState
import pl.why.common.resumable.ResumableProjectionEntity.Command.CreateResumableProjection
import pl.why.common.resumable.ResumableProjectionManager.{FindLatestOffset, StoreLatestOffset}

object ResumableProjectionManager {
  val Name = "resumable-projection-manager"

  case class FindLatestOffset(projectionId: String)

  case class StoreLatestOffset(projectionId: String, offset: String)

  def props: Props = Props[ResumableProjectionManager]

}

class ResumableProjectionManager extends Aggregate[ResumableOffset, ResumableProjectionEntity] {
  override def entityProps: Props = ResumableProjectionEntity.props

  override def receive: Receive = {
    case StoreLatestOffset(projectionId, offset) =>
      val ro = ResumableOffset(projectionId, offset)
      entityShardRegion.tell(CreateResumableProjection(ro), sender())

    case FindLatestOffset(projectionId) =>
      forwardCommand(projectionId, GetState(projectionId))
  }
}
