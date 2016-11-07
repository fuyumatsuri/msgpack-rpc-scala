package xyz.aoei.msgpack.rpc

import org.scalatest._
import java.io.{InputStream, OutputStream}

import org.scalatest.concurrent.ScalaFutures

import scala.sys.process._
import scala.concurrent.{Await, Future, SyncVar}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class NeovimTest extends FlatSpec with BeforeAndAfter with ScalaFutures {
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

  class Window(val data: Array[Byte])
  val windowType = ExtendedType(classOf[Window], 1, (win: Window) => win.data, (bytes) => new Window(bytes))

  val session = new Session(inputStream.get, outputStream.get, List(windowType))

  var requests: Array[Any] = Array()
  var notifications: Array[Any] = Array()

  session.onRequest((method, args, resp) => {
    requests = requests :+ Array(method, args)
    resp.send("received " + method + "(" + args.toString + ")")
  })

  session.onNotification((method, args) => {
    notifications = notifications :+ Array(method, args)
  })

  before {
    requests = Array()
    notifications = Array()
  }

  it should "send requests and receive response" in {
    val f: Future[Any] = session.request("vim_eval", """{"k1": "v1", "k2": "v2"}""")
    ScalaFutures.whenReady(f) { res =>
      assert(res == Map("k1" -> "v1", "k2" -> "v2"))
    }
  }

  it should "receive requests and send responses" in {
    val f: Future[Any] = session.request("vim_eval", """rpcrequest(1, "request", 1, 2, 3)""")
    ScalaFutures.whenReady(f) { res =>
      assertResult(Array(Array("request", Array(1, 2, 3)))) { requests }
      assertResult(Array()) { notifications }
    }
  }

  it should "receive notifications" in {
    val f: Future[Any] = session.request("vim_eval", """rpcnotify(1,"notify", 1, 2, 3)""")
    ScalaFutures.whenReady(f) { res =>
      assertResult(Array(Array("notify", Array(1, 2, 3)))) { notifications }
    }
  }

  it should "deal with custom types" in {
    val res = Await.result(for {
      _ <- session.request("vim_command", "vsp")
      windows <- session.request("vim_get_windows")
      _ <- {
        val w = windows.asInstanceOf[List[Window]]
        assert(w.length == 2)
        assert(w.head.isInstanceOf[Window])
        assert(w(1).isInstanceOf[Window])
        session.request("vim_set_current_window", w(1))
      }
      window <- session.request("vim_get_current_window")
    } yield (window, windows), 1 second)

    res match { case (win: Window, windows: List[Window]) => assert(win.data.deep == windows(1).data.deep) }
  }

  it should "handle variable argument parameters" in {
    val f: Future[Any] = session.request("vim_call_function", "abs", List(-1))
    ScalaFutures.whenReady(f) { res =>
      assert(res === 1)
    }
  }
}
