package xyz.aoei.msgpack.rpc

import org.scalatest._
import java.io.{InputStream, OutputStream}

import scala.sys.process._
import scala.concurrent.{Await, SyncVar, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class NeovimTest extends FlatSpec with BeforeAndAfter {
  val inputStream = new SyncVar[InputStream]
  val outputStream = new SyncVar[OutputStream]

  try {
    val pb = Process(Seq("nvim", "-u", "NONE", "-N", "--embed"))
    val pio = new ProcessIO(
      stdout => outputStream.put(stdout),
      stdin => inputStream.put(stdin),
      _ => ())
    pb.run(pio)
  } catch {
    case e: Exception => {
      println("A Neovim installation is required to run the tests")
      println("(see https://github.com/neovim/neovim/wiki/Installing)")
      System.exit(1)
    }
  }

  val session = new Session(inputStream.get, outputStream.get)

  var requests: Array[Any] = Array()
  var notifications: Array[Any] = Array()

  session.onRequest((method, args, resp) => {
    requests = requests :+ Array(method, args)
    resp.send(List("received " + method + "(" + args.toString + ")"))
  })

  session.onNotification((method, args) => {
    notifications = notifications :+ Array(method, args)
  })

  before {
    requests = Array()
    notifications = Array()
  }

  it should "send requests and receive response" in {
    val f: Future[Any] = session.request("vim_eval", """{"k1": "v1", "k2": "v2"}""" :: Nil)
    Await.ready(f, 1 seconds).onComplete {
      case Success(res) => assert(res == Map("k1" -> "v1", "k2" -> "v2"))
      case Failure(err) => fail("Received error: " + err)
    }
  }

  it should "receive requests and send responses" in {
    val f: Future[Any] = session.request("vim_eval", """rpcrequest(1, "request", 1, 2, 3)""" :: Nil)
    Await.ready(f, 1 seconds).onComplete {
      case Success(res) =>
      case Failure(err) => fail("Received error: " + err)
    }
    assertResult(Array(Array("request", Array(1, 2, 3)))) { requests }
    assertResult(Array()) { notifications }
  }
}
