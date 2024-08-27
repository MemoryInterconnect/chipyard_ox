package omnixtend

import chisel3._
import chisel3.util._

object OXread {

  def readPacket(txAddr: UInt, seqNum: UInt, seqNumAck: UInt): UInt ={

    // Create a new instance of the TloePacket (a user-defined bundle)
    val tloePacket = Wire(new TloePacket)

    // Populate the Ethernet header fields
    tloePacket.ethHeader.destMAC    := "h98039b6c5892".U  // 6-byte Destination MAC Address
    tloePacket.ethHeader.srcMAC     := "h123456789ABC".U  // 6-byte Source MAC Address
    tloePacket.ethHeader.etherType  := "hAAAA".U          // 2-byte EtherType (Example value for TLoE)

    // Populate the OmniXtend header fields
    tloePacket.omniHeader.vc        := 0.U      // Virtual Channel ID
    tloePacket.omniHeader.res1      := 0.U      // Reserved field 1
    tloePacket.omniHeader.seqNum    := seqNum   // Sequence Number
    tloePacket.omniHeader.seqNumAck := seqNumAck// Acknowledged Sequence Number
    tloePacket.omniHeader.ack       := 1.U      // Acknowledgment flag
    tloePacket.omniHeader.res2      := 0.U      // Reserved field 2
    tloePacket.omniHeader.credit    := 0.U      // Credit field
    tloePacket.omniHeader.chan      := 0.U      // Channel ID

    // Populate the high part of the TileLink message fields
    tloePacket.tlMsgHigh.res1       := 0.U      // Reserved field 1
    tloePacket.tlMsgHigh.chan       := 1.U      // Channel ID
    tloePacket.tlMsgHigh.opcode     := 4.U      // TileLink operation code (input parameter)
    tloePacket.tlMsgHigh.res2       := 0.U      // Reserved field 2
    tloePacket.tlMsgHigh.param      := 0.U      // TileLink parameter field
    tloePacket.tlMsgHigh.size       := 3.U      // Size of the transaction
    tloePacket.tlMsgHigh.domain     := 0.U      // Domain field
    tloePacket.tlMsgHigh.err        := 0.U      // Error field
    tloePacket.tlMsgHigh.res3       := 0.U      // Reserved field 3
    tloePacket.tlMsgHigh.source     := 0.U      // Source field

    // Populate the low part of the TileLink message fields
    tloePacket.tlMsgLow.addr        := txAddr  // TileLink address (input parameter)
    //tloePacket.tlMsgLow.addr        := "h0000000100000000".U(64.W)

    // Define Padding and Mask
    val padding = 0.U(192.W)                    // 192-bit padding
    //val mask = "h0000000000000001".U(64.W)      // 64-bit mask, all bits set to 1
    val mask = "h0000000000000001".U(64.W)      // 64-bit mask, all bits set to 1

    // Convert the TLoE packet bundle to a single UInt representing the entire packet
    val packetWithPadding = Cat(tloePacket.asUInt, padding, mask, "h0000".U(16.W))
    
    packetWithPadding
  }

  def writePacket(txAddr: UInt, txData:UInt, seqNum: UInt, seqNumAck: UInt): UInt ={
    // Create a new instance of the TloePacket (a user-defined bundle)
    val tloePacket = Wire(new TloePacket)

    // Populate the Ethernet header fields
    tloePacket.ethHeader.destMAC    := "h98039b6c5892".U  // 6-byte Destination MAC Address
    tloePacket.ethHeader.srcMAC     := "h123456789ABC".U  // 6-byte Source MAC Address
    tloePacket.ethHeader.etherType  := "hAAAA".U          // 2-byte EtherType (Example value for TLoE)

    // Populate the OmniXtend header fields
    tloePacket.omniHeader.vc        := 0.U      // Virtual Channel ID
    tloePacket.omniHeader.res1      := 0.U      // Reserved field 1
    tloePacket.omniHeader.seqNum    := seqNum   // Sequence Number
    tloePacket.omniHeader.seqNumAck := seqNumAck// Acknowledged Sequence Number
    tloePacket.omniHeader.ack       := 1.U      // Acknowledgment flag
    tloePacket.omniHeader.res2      := 0.U      // Reserved field 2
    tloePacket.omniHeader.credit    := 0.U      // Credit field
    tloePacket.omniHeader.chan      := 0.U      // Channel ID

    // Populate the high part of the TileLink message fields
    tloePacket.tlMsgHigh.res1       := 0.U      // Reserved field 1
    tloePacket.tlMsgHigh.chan       := 1.U      // Channel ID
    tloePacket.tlMsgHigh.opcode     := 0.U      // TileLink operation code (input parameter)
    tloePacket.tlMsgHigh.res2       := 0.U      // Reserved field 2
    tloePacket.tlMsgHigh.param      := 0.U      // TileLink parameter field
    tloePacket.tlMsgHigh.size       := 3.U      // Size of the transaction
    tloePacket.tlMsgHigh.domain     := 0.U      // Domain field
    tloePacket.tlMsgHigh.err        := 0.U      // Error field
    tloePacket.tlMsgHigh.res3       := 0.U      // Reserved field 3
    tloePacket.tlMsgHigh.source     := 0.U      // Source field

    // Populate the low part of the TileLink message fields
    tloePacket.tlMsgLow.addr        := txAddr  // TileLink address (input parameter)
    //tloePacket.tlMsgLow.addr        := "h0000000100000000".U(64.W)

    // Define Padding and Mask
    val padding = 0.U(128.W)                    // 192-bit padding
    val mask = "h0000000000000001".U(64.W)      // 64-bit mask, all bits set to 1

    // Convert the TLoE packet bundle to a single UInt representing the entire packet
    val packetWithPadding = Cat(tloePacket.asUInt, txData, padding, mask, "h1111".U(16.W))
    
    packetWithPadding
  }
}
