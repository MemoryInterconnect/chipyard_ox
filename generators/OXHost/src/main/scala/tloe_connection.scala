package omnixtend

import chisel3._
import chisel3.util._

// Constants for OmniXtend protocol
object TLOEConnectionConstants {
  val CHANNEL_NUM = 6
  val CREDIT_DEFAULT = 9
  val CONN_PACKET_SIZE = 72  // Size in bytes
  val CONN_RESEND_TIME = 5000000L  // 5 seconds in microseconds
  
  // Message types
  val TYPE_NORMAL = 0.U(4.W)
  val TYPE_OPEN_CONNECTION = 2.U(4.W)
  val TYPE_CLOSE_CONNECTION = 3.U(4.W)
  val TYPE_ACKONLY = 4.U(4.W)
  
  // Channels
  val CHANNEL_A = 1.U(3.W)
  val CHANNEL_B = 2.U(3.W)
  val CHANNEL_C = 3.U(3.W)
  val CHANNEL_D = 4.U(3.W)
  val CHANNEL_E = 5.U(3.W)
}

/**
 * TLOEConnection handles the connection management for OmniXtend protocol.
 * It manages connection establishment, maintenance, and termination.
 */
class TLOEConnection extends Module {
  import TLOEConnectionConstants._
  
  val io = IO(new Bundle {
    // Control signals
    val ox_open  = Input(Bool())
    val ox_close = Input(Bool())
    val debug1   = Input(Bool())
    val debug2   = Input(Bool())

    // Ethernet interface
    val txdata   = Output(UInt(64.W))
    val txvalid  = Output(Bool())
    val txlast   = Output(Bool())
    val txkeep   = Output(UInt(8.W))
    val txready  = Input(Bool())
    val rxdata   = Input(UInt(64.W))
    val rxvalid  = Input(Bool())
    val rxlast   = Input(Bool())

    // Connection state
    val connected = Output(Bool())
    
    // Flow control interface
    val set_credit = Output(Bool())
    val channel = Output(UInt(3.W))
    val credit = Output(UInt(5.W))
    
    // Sequence management interface
    val next_tx_seq = Input(UInt(22.W))
    val ackd_seq = Input(UInt(22.W))
    val next_rx_seq = Input(UInt(22.W))
    val next_tx_seq_out = Output(UInt(22.W))
    val ackd_seq_out = Output(UInt(22.W))
    val next_rx_seq_out = Output(UInt(22.W))
    val seq_update = Output(Bool())
  })

  // State registers
  val connection = RegInit(0.U(1.W))
  val timer_active = RegInit(false.B)  // Keep the register but won't use
  val timer_count = RegInit(0.U(32.W))  // Keep the register but won't use
  val conn_flag = RegInit(false.B)

  // Add channel index register at module level
  val channelIndex = RegInit(1.U(3.W))

  // Add packet transmission tracking
  val currentPacketSent = RegInit(false.B)

  // Add packet resend timer
  val packet_timer = RegInit(0.U(32.W))  // Keep the register but won't use
  val packet_resend = RegInit(false.B)  // Keep the register but won't use

  // Default output values
  io.txdata := 0.U
  io.txvalid := false.B
  io.txlast := false.B
  io.txkeep := 0.U
  io.connected := connection
  io.set_credit := false.B
  io.channel := 0.U
  io.credit := 0.U
  io.next_tx_seq_out := io.next_tx_seq
  io.ackd_seq_out := io.ackd_seq
  io.next_rx_seq_out := io.next_rx_seq
  io.seq_update := false.B

  // Packet generation and transmission
  val oPacket = RegInit(0.U(576.W))
  val nAckPacket = RegInit(0.U(576.W))
  val txPacketVec = RegInit(VecInit(Seq.fill(14)(0.U(64.W))))
  val txPacketVecSize = RegInit(0.U(4.W))
  val sendPacket = RegInit(false.B)
  val txComplete = RegInit(false.B)
  val txIndex = RegInit(0.U(4.W))

  // Packet reception registers
  val rxcount = RegInit(0.U(8.W))
  val rPacketVec = RegInit(VecInit(Seq.fill(14)(0.U(64.W))))
  val rxPacketReceived = RegInit(false.B)

  // Connection states for open and close operations
  object ConnectionState {
    val open_idle :: open_serving :: open_waiting :: Nil = Enum(3)
    val close_idle :: close_serving :: close_waiting :: Nil = Enum(3)
  }
  import ConnectionState._
  
  val open_state = RegInit(open_idle)
  val close_state = RegInit(close_idle)

  // Comment out timer logic
  /*
  // Timer logic for both connection retry and packet resend
  when(timer_active) {
    timer_count := timer_count + 1.U
    when(timer_count >= CONN_RESEND_TIME.U) {
      timer_count := 0.U
      when(open_state === open_serving) {
        channelIndex := 1.U
        currentPacketSent := false.B
        txComplete := false.B
        sendPacket := false.B
      }
    }
  }

  // Packet resend timer logic
  when(sendPacket && connection === 1.U) {
    packet_timer := packet_timer + 1.U
    when(packet_timer >= PACKET_RESEND_TIME.U) {
      packet_timer := 0.U
      when(!txComplete) {
        // Reset for packet resend
        txIndex := 0.U
        sendPacket := true.B
        packet_resend := true.B
      }
    }
  }.otherwise {
    packet_timer := 0.U
    packet_resend := false.B
  }
  */

  // Function to send a frame
  def send_frame(msgType: UInt, seq_num: UInt, seq_num_ack: UInt, chan: UInt, credit: UInt) = {
    val packet = Wire(new TloePacket)
    
    // Set packet fields
    packet.ethHeader.destMAC := OXPacket.destMac
    packet.ethHeader.srcMAC := OXPacket.srcMac
    packet.ethHeader.etherType := OXPacket.etherType
    
    packet.omniHeader.vc := 0.U
    packet.omniHeader.msgType := msgType
    packet.omniHeader.res1 := 0.U
    packet.omniHeader.seqNum := seq_num
    packet.omniHeader.seqNumAck := seq_num_ack
    packet.omniHeader.ack := 1.U
    packet.omniHeader.res2 := 0.U
    packet.omniHeader.chan := chan
    packet.omniHeader.credit := credit
    
    packet.tlMsgHigh := 0.U.asTypeOf(new TLMessageHigh)
    packet.tlMsgLow := 0.U.asTypeOf(new TLMessageLow)
    
    // Convert to packet format
    val packetWithPadding = Cat(packet.asUInt, 0.U(272.W))
    
    // Split into 64-bit chunks for transmission
    txPacketVec := VecInit(Seq.tabulate(9) { i =>
      val high = 576 - (64 * i) - 1
      val low = math.max(576 - 64 * (i + 1), 0)
      packetWithPadding(high, low)
    } ++ Seq.fill(5)(0.U(64.W)))
    
    txPacketVecSize := 9.U
    sendPacket := true.B
    txComplete := false.B
    txIndex := 0.U
  }

  // State machine for open connection management
  switch(open_state) {
    is(open_idle) {
      when(io.ox_open) {
        open_state := open_serving
        timer_active := true.B
        timer_count := 0.U
        channelIndex := 1.U
        currentPacketSent := false.B
        txComplete := false.B
        sendPacket := false.B
      }
    }

    is(open_serving) {
      // Keep timer active until connection is established
      timer_active := true.B
      
      when(!currentPacketSent) {  // Only send new packet when current packet is fully transmitted
        when(channelIndex < CHANNEL_NUM.U) {
          val msgType = Mux(channelIndex === CHANNEL_A, TYPE_OPEN_CONNECTION, TYPE_NORMAL)
          send_frame(msgType, (channelIndex-1.U), io.next_rx_seq - 1.U, channelIndex, CREDIT_DEFAULT.U)
          currentPacketSent := true.B
        }.otherwise {
          /*
          channelIndex := 1.U  // Reset for next potential resend
          when(rxPacketReceived) {  // Only move to waiting if we received a response
            timer_active := false.B  // Stop timer when moving to waiting state
            open_state := open_waiting
          }
          */
          open_state := open_waiting  // Move to waiting state after sending all channel packets
          timer_active := false.B     // Stop timer
        }
      }.elsewhen(txComplete) {  // Wait for current packet to complete before moving to next
        channelIndex := channelIndex + 1.U
        currentPacketSent := false.B
        txComplete := false.B
      }
    }

    is(open_waiting) {
      // Process received packets
      when(io.rxvalid) {
        rxcount := rxcount + 1.U
        rPacketVec(rxcount) := io.rxdata
        when(io.rxlast) {
          rxPacketReceived := true.B
          rxcount := 0.U
        }
      }

      when(rxPacketReceived) {
        val packet = Wire(new TloePacket)
        packet := rPacketVec.asUInt.asTypeOf(new TloePacket)
        
        // Update credit and sequence numbers
        when(packet.omniHeader.seqNum === io.next_rx_seq) {
          io.set_credit := true.B
          io.channel := packet.omniHeader.chan
          io.credit := packet.omniHeader.credit
          
          io.next_rx_seq_out := packet.omniHeader.seqNum + 1.U
          io.ackd_seq_out := packet.omniHeader.seqNumAck
          io.seq_update := true.B
        }
        
        rxPacketReceived := false.B
      }
    }
  }

  // State machine for close connection management
  switch(close_state) {
    is(close_idle) {
      when(io.ox_close) {
        close_state := close_serving
        timer_active := true.B
        timer_count := 0.U
      }
    }

    is(close_serving) {
      when(!timer_active) {
        send_frame(TYPE_CLOSE_CONNECTION, io.next_tx_seq, io.next_rx_seq - 1.U, 0.U, 0.U)
        io.next_tx_seq_out := io.next_tx_seq + 1.U
        io.seq_update := true.B
        close_state := close_waiting
      }
    }

    is(close_waiting) {
      when(txComplete) {
        close_state := close_idle
        open_state := open_idle  // Reset open state as well
      }
    }
  }

  // State machine for sending packets via AXI-Stream interface
  when(sendPacket) {
    when(io.txready && txIndex < txPacketVecSize) {
      io.txdata := TloePacGen.toBigEndian(txPacketVec(txIndex))
      io.txvalid := true.B

      // Handle last packet differently
      when(txIndex === (txPacketVecSize - 1.U)) {
        io.txlast := true.B
        io.txkeep := "h3F".U
      }.otherwise {
        io.txlast := false.B
        io.txkeep := "hFF".U
      }

      txIndex := txIndex + 1.U
    }.elsewhen(txIndex >= txPacketVecSize) {
      // Reset all signals explicitly
      io.txdata := 0.U
      io.txvalid := false.B
      io.txlast := false.B
      io.txkeep := 0.U

      // Remove packet_resend check since timer is disabled
      txIndex := 0.U
      sendPacket := false.B
      txComplete := true.B
    }
  }

  // Update connection state
  connection := open_state === open_waiting && close_state === close_idle

  // Debug signals
  when(io.debug1) {
    // Add debug behavior if needed
  }

  when(io.debug2) {
    // Add debug behavior if needed
  }
} 