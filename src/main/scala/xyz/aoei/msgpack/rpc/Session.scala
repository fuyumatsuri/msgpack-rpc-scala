package xyz.aoei.msgpack.rpc

import java.io.{InputStream, OutputStream}

import com.fasterxml.jackson.databind.JsonMappingException
import rx.lang.scala.subjects.ReplaySubject

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.reflect.ClassTag

// An instance of ResponseHandler is given to the user when a request is received
// to allow the user to provide a response
case class ResponseHandler(writer: (Object) => Unit, requestId: Long) {
  def send(resp: Any*): Unit = send(resp.toList)
  def send(resp: List[Any]): Unit = {
    writer(Response(requestId, null, resp))
  }
}

// Provided by the user on session start to register any msgpack extended types
case class ExtendedType[T <: AnyRef](typeClass: Class[T], typeId: Byte,
                                     serializer: T => Array[Byte],
                                     deserializer: Array[Byte] => T)

class Session(in: InputStream, out: OutputStream, types: List[ExtendedType[_ <: AnyRef]] = List()) {
  private val msgpack = new Msgpack(types)

  private var nextRequestId: Long = 1
  private var pendingRequests = mutable.HashMap.empty[Long, (ClassTag[_], Promise[Any])]

  private case class RequestEvent(method: String, args: List[Any], resp: ResponseHandler)
  private val requestEvent = ReplaySubject[RequestEvent]

  private case class NotificationEvent(method: String, args: List[Any])
  private val notificationEvent = ReplaySubject[NotificationEvent]

  // Create a thread to listen for any packets
  new Thread(new Runnable {
    override def run(): Unit = {
      try {
        while (true) {
          val packet = msgpack.readPacket(in)
          parseMessage(packet)
        }
      } catch {
        case e: JsonMappingException => // end-of-input
      }
    }
  }).start()

  def onRequest(callback: (String, List[Any], ResponseHandler) => Unit) =
    requestEvent.subscribe( next => callback(next.method, next.args, next.resp) )

  def onNotification(callback: (String, List[Any]) => Unit) =
    notificationEvent.subscribe( next => callback(next.method, next.args) )

  trait DefaultsTo[Type, Default]
  object DefaultsTo {
    implicit def defaultDefaultsTo[T]: DefaultsTo[T, T] = null
    implicit def fallback[T, D]: DefaultsTo[T, D] = null
  }

  def request[T <: Any : ClassTag](method: String, args: Any*)(implicit default: T DefaultsTo Any): Future[T] = request[T](method, args.toList)
  def request[T <: Any : ClassTag](method: String, args: List[Any] = List())(implicit default: T DefaultsTo Any): Future[T] = {
    val ct = implicitly[ClassTag[T]]

    val p = Promise[T]

    synchronized {
      val id: Long = this.nextRequestId
      this.nextRequestId += 1

      this.pendingRequests += (id -> (ct, p.asInstanceOf[Promise[Any]]))
      write(Request(id, method, args))
    }

    p.future
  }

  def notify(method: String, args: Any*): Unit = notify(method, args.toList)
  def notify(method: String, args: List[Any]): Unit = write(Notification(method, args))

  private def write(obj: Object): Unit = {
    msgpack.write(obj, out)
  }

  private def parseMessage(packet: Packet) = packet match {
    case Request(_, id, method, args) =>
      this.requestEvent.onNext(RequestEvent(method, args, ResponseHandler(this.write, id)))

    case Response(_, id, err, result) =>
      synchronized {
        this.pendingRequests(id) match {
          case (tag, handler) =>
            if (err != null) handler.failure(new IllegalArgumentException(err.toString))
            else result match {
              case null => handler.success(null)
              case tag(x) => handler.success(x)
              case _ => handler.failure(new IllegalArgumentException("result type " + result.getClass + " is not expected type " + tag.runtimeClass))
            }
        }
        this.pendingRequests.remove(id)
      }

    case Notification(_, method, args) =>
      this.notificationEvent.onNext(NotificationEvent(method, args))
  }
}
