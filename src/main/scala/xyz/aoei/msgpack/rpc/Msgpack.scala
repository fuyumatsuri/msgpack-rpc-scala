package xyz.aoei.msgpack.rpc

import java.io.{InputStream, OutputStream}

import com.fasterxml.jackson.core.{JsonGenerator, JsonParser}
import com.fasterxml.jackson.databind.{JsonSerializer, ObjectMapper}
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import xyz.aoei.msgpack.rpc.jackson.{CustomFactory, CustomSerializer}

class Msgpack(types: List[ExtendedType[_ <: AnyRef]]) {
  private val objectMapper: ObjectMapper = {
    val factory = new CustomFactory
    factory.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE)
    factory.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)

    // Register any extension types
    val mod = new SimpleModule
    types.map { t =>
      // Allow the MessagePackFactory to parse the type
      factory.register(t.typeId, t.deserializer)
      // Allow Jackson to serialize the type
      mod.addSerializer(t.typeClass, new CustomSerializer(t.typeId, t.serializer).asInstanceOf[JsonSerializer[Object]])
    }

    val objectMapper = new ObjectMapper(factory)
    objectMapper.registerModule(DefaultScalaModule)
    objectMapper.registerModule(mod)
    objectMapper
  }

  def readPacket(in: InputStream): Packet = {
    val tree = objectMapper.readTree(in)

    val packet: Packet = tree.get(0).asInt match {
      case 0 => objectMapper.treeToValue(tree, classOf[Request])
      case 1 => objectMapper.treeToValue(tree, classOf[Response])
      case 2 => objectMapper.treeToValue(tree, classOf[Notification])
      case x => throw new IllegalArgumentException("Invalid Packet Type: " + x)
    }

    packet
  }

  def write(obj: Object, out: OutputStream): Unit = {
    objectMapper.writeValue(out, obj)
    out.flush()
  }
}
