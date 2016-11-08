# msgpack-rpc-scala
Scala Msgpack-RPC Implementation.
Adapted from [tarruda/node-msgpack5rpc](https://github.com/tarruda/node-msgpack5rpc).
See the [specification](https://github.com/msgpack-rpc/msgpack-rpc/blob/master/spec.md) for details.

## SBT
Add dependency:

    libraryDependencies += "xyz.aoei" %% "msgpack-rpc-scala" % "1.1"

## Usage
This package exports a single class which represents a msgpack-rpc session. A
Session instance can attached to any pair of write/read streams, and can send
and receive requests and notifications, so it can be used for both client and
servers. 

Example:

```scala
import xyz.aoei.msgpack.rpc._

val session = new Session(inputStream, outputStream)

session.onRequest((method, args, resp) => {
  println("received request")
  resp.send("response!")
  session.request("remote-method", "arg1", "arg2").onSuccess {
    case result => println("received response")
  }
})

session.onNotification((method, args) => {
  println("received notification")
})
```


Extended types can used by passing a list of `ExtendedType` objects to the `Session` constructor:

```scala
class ExType(val data: Array[Byte])

val typeId = 0
val encoder = (ex: ExType) => ex.data
val decoder = (bytes: Array[Byte]) => new ExType(bytes)
val exType = new ExtendedType(classOf[ExType], typeId, encoder, decoder)

val session = new Session(inputStream, outputStream, List(exType))
```

Requests by default return a `Future[Any]`. If you know what type the request should return
you may specify it to get a `Future` of that type:
```scala
session.request[ExType]("remote-method") // returns Future[ExType]
```
