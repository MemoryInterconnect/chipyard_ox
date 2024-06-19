package omnixtend

case class TileLinkMessage(
  chan: Byte,                     // 3 bits
  opcode: Byte,                   // 3 bits
  param: Byte,                    // 4 bits
  size: Byte,                     // 4 bits
  source: Int,                    // 26 bits
  address: Long,                  // 64 bits
  mask: Option[Array[Byte]],      // Optional Mask
  data: Option[Array[Byte]]       // Optional Data
) {
  def toBytes: Array[Byte] = {
    val header = new Array[Byte](13) 
    header(0) = ((chan & 0x07) << 5 | (opcode & 0x07) << 2 | (param >> 2 & 0x03)).toByte
    header(1) = ((param & 0x03) << 6 | (size & 0x0F) << 2 | (source >> 24 & 0x03)).toByte
    header(2) = (source >> 16).toByte
    header(3) = (source >> 8).toByte
    header(4) = source.toByte
    header(5) = (address >> 56).toByte
    header(6) = (address >> 48).toByte
    header(7) = (address >> 40).toByte
    header(8) = (address >> 32).toByte
    header(9) = (address >> 24).toByte
    header(10) = (address >> 16).toByte
    header(11) = (address >> 8).toByte
    header(12) = address.toByte
    header ++ mask.getOrElse(Array.empty[Byte]) ++ data.getOrElse(Array.empty[Byte])
  }

  def printMessage(): Unit = {
    println(s"Channel: $chan")
    println(s"Opcode: $opcode")
    println(s"Param: $param")
    println(s"Size: $size")
    println(s"Source: $source")
    println(s"Address: ${address.toHexString}")
    mask.foreach(m => println(s"Mask: ${m.map("%02X".format(_)).mkString(" ")}"))
    data.foreach(d => println(s"Data: ${d.map("%02X".format(_)).mkString(" ")}"))
  }
}

