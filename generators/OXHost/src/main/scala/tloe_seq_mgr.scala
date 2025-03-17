package omnixtend

import chisel3._
import chisel3.util._

/**
 * TLOESeqManager handles sequence number management for OmniXtend protocol.
 * It provides functionality for sequence number comparison, increment, and updates.
 */
class TLOESeqManager extends Module {
  val io = IO(new Bundle {
    // Sequence number inputs
    val seq_a = Input(UInt(22.W))
    val seq_b = Input(UInt(22.W))
    val seq_num = Input(UInt(22.W))
    val seq_num_ack = Input(UInt(22.W))

    // Current endpoint sequence states
    val next_tx_seq = Input(UInt(22.W))
    val next_rx_seq = Input(UInt(22.W))
    val acked_seq = Input(UInt(22.W))

    // Sequence number outputs
    val seq_cmp_result = Output(SInt(2.W))  // -1, 0, 1
    val seq_prev = Output(UInt(22.W))
    val seq_next = Output(UInt(22.W))
    val next_tx_seq_inc = Output(UInt(22.W))

    // Frame sequence updates
    val frame_seq_num = Output(UInt(22.W))
    val frame_seq_num_ack = Output(UInt(22.W))

    // Endpoint sequence updates
    val next_rx_seq_out = Output(UInt(22.W))
    val acked_seq_out = Output(UInt(22.W))
    val update_rx_seq = Output(Bool())
    val update_acked_seq = Output(Bool())
  })

  // Constants
  val MAX_SEQ_NUM = (1 << 22) - 1  // 22비트 최대값
  val HALF_MAX_SEQ_NUM = (1 << 21)  // 최대값의 절반

  // Sequence number comparison
  val diff = io.seq_a.asSInt - io.seq_b.asSInt
  io.seq_cmp_result := Mux(diff === 0.S,
    0.S,
    Mux(diff > 0.S,
      Mux(diff < HALF_MAX_SEQ_NUM.S, 1.S, -1.S),
      Mux(-diff < HALF_MAX_SEQ_NUM.S, -1.S, 1.S)
    )
  )

  // Previous sequence number
  io.seq_prev := Mux(io.seq_num === 0.U,
    MAX_SEQ_NUM.U,
    io.seq_num - 1.U
  )

  // Next sequence number
  io.seq_next := (io.seq_num + 1.U) & MAX_SEQ_NUM.U

  // Next TX sequence increment
  io.next_tx_seq_inc := (io.next_tx_seq + 1.U) & MAX_SEQ_NUM.U

  // Frame sequence number updates
  io.frame_seq_num := io.next_tx_seq
  io.frame_seq_num_ack := Mux(io.next_rx_seq === 0.U,
    MAX_SEQ_NUM.U,
    io.next_rx_seq - 1.U
  )

  // Next RX sequence update
  io.next_rx_seq_out := (io.seq_num + 1.U) & MAX_SEQ_NUM.U
  io.update_rx_seq := true.B

  // Acked sequence update
  val seq_num_ack_gt_acked = (io.seq_num_ack.asSInt - io.acked_seq.asSInt) > 0.S &&
                            (io.seq_num_ack.asSInt - io.acked_seq.asSInt) < HALF_MAX_SEQ_NUM.S
  
  io.acked_seq_out := io.seq_num_ack
  io.update_acked_seq := seq_num_ack_gt_acked
}

/**
 * Frame header bundle for OmniXtend protocol
 */
class TLOEFrameHeader extends Bundle {
  val seq_num = UInt(22.W)
  val seq_num_ack = UInt(22.W)
}

/**
 * Frame bundle for OmniXtend protocol
 */
class TLOEFrame extends Bundle {
  val header = new TLOEFrameHeader
  // Add other frame fields as needed
} 