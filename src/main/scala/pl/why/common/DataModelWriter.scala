package pl.why.common

import akka.persistence.journal.{EventAdapter, EventSeq, Tagged}
import com.trueaccord.scalapb.GeneratedMessage

trait DataModelWriter {

  def toDataModel: GeneratedMessage
}

trait DataModelReader {

  def fromDataModel: PartialFunction[GeneratedMessage, AnyRef]
}

class ProtobufDataModelAdapter extends EventAdapter {
  override def manifest(event: Any) = event.getClass.getName

  override def toJournal(event: Any) = event match {
    case ev: EntityEvent with DataModelWriter =>
      val message = ev.toDataModel

      //Add tags for the entity type and the event class name
      val eventType = ev.getClass.getName().toLowerCase().split("\\$").last
      Tagged(message, Set(ev.entityType, eventType))

    case _ => throw new RuntimeException(s"Protobuf adapter can't write adapt type: $event")
  }

  override def fromJournal(event: Any, manifest: String) = {
    event match {
      case m: GeneratedMessage =>
        //Reflect to get the companion for the domain class that was serialized and then
        //use that to perform the conversion back into the domain model
        val reader = Class.forName(manifest + "$").getField("MODULE$").get(null).asInstanceOf[DataModelReader]
        reader.
          fromDataModel.
          lift(m).
          map(EventSeq.single).
          getOrElse(throw readException(event))

      case _ => throw readException(event)
    }
  }

  private def readException(event: Any) = new RuntimeException(s"Protobuf adapter can't read adapt for type: $event")
}
