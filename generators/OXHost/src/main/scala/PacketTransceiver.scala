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
    val txOpcode = Input(UInt(3.W))  // Input opcode for transmission
    val txValid  = Input(Bool())      // Valid signal for transmission
    val txReady  = Output(Bool())     // Ready signal for transmission

    val rxData   = Output(UInt(64.W))  // Output data received
    val rxValid  = Output(Bool())     // Valid signal for received data
    val rxReady  = Input(Bool())      // Ready signal for receiver
  })

  val txQueue = Module(new Queue(UInt(131.W), 16)) // 64 (addr) + 64 (data) + 3 (opcode) = 131 bits
  val rxQueue = Module(new Queue(UInt(64.W), 16))  // Data width for received data

  // Default values
  txQueue.io.enq.bits  := 0.U
  txQueue.io.enq.valid := false.B
  io.txReady           := false.B
  rxQueue.io.enq.bits  := 0.U
  rxQueue.io.enq.valid := false.B
  io.rxData            := 0.U
  io.rxValid           := false.B

  // Enqueue data into txQueue when valid
  when(io.txValid) {
    txQueue.io.enq.bits  := Cat(io.txAddr, io.txData, io.txOpcode) // Serialize addr, data, opcode
    txQueue.io.enq.valid := true.B
  }
  io.txReady := txQueue.io.enq.ready

  val endpoint = Module(new Endpoint)

  // Connect txQueue to Endpoint
  endpoint.io.txQueueData.bits  := txQueue.io.deq.bits
  endpoint.io.txQueueData.valid := txQueue.io.deq.valid
  txQueue.io.deq.ready          := endpoint.io.txQueueData.ready

  // Handle rxQueue and deserialize data
  endpoint.io.rxQueueData.ready := rxQueue.io.enq.ready
  when(endpoint.io.rxQueueData.valid) {
    val rxDataFull = endpoint.io.rxQueueData.bits
    val rxData = rxDataFull(66, 3) // Deserialize: extract data only (bits [66:3])
    rxQueue.io.enq.bits  := rxData
    rxQueue.io.enq.valid := true.B
  }

  io.rxData            := rxQueue.io.deq.bits
  io.rxValid           := rxQueue.io.deq.valid
  rxQueue.io.deq.ready := io.rxReady
}

