package omnixtend

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

case class PacketSender(var availableCredits: Int = 10, receiver: PacketReceiver) {  // 초기 크레딧 수

  def send(data: Array[Byte], timeout: Duration = 1.second, maxRetries: Int = 3): Boolean = {
    var retries = 0
    var ackReceived = false

    while (retries < maxRetries && !ackReceived) {
      if (availableCredits > 0) {
        println(s"Sending data (attempt ${retries + 1}): ${data.map("%02X".format(_)).mkString(" ")}")
        availableCredits -= 1

        try {
          val futureAck = receiver.receive(data)
          ackReceived = Await.result(futureAck, timeout)
        } catch {
          case _: Throwable => ackReceived = false
        }

        if (!ackReceived) {
          println(s"No ACK received, retrying... (${retries + 1})")
          retries += 1
        } else {
          availableCredits += 1  // ACK 수신 시 크레딧 반환
        }
      } else {
        println("No available credits, waiting...")
        Thread.sleep(1000)  // 크레딧이 반환될 때까지 대기
      }
    }

    ackReceived
  }

  def addCredits(credits: Int): Unit = {
    availableCredits += credits
    println(s"Added $credits credits, total available credits: $availableCredits")
  }
}

