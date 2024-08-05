package omnixtend

import chisel3._
import chisel3.util._

/**
 * EthernetHeader class defines the structure of an Ethernet header.
 */
class EthernetHeader extends Bundle {
  val preamble  = UInt(64.W)    // 8-byte Preamble/SFD
  val destMAC   = UInt(48.W)    // 6-byte Destination MAC Address
  val srcMAC    = UInt(48.W)    // 6-byte Source MAC Address
  val etherType = UInt(16.W)    // 2-byte EtherType field
}

/**
 * OmniXtendHeader class defines the structure of an OmniXtend header.
 */
class OmniXtendHeader extends Bundle {
  val vc        = UInt(3.W)     // Virtual Channel
  val seqNum    = UInt(22.W)    // Sequence Number
  val seqNumAck = UInt(22.W)    // Sequence Number Acknowledgment
  val ack       = UInt(1.W)     // Acknowledgment
  val credit    = UInt(5.W)     // Credit
  val chan      = UInt(3.W)     // Channel
}

/**
 * TileLinkMessage class defines the structure of a TileLink message.
 */
class TileLinkMessage extends Bundle {
  val addr      = UInt(64.W)    // Address
  val data      = UInt(64.W)    // Data
  val opcode    = UInt(3.W)     // Opcode
  val param     = UInt(4.W)     // Parameter
  val size      = UInt(4.W)     // Size
  val source    = UInt(26.W)    // Source
  val sink      = UInt(26.W)    // Sink
  val err       = UInt(2.W)     // Error
  val domain    = UInt(8.W)     // Domain
}

/**
 * TloePacket class defines the structure of a TLoE packet.
 */
class TloePacket extends Bundle {
  val ethHeader   = new EthernetHeader
  val omniHeader  = new OmniXtendHeader
  val tileLinkMsg = new TileLinkMessage
  val padding     = UInt(64.W)   // Padding if necessary
  val tloeMask    = UInt(64.W)   // TLoE Frame Mask
}

/**
 * TloePacketGenerator object contains functions to create and manipulate TLoE packets.
 */
object TloePacketGenerator {

  /**
   * Create a new TLoE packet.
   */
  def createTloePacket(txAddr: UInt, txData: UInt, txOpcode: UInt): TloePacket = {
    val tloePacket = Wire(new TloePacket)
    tloePacket.ethHeader.preamble  := "hD5D5D5D5D5D5D5D5".U   // 8-byte Preamble/SFD in hexadecimal
    tloePacket.ethHeader.destMAC   := "hFFFFFFFFFFFF".U       // 6-byte Destination MAC Address in hexadecimal
    tloePacket.ethHeader.srcMAC    := "hAAAAAAAAAAAA".U       // 6-byte Source MAC Address in hexadecimal
    tloePacket.ethHeader.etherType := "hAAAA".U               // Example EtherType for TLoE in hexadecimal

    tloePacket.omniHeader.vc        := 0.U
    tloePacket.omniHeader.seqNum    := 0.U
    tloePacket.omniHeader.seqNumAck := 0.U
    tloePacket.omniHeader.ack       := 1.U
    tloePacket.omniHeader.credit    := 0.U
    tloePacket.omniHeader.chan      := 0.U

    tloePacket.tileLinkMsg.addr     := txAddr
    tloePacket.tileLinkMsg.data     := txData
    tloePacket.tileLinkMsg.opcode   := txOpcode
    tloePacket.tileLinkMsg.param    := 0.U
    tloePacket.tileLinkMsg.size     := 0.U
    tloePacket.tileLinkMsg.source   := 0.U
    tloePacket.tileLinkMsg.sink     := 0.U
    tloePacket.tileLinkMsg.err      := 0.U
    tloePacket.tileLinkMsg.domain   := 0.U

    tloePacket.padding              := 0.U
    tloePacket.tloeMask             := 0.U

    tloePacket
  }
}

