package omnixtend

object OmniXtendPacketAXIStreamSender {
  def main(args: Array[String]): Unit = {
    // 예시 패킷 데이터
    val ethPreambleSFD = Array[Byte](0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0xD5.toByte)
    val destAddress = Array[Byte](0xFF.toByte, 0xFF.toByte, 0xFF.toByte, 0xFF.toByte, 0xFF.toByte, 0xFF.toByte)
    val srcAddress = Array[Byte](0x00, 0x11, 0x22, 0x33, 0x44, 0x55)
    val etherType = Array[Byte](0x08, 0x00)
    val tloeHeader = TLoEHeader(0x01, 0x2E50D, 0x56D4B, ack = true, 0x08, 0x02, 0x00, retransmit = false)
    val tileLinkMessages = Seq(
      TileLinkMessage(0x01, 0x04, 0x00, 0x05, 0xF33555, 0x7BA80000130EC440L, None, None)
    )
    val padding = Some(Array[Byte](0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
    val tloeFrameMask = Array[Byte](0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01)
    val ethernetFCS = Array[Byte](0xDE.toByte, 0xAD.toByte, 0xBE.toByte, 0xEF.toByte)

    // OmniXtend 패킷 생성
    val packet = OmniXtendPacket(ethPreambleSFD, destAddress, srcAddress, etherType, tloeHeader, tileLinkMessages, padding, tloeFrameMask, ethernetFCS)

    // AXI Stream 인터페이스 생성
//    val axiStreamInterface = AXIStreamInterface()

    // 패킷을 AXI Stream으로 전달 및 재전송 처리
//    val success = axiStreamInterface.send(packet.toAXIStreamData())

    // PacketReceiver 생성
    val receiver = new PacketReceiver()

    // PacketSender 모듈 생성 및 PacketReceiver와 연동
    val packetSender = PacketSender(receiver = receiver)

    // 패킷을 PacketSender 모듈로 전달 및 재전송 처리
    val success = packetSender.send(packet.toAXIStreamData())

    if (success) {
      println("Packet sent successfully.")
    } else {
      println("Failed to send packet after retries.")
    }

    // 패킷 정보 출력
    packet.printPacket()

    // 크레딧 추가 예시
//    axiStreamInterface.addCredits(5)
    packetSender.addCredits(5)
  }
}

