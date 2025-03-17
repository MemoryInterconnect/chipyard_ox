package omnixtend

import chisel3._
import chisel3.util._

/**
 * TLOEReceiver handles the reception of OmniXtend packets and acknowledgments.
 * It processes incoming packets and manages acknowledgments.
 */
class TLOEReceiver extends Module {
  val io = IO(new Bundle {
    // Interface from TLOETransmitter
    val ackQueue = Flipped(Decoupled(new AckBundle()))
    
    // Queue interface to TileLinkHandler
    val rxQueue = Decoupled(new RxBundle())
    
    // Ethernet interface
    val rxdata  = Input(UInt(64.W))
    val rxvalid = Input(Bool())
    val rxlast  = Input(Bool())

    // Sequence management interface
    val next_tx_seq = Input(UInt(22.W))
    val ackd_seq = Input(UInt(22.W))
    val next_rx_seq = Input(UInt(22.W))
    val next_tx_seq_out = Output(UInt(22.W))
    val ackd_seq_out = Output(UInt(22.W))
    val next_rx_seq_out = Output(UInt(22.W))
    val seq_update = Output(Bool())
  })

  // Acknowledgment bundle definition
  class AckBundle extends Bundle {
    val seq_num = UInt(22.W)
    val ack_type = UInt(2.W)
  }

  // Reception bundle definition
  class RxBundle extends Bundle {
    val data  = UInt(64.W)
    val valid = Bool()
  }

  // Packet reception registers
  val rxcount = RegInit(0.U(8.W))
  val rPacketVec = RegInit(VecInit(Seq.fill(14)(0.U(64.W))))
  val rxPacketReceived = RegInit(false.B)

  // Reception queue
  val rxQueue = Module(new Queue(new RxBundle, 16))
  io.rxQueue <> rxQueue.io.deq

  // Sequence update control
  io.seq_update := false.B
  io.next_tx_seq_out := io.next_tx_seq
  io.ackd_seq_out := io.ackd_seq
  io.next_rx_seq_out := io.next_rx_seq

  // Default values
  rxQueue.io.enq.valid := false.B
  rxQueue.io.enq.bits.data := 0.U
  rxQueue.io.enq.bits.valid := false.B
  io.ackQueue.ready := true.B  // Always ready to receive acknowledgments

  // Handle incoming packets
  when(!io.rxvalid) {
    rxcount := 0.U
  }

  when(io.rxvalid) {
    rxcount := rxcount + 1.U
    rPacketVec(rxcount) := io.rxdata

    when(io.rxlast) {
      rxPacketReceived := true.B
      rxcount := 0.U
    }
  }

  // Process received packets
  when(rxPacketReceived) {
    // Extract TileLink message from the packet and enqueue it
    rxQueue.io.enq.bits.data := rPacketVec(0)  // First word contains TileLink data
    rxQueue.io.enq.bits.valid := true.B
    rxQueue.io.enq.valid := true.B
    
    rxPacketReceived := false.B
  }

  // Handle acknowledgments from transmitter
  when(io.ackQueue.valid) {
    // Process acknowledgment information
    // This would typically involve updating sequence numbers and credit information
    io.ackQueue.ready := true.B
  }
} 