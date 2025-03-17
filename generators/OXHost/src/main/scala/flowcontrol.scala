package omnixtend

import chisel3._
import chisel3.util._

/**
 * FlowControl manages credit-based flow control for OmniXtend channels.
 * It tracks credits for each channel and provides credit status information.
 */
class FlowControl extends Module {
  val io = IO(new Bundle {
    // Credit management interface
    val set_credit = Input(Bool())
    val channel = Input(UInt(3.W))
    val credit = Input(UInt(5.W))
    
    // Credit status
    val all_channels_credited = Output(Bool())
    val channel_credit = Output(Vec(TLOEConnectionConstants.CHANNEL_NUM, UInt(5.W)))
  })

  // Credit registers for each channel
  val credits = RegInit(VecInit(Seq.fill(TLOEConnectionConstants.CHANNEL_NUM)(0.U(5.W))))
  
  // Update credits when requested
  when(io.set_credit) {
    credits(io.channel) := io.credit
  }
  
  // Check if all channels have credits
  io.all_channels_credited := credits.tail.foldLeft(true.B)((acc, credit) => acc && credit =/= 0.U)
  
  // Output current credit values
  io.channel_credit := credits
} 