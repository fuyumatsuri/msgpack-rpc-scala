package xyz.aoei.msgpack.rpc.jackson

import java.io.InputStream

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.io.IOContext
import org.msgpack.jackson.dataformat.{MessagePackFactory, MessagePackParser}
import xyz.aoei.msgpack.rpc.jackson.DecodersType.Decoders

object DecodersType { type Decoders = Map[Byte, Array[Byte] => AnyRef] }

class CustomFactory extends MessagePackFactory {
  var decoders: Decoders = Map()

  def register(typeId: Byte, decoder: (Array[Byte] => AnyRef)): Unit = {
    decoders = decoders + (typeId -> decoder)
  }

  override def _createParser(in: InputStream, ctxt: IOContext): MessagePackParser = {
    CustomParser(ctxt, _parserFeatures, _objectCodec, in, decoders)
  }

  override def _createParser(data: Array[Byte], offset: Int, len: Int, ctxt: IOContext): JsonParser = {
    val newData = if (offset != 0 || len != data.length) data.slice(offset, offset+len) else data
    CustomParser(ctxt, _parserFeatures, _objectCodec, newData, decoders)
  }
}
