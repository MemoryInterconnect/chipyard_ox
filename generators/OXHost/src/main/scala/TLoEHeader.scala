package omnixtend

case class TLoEHeader(
  vc: Byte,                        // 3 bits
  sequenceNumber: Int,             // 22 bits
  sequenceNumberAck: Int,          // 22 bits
  ack: Boolean,                    // 1 bit
  credit: Byte,                    // 5 bits
  channel: Byte,                   // 3 bits
  reserved: Byte,                  // 7 bits
  retransmit: Boolean              // 1 bit
) {
  def toBytes: Array[Byte] = {
    val header = new Array[Byte](8)   // 8 바이트 배열 초기화
    header(0) = ((vc & 0x07) << 5 | (sequenceNumber >> 17 & 0x1F)).toByte
    header(1) = (sequenceNumber >> 9).toByte
    header(2) = (sequenceNumber >> 1).toByte
    header(3) = ((sequenceNumber & 0x01) << 7 | (sequenceNumberAck >> 15 & 0x7F)).toByte
    header(4) = (sequenceNumberAck >> 7).toByte
    header(5) = ((sequenceNumberAck & 0x7F) << 1 | (if (ack) 1 else 0) << 6 | (credit & 0x1F)).toByte
    header(6) = ((channel & 0x07) << 5 | (reserved >> 2 & 0x1F)).toByte
    header(7) = ((reserved & 0x03) << 6 | (if (retransmit) 1 else 0) << 5).toByte
    header
  }

  def printHeader(): Unit = {
    println(s"Virtual Channel: $vc")
    println(s"Sequence Number: $sequenceNumber")
    println(s"Sequence Number Ack: $sequenceNumberAck")
    println(s"Ack: $ack")
    println(s"Credit: $credit")
    println(s"Channel: $channel")
    println(s"Reserved: $reserved")
    println(s"Retransmit: $retransmit")
  }
}

