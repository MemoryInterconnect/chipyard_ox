package omnixtend

import chisel3._
import chisel3.util._

/**
 * Transceiver module interfaces with the TileLink messages and manages the
 * serialization and deserialization of data for transmission and reception.
 */
class Transceiver extends Module {
  val io = IO(new Bundle {
    val txAddr   = Input(UInt(64.W))   // Input address for transmission
    val txData   = Input(UInt(64.W))   // Input data for transmission
    val txOpcode = Input(UInt(3.W))    // Input opcode for transmission
    val txValid  = Input(Bool())       // Valid signal for transmission
    val txReady  = Output(Bool())      // Ready signal for transmission

    val rxData   = Output(UInt(64.W))  // Output data received
    val rxValid  = Output(Bool())      // Valid signal for received data
    val rxReady  = Input(Bool())       // Ready signal for receiver
  })

  // Queue for outgoing TLoE packets
  // 16 entries of TloePacket type
  val txQueue = Module(new Queue(new TloePacket, 16))
  
  // Queue for incoming TLoE packets
  // 16 entries of TloePacket type
  val rxQueue = Module(new Queue(new TloePacket, 16))

  // Default values for transmission queue
  txQueue.io.enq.bits  := 0.U.asTypeOf(new TloePacket)
  txQueue.io.enq.valid := false.B
  io.txReady           := false.B

  // Default values for reception queue
  rxQueue.io.enq.bits  := 0.U.asTypeOf(new TloePacket)
  rxQueue.io.enq.valid := false.B
  io.rxData            := 0.U
  io.rxValid           := false.B

  // Create a TLoE packet using input address, data, and opcode
  val tloePacket = TloePacketGenerator.createTloePacket(io.txAddr, io.txData, io.txOpcode)

  // Enqueue the TLoE packet into txQueue when txValid is high
  txQueue.io.enq.bits  := tloePacket
  txQueue.io.enq.valid := io.txValid
  io.txReady           := txQueue.io.enq.ready

  // Instantiate the Endpoint module
  val endpoint = Module(new Endpoint)

  // Connect txQueue to the Endpoint for transmission
  endpoint.io.txQueueData.bits  := txQueue.io.deq.bits
  endpoint.io.txQueueData.valid := txQueue.io.deq.valid
  txQueue.io.deq.ready          := endpoint.io.txQueueData.ready

  // Handle rxQueue and deserialize data
  endpoint.io.rxQueueData.ready := rxQueue.io.enq.ready

  // Enqueue received data into rxQueue when Endpoint valid signal is high
  when(endpoint.io.rxQueueData.valid) {
    rxQueue.io.enq.bits  := endpoint.io.rxQueueData.bits
    rxQueue.io.enq.valid := true.B
  }

  // Dequeue data from rxQueue and output when rxReady is high
  when(rxQueue.io.deq.valid && io.rxReady) {
    val rxPacket = rxQueue.io.deq.bits
    io.rxData            := rxPacket.tileLinkMsg.data
    io.rxValid           := true.B
    rxQueue.io.deq.ready := io.rxReady
  }.otherwise {
    io.rxValid           := false.B
    rxQueue.io.deq.ready := false.B
  }
}

