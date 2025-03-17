package omnixtend

import chisel3._
import chisel3.util._

/**
 * TLOETransmitter handles data packet transmission for OmniXtend protocol.
 * It processes TileLink transactions and converts them into OmniXtend packets.
 */
class TLOETransmitter extends Module {
  val io = IO(new Bundle {
    // Queue interface for incoming transactions
    val txQueue = Flipped(Decoupled(new TxBundle()))

    // Ethernet interface
    val txdata   = Output(UInt(64.W))
    val txvalid  = Output(Bool())
    val txlast   = Output(Bool())
    val txkeep   = Output(UInt(8.W))
    val txready  = Input(Bool())

    // Control signals
    val ox_open  = Input(Bool())
    val ox_close = Input(Bool())
    val debug1   = Input(Bool())
    val debug2   = Input(Bool())

    // Sequence management
    val next_tx_seq = Input(UInt(22.W))
    val ackd_seq = Input(UInt(22.W))
    val next_rx_seq = Input(UInt(22.W))
    val next_tx_seq_out = Output(UInt(22.W))
    val ackd_seq_out = Output(UInt(22.W))
    val next_rx_seq_out = Output(UInt(22.W))
    val seq_update = Output(Bool())

    // Acknowledgment interface
    val ackQueue = Decoupled(new AckBundle())
  })

  // Transaction bundle definition
  class TxBundle extends Bundle {
    val addr   = UInt(64.W)
    val opcode = UInt(3.W)
    val size   = UInt(3.W)
    val data   = UInt(512.W)
  }

  // Acknowledgment bundle definition
  class AckBundle extends Bundle {
    val seq_num = UInt(22.W)
    val ack_type = UInt(2.W)
  }

  // States for packet transmission
  val sIDLE :: sGEN_PACKET :: sSEND_PACKET :: sWAIT_ACK :: Nil = Enum(4)
  val state = RegInit(sIDLE)

  // Packet generation and transmission registers
  val dataPacket = RegInit(0.U(576.W))
  val txPacketVec = RegInit(VecInit(Seq.fill(14)(0.U(64.W))))
  val txPacketVecSize = RegInit(0.U(4.W))
  val sendPacket = RegInit(false.B)
  val txComplete = RegInit(false.B)
  val txIndex = RegInit(0.U(4.W))

  // Sequence management
  val currentTxSeq = RegInit(0.U(22.W))
  io.next_tx_seq_out := io.next_tx_seq
  io.ackd_seq_out := io.ackd_seq
  io.next_rx_seq_out := io.next_rx_seq
  io.seq_update := false.B

  // Default output values
  io.txdata := 0.U
  io.txvalid := false.B
  io.txlast := false.B
  io.txkeep := 0.U
  io.ackQueue.valid := false.B
  io.ackQueue.bits.seq_num := 0.U
  io.ackQueue.bits.ack_type := 0.U
  io.txQueue.ready := false.B

/*
  // State machine for packet transmission
  switch(state) {
    is(sIDLE) {
      when(io.ox_open) {
        io.txQueue.ready := true.B
        when(io.txQueue.valid) {
          state := sGEN_PACKET
          currentTxSeq := io.next_tx_seq
        }
      }.otherwise {
        io.txQueue.ready := false.B
      }
    }

    is(sGEN_PACKET) {
      io.txQueue.ready := false.B
      // Generate data packet from txQueue
      dataPacket := OXPacket.readPacket(
        io.txQueue.bits.addr,    // Address from txQueue
        io.next_tx_seq,         // Sequence number from endpoint
        io.next_rx_seq,         // Receive sequence from endpoint
        io.txQueue.bits.size    // Size from txQueue
      )

      // Split packet into 64-bit chunks
      txPacketVec := VecInit(Seq.tabulate(9) { i =>
        val packetWidth = 576
        val high = packetWidth - (64 * i) - 1
        val low = math.max(packetWidth - 64 * (i + 1), 0)
        dataPacket(high, low)
      } ++ Seq.fill(5)(0.U(64.W)))

      txPacketVecSize := 9.U
      sendPacket := true.B
      txComplete := false.B
      txIndex := 0.U  // Reset index before sending
      state := sSEND_PACKET
    }

    is(sSEND_PACKET) {
      io.txQueue.ready := false.B
      when(txComplete) {
        // Add to ackQueue for tracking
        io.ackQueue.valid := true.B
        io.ackQueue.bits.seq_num := currentTxSeq
        io.ackQueue.bits.ack_type := 0.U // Normal data packet

        when(io.ackQueue.ready) {
          state := sWAIT_ACK
          io.seq_update := true.B
          io.next_tx_seq_out := currentTxSeq + 1.U
        }
      }
    }

    is(sWAIT_ACK) {
      io.txQueue.ready := false.B
      when(io.ackd_seq >= currentTxSeq) {
        state := sIDLE
      }
    }
  }

  // Packet transmission logic
  when(sendPacket) {
    when(io.txready && txIndex < txPacketVecSize) {
      io.txdata := TloePacGen.toBigEndian(txPacketVec(txIndex))
      io.txvalid := true.B

      // Handle last packet differently
      when(txIndex === (txPacketVecSize - 1.U)) {
        io.txlast := true.B
        io.txkeep := "h3F".U  // Last packet uses 0x3F
      }.otherwise {
        io.txlast := false.B
        io.txkeep := "hFF".U  // All bytes valid for normal packets
      }

      when(io.txready) {
        txIndex := txIndex + 1.U
      }
    }.elsewhen(txIndex >= txPacketVecSize) {
      // Reset all signals
      io.txdata := 0.U
      io.txvalid := false.B
      io.txlast := false.B
      io.txkeep := 0.U
      
      sendPacket := false.B
      txComplete := true.B
      txIndex := 0.U
    }
  }

  // Debug signals
  when(io.debug1) {
    // Add debug behavior if needed
  }

  when(io.debug2) {
    // Add debug behavior if needed
  }
    */
} 