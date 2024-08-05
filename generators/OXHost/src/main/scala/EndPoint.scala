package omnixtend

import chisel3._
import chisel3.util._

/**
 * Endpoint module handles memory read and write operations based on the received
 * TileLink messages. It serializes and deserializes data to/from the queues.
 * This version uses a 1MB memory and applies an address offset of 0x100000000.
 */
class Endpoint extends Module {
  val io = IO(new Bundle {
    val txQueueData = Flipped(Decoupled(UInt(131.W))) // 64 (addr) + 64 (data) + 3 (opcode) = 131 bits
    val rxQueueData = Decoupled(UInt(131.W)) // Output data for response, same width
  })

  // Default values
  io.txQueueData.ready := false.B
  io.rxQueueData.valid := false.B
  io.rxQueueData.bits := 0.U

  // 1MB memory for read and write operations
  val mem = SyncReadMem(131072, UInt(64.W)) // 131072 x 64-bit memory (1MB)

  // Offset value
  val addressOffset = 0x100000000L.U(64.W)

  // Handle txQueue data and place the result into rxQueue
  when(io.txQueueData.valid) {
    // Extract addr (64 bits), data (64 bits), and opcode (3 bits) from input
    val addr = io.txQueueData.bits(130, 67) - addressOffset
    val data = io.txQueueData.bits(66, 3)
    val opcode = io.txQueueData.bits(2, 0)

    // Perform memory operations based on opcode
    when(opcode === 4.U) { // GET
      // For Get operation, read from memory and serialize addr, data, opcode
      io.rxQueueData.bits := Cat(addr + addressOffset, mem.read(addr), opcode)
    } .elsewhen(opcode === 0.U) { // PutFullData
      // For PutFullData operation, write to memory and serialize addr, data, opcode
      mem.write(addr, data)
      io.rxQueueData.bits := Cat(addr + addressOffset, data, opcode)
    }
    io.rxQueueData.valid := true.B
    io.txQueueData.ready := true.B
  }
}

