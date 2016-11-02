package xyz.aoei.msgpack.rpc

import java.io.{InputStream, OutputStream}

import com.fasterxml.jackson.core.{JsonGenerator, JsonParser}
import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import rx.lang.scala.subjects.ReplaySubject
import xyz.aoei.msgpack.rpc.jackson.{CustomFactory, CustomSerializer}

import scala.collection.mutable
import scala.concurrent.{Future, Promise}

// An instance of ResponseHandler is given to the user when a request is received
// to allow the user to provide a response
case class ResponseHandler(writer: (Object) => Unit, requestId: Long) {
  def send(resp: List[Any]): Unit = {
    writer(Response(requestId, null, resp))
  }
}

// Provided by the user on session start to register any msgpack extended types
case class ExtendedType[T <: AnyRef](typeClass: Class[T], typeId: Byte,
                                     serializer: T => Array[Byte],
                                     deserializer: Array[Byte] => T)

class Session(in: InputStream, out: OutputStream, types: List[ExtendedType[_ <: AnyRef]] = List()) {
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

  private var nextRequestId: Long = 1
  private var pendingRequests = mutable.HashMap.empty[Long, Promise[Any]]

  private case class RequestEvent(method: String, args: List[Any], resp: ResponseHandler)
  private val requestEvent = ReplaySubject[RequestEvent]

  private case class NotificationEvent(method: String, args: List[Any])
  private val notificationEvent = ReplaySubject[NotificationEvent]

  // Create a thread to listen for any packets
  new Thread(new Runnable {
    override def run(): Unit = {
      while (true) {
        val tree = objectMapper.readTree(in)

        val packet: Packet = tree.get(0).asInt match {
          case 0 => objectMapper.treeToValue(tree, classOf[Request])
          case 1 => objectMapper.treeToValue(tree, classOf[Response])
          case 2 => objectMapper.treeToValue(tree, classOf[Notification])
          case x => throw new IllegalArgumentException("Invalid Packet Type: " + x)
        }

        parseMessage(packet)
      }
    }
  }).start()

  def onRequest(callback: (String, List[Any], ResponseHandler) => Unit) =
    requestEvent.subscribe( next => callback(next.method, next.args, next.resp) )

  def onNotification(callback: (String, List[Any]) => Unit) =
    notificationEvent.subscribe( next => callback(next.method, next.args) )

  def request(method: String, args: List[Any] = List()): Future[Any] = {
    val id: Long = this.nextRequestId
    this.nextRequestId += 1

    val p = Promise[Any]
    this.pendingRequests += (id -> p)

    write(Request(id, method, args))

    p.future
  }

  def notify(method: String, args: List[Any]): Unit = write(Notification(method, args))

  private def write(obj: Object): Unit = {
    objectMapper.writeValue(out, obj)
    out.flush()
  }

  private def parseMessage(packet: Packet) = packet match {
    case Request(_, id, method, args) =>
      this.requestEvent.onNext(RequestEvent(method, args, ResponseHandler(this.write, id)))

    case Response(_, id, err, result) =>
      val handler = this.pendingRequests(id)
      this.pendingRequests.remove(id)

      if (err != null) handler.failure(new IllegalArgumentException(err.toString))
      else handler.success(result)

    case Notification(_, method, args) =>
      this.notificationEvent.onNext(NotificationEvent(method, args))
  }
}
