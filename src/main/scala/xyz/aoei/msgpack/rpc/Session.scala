package xyz.aoei.msgpack.rpc

import java.io.{InputStream, OutputStream}

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import com.fasterxml.jackson.core.{JsonGenerator, JsonParser}
import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.msgpack.jackson.dataformat.MessagePackFactory
import rx.lang.scala.subjects.ReplaySubject

case class ResponseHandler(writer: (Object) => Unit, requestId: Long) {
  def send(resp: Array[Any]): Unit = {
    writer(Response(requestId, null, resp))
  }
}

case class Window(data: Any)

class Session {
  private val objectMapper: ObjectMapper = {
    val factory = new MessagePackFactory()
    factory.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE)
    factory.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)

    val objectMapper = new ObjectMapper(factory)
    objectMapper.registerModule(DefaultScalaModule)
    objectMapper
  }

  private var nextRequestId: Long = 1
  private var pendingRequests = mutable.HashMap.empty[Long, Promise[Any]]

  private var in: InputStream = _
  private var out: OutputStream = _

  private case class RequestEvent(method: String, args: Array[Any], resp: ResponseHandler)
  private val requestEvent = ReplaySubject[RequestEvent]

  private case class NotificationEvent(method: String, args: Array[Any])
  private val notificationEvent = ReplaySubject[NotificationEvent]

  def attach(in: InputStream, out: OutputStream) = {
    this.in = in
    this.out = out

    reader.start()
  }

  def onRequest(callback: (String, Array[Any], ResponseHandler) => Unit) =
    requestEvent.subscribe( next => callback(next.method, next.args, next.resp) )

  def onNotification(callback: (String, Array[Any]) => Unit) =
    notificationEvent.subscribe( next => callback(next.method, next.args) )

  def request(method: String, args: Array[Any]): Future[Any] = {
    val id: Long = this.nextRequestId
    this.nextRequestId += 1

    val p = Promise[Any]
    this.pendingRequests += (id -> p)

    write(Request(id, method, args))

    p.future
  }

  def notify(method: String, args: Array[Any]): Unit = write(Notification(method, args))

  private val reader = new Thread(new Runnable {
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
  })

  private def write(obj: Object): Unit = {
    objectMapper.writeValue(out, obj)
    out.flush()
  }

  private def parseMessage(packet: Packet) = packet match {
    case Request(_, id, method, args) => this.requestEvent.onNext(RequestEvent(method, args, ResponseHandler(this.write, id)))
    case Response(_, id, err, result) =>
      val handler = this.pendingRequests(id)
      this.pendingRequests.remove(id)

      if (err != null) handler.failure(new IllegalArgumentException(err.toString))
      else handler.success(result)
    case Notification(_, method, args) => this.notificationEvent.onNext(NotificationEvent(method, args))
  }
}
