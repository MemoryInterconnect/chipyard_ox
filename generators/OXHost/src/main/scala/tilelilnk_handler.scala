package omnixtend

import chisel3._
import chisel3.util._

/**
 * TileLinkHandler manages the interface between TileLink and OmniXtend.
 * It converts TileLink transactions to OmniXtend format and vice versa.
 */
class TileLinkHandler extends Module {
  val io = IO(new Bundle {
    // TileLink interface
    val txAddr   = Input(UInt(64.W))   // Input address for transmission
    val txData   = Input(UInt(512.W))  // Input data for transmission
    val txSize   = Input(UInt(3.W))    // Size of transmission
    val txOpcode = Input(UInt(3.W))    // Opcode for transmission
    val txValid  = Input(Bool())       // Valid signal for transmission
    val txReady  = Output(Bool())      // Ready signal for transmission

    // Reception interface
    val rxData   = Output(UInt(64.W))  // Output data received
    val rxValid  = Output(Bool())      // Valid signal for received data
    val rxReady  = Input(Bool())       // Ready signal for receiver

    // OmniXtend interface
    val tx = Decoupled(new TxBundle())  // Interface for transmission to endpoint
    val rx = Flipped(Decoupled(new RxBundle()))  // Interface for reception from endpoint
  })

  // Transaction bundle definition
  class TxBundle extends Bundle {
    val addr   = UInt(64.W)
    val opcode = UInt(3.W)
    val size   = UInt(3.W)
    val data   = UInt(512.W)
  }

  // Reception bundle definition
  class RxBundle extends Bundle {
    val data   = UInt(64.W)
    val valid  = Bool()
  }

  // Default values
  io.tx.valid := false.B
  io.tx.bits.addr := 0.U
  io.tx.bits.opcode := 0.U
  io.tx.bits.size := 0.U
  io.tx.bits.data := 0.U
  io.txReady := io.tx.ready

  // Handle incoming TileLink transactions
  when(io.txValid) {
    io.tx.valid := true.B
    io.tx.bits.addr := io.txAddr
    io.tx.bits.opcode := io.txOpcode
    io.tx.bits.size := io.txSize
    io.tx.bits.data := io.txData
  }

  // Handle reception data
  io.rxData := io.rx.bits.data
  io.rxValid := io.rx.valid
  io.rx.ready := io.rxReady
} 