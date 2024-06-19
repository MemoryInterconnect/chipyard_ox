package omnixtend

import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global

class PacketReceiver {
  def receive(data: Array[Byte]): Future[Boolean] = {
    val promise = Promise[Boolean]()

    Future {
      // 데이터 수신 후 처리 로직 (예: 유효성 검사 등)
      println(s"Received raw data: ${data.map("%02X".format(_)).mkString(" ")}")
      // 데이터 해석 및 출력
      decodeOmniXtendPacket(data)
      // 임의의 지연을 추가하여 ACK/NACK 응답을 시뮬레이션합니다.
      Thread.sleep(500)
      // 50% 확률로 ACK/NACK 응답
      val isAck = scala.util.Random.nextBoolean()
      promise.success(isAck)
    }

    promise.future
  }

  def decodeOmniXtendPacket(data: Array[Byte]): Unit = {
    val ethPreambleSFD = data.slice(0, 8)
    val destAddress = data.slice(8, 14)
    val srcAddress = data.slice(14, 20)
    val etherType = data.slice(20, 22)
    val tloeHeader = data.slice(22, 30)
    val tileLinkMessages = data.slice(30, data.length - 12) // Assuming the rest is TileLink message
    val tloeFrameMask = data.slice(data.length - 12, data.length - 4)
    val ethernetFCS = data.slice(data.length - 4, data.length)

    println("------- Receive -------")
    println(s"Ethernet Preamble/SFD: ${ethPreambleSFD.map("%02X".format(_)).mkString(" ")}")
    println(s"Destination Address: ${destAddress.map("%02X".format(_)).mkString(" ")}")
    println(s"Source Address: ${srcAddress.map("%02X".format(_)).mkString(" ")}")
    println(s"EtherType: ${etherType.map("%02X".format(_)).mkString(" ")}")
    println(s"TLoE Header: ${tloeHeader.map("%02X".format(_)).mkString(" ")}")
    println(s"TileLink Messages: ${tileLinkMessages.map("%02X".format(_)).mkString(" ")}")
    println(s"TLoE Frame Mask: ${tloeFrameMask.map("%02X".format(_)).mkString(" ")}")
    println(s"Ethernet FCS: ${ethernetFCS.map("%02X".format(_)).mkString(" ")}")
    println("-----------------------")
  }
}

