package xyz.aoei.msgpack.rpc.jackson

import java.io.InputStream

import com.fasterxml.jackson.core.ObjectCodec
import com.fasterxml.jackson.core.io.IOContext
import org.msgpack.jackson.dataformat.{MessagePackExtensionType, MessagePackParser}
import xyz.aoei.msgpack.rpc.jackson.DecodersType.Decoders

object CustomParser {
  def apply(ctxt: IOContext, features: Int, objectCodec: ObjectCodec, in: InputStream, decs: Decoders) =
    new MessagePackParser(ctxt, features, objectCodec, in) with CustomParser {
      override var decoders = decs
    }
  def apply(ctxt: IOContext, features: Int, objectCodec: ObjectCodec, bytes: Array[Byte], decs: Decoders) =
    new MessagePackParser(ctxt, features, objectCodec, bytes) with CustomParser {
      override var decoders = decs
    }
}

trait CustomParser extends MessagePackParser {
  var decoders: Decoders

  override def getEmbeddedObject: AnyRef = {
    val ret = super.getEmbeddedObject
    ret match {
      case x: MessagePackExtensionType =>
        decoders.get(x.getType) match {
          case Some(decoder) => decoder(x.getData)
          case None => ret
        }
      case _ => ret
    }
  }
}
