package xyz.aoei.msgpack.rpc

import com.fasterxml.jackson.annotation.{JsonFormat}

object PacketType {
  val Request = 0
  val Response = 1
  val Notification = 2
}

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
abstract class Packet

case class Request(packetType: Int, requestId: Long, method: String, args: Array[Any]) extends Packet
object Request {
  def apply(requestId: Long, method: String, args: Array[Any]) = new Request(PacketType.Request, requestId, method, args)
}

case class Response(packetType: Int, requestId: Long, error: Any, result: Any) extends Packet
object Response {
  def apply(requestId: Long, error: Any, result: Any) = new Response(PacketType.Response, requestId, error, result)
}

case class Notification(packetType: Int, method: String, args: Array[Any]) extends Packet
object Notification{
  def apply(method: String, args: Array[Any]) = new Notification(PacketType.Notification, method, args)
}