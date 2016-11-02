package xyz.aoei.msgpack.rpc.jackson

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.{JsonSerializer, SerializerProvider}
import org.msgpack.jackson.dataformat.{MessagePackExtensionType, MessagePackGenerator}

class CustomSerializer[T](val typeId: Byte, val serializer: T => Array[Byte]) extends JsonSerializer[T] {
  override def serialize(value: T, gen: JsonGenerator, serializers: SerializerProvider): Unit = {
    val generator = gen.asInstanceOf[MessagePackGenerator]
    generator.writeExtensionType(new MessagePackExtensionType(typeId, serializer(value)))
  }
}
