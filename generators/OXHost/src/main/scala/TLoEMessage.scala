package omnixtend

import chisel3._
import chisel3.util._

/**
 * EthernetHeader
 *
 * Represents the Ethernet header for an OmniXtend packet.
 *
 * @param preamble       Array of bytes for the Ethernet preamble
 * @param destinationMac Array of bytes for the destination MAC address
 * @param sourceMac      Array of bytes for the source MAC address
 * @param etherType      Array of bytes for the EtherType field
 */
case class EthernetHeader(
  preamble: Array[Byte],
  destinationMac: Array[Byte],
  sourceMac: Array[Byte],
  etherType: Array[Byte]
)

/**
 * OmniXtendHeader
 *
 * Represents the OmniXtend header for an OmniXtend packet.
 *
 * @param virtualChannel     Byte representing the virtual channel
 * @param sequenceNumber     Integer for the sequence number
 * @param sequenceNumberAck  Integer for the sequence number acknowledgment
 * @param ack                Byte for the acknowledgment
 * @param credit             Byte for the credit
 * @param chan               Byte for the channel
 */
case class OmniXtendHeader(
  virtualChannel: Byte,
  sequenceNumber: Int,
  sequenceNumberAck: Int,
  ack: Byte,
  credit: Byte,
  chan: Byte
)

/**
 * TileLinkMessage
 *
 * Represents a TileLink message within an OmniXtend packet.
 *
 * @param chan    Byte for the channel
 * @param opcode  Byte for the opcode
 * @param param   Byte for the parameter
 * @param size    Byte for the size
 * @param source  Integer for the source
 * @param address Long for the address
 * @param data    Array of bytes for the data
 * @param mask    Array of bytes for the mask
 */
case class TileLinkMessage(
  chan: Byte,
  opcode: Byte,
  param: Byte,
  size: Byte,
  source: Int,
  address: Long,
  data: Array[Byte],
  mask: Array[Byte]
)

/**
 * OmniXtendPacket
 *
 * Represents a complete OmniXtend packet, including Ethernet and OmniXtend headers,
 * TileLink messages, padding, frame mask, and frame check sequence (FCS).
 *
 * @param ethernetHeader   The Ethernet header
 * @param omniXtendHeader  The OmniXtend header
 * @param tileLinkMessages Array of TileLink messages
 * @param padding          Array of bytes for padding
 * @param frameMask        Long for the frame mask
 * @param fcs              Array of bytes for the frame check sequence
 */
case class OmniXtendPacket(
  ethernetHeader: EthernetHeader,
  omniXtendHeader: OmniXtendHeader,
  tileLinkMessages: Array[TileLinkMessage],
  padding: Array[Byte],
  frameMask: Long,
  fcs: Array[Byte]
)

/**
 * TloeMessage
 *
 * Object containing utility functions to create and analyze OmniXtend packets.
 */
object TloeMessage {

  /**
   * Creates a GET message for an OmniXtend packet.
   *
   * @param ethernetHeader  The Ethernet header
   * @param omniXtendHeader The OmniXtend header
   * @param source          The source identifier
   * @param address         The address for the GET request
   * @param size            The size of the data
   * @return A tuple containing the packet as a byte array and as a UInt
   */
  def createGetMessage(
    ethernetHeader: EthernetHeader,
    omniXtendHeader: OmniXtendHeader,
    source: Int,
    address: Long,
    size: Byte
  ): (Array[Byte], UInt) = {
    val getMessage = TileLinkMessage(
      chan = 1, // Channel A
      opcode = 4, // GET opcode
      param = 0,
      size = size,
      source = source,
      address = address,
      data = Array.emptyByteArray,
      mask = Array.emptyByteArray
    )

    val omniXtendPacket = createOmniXtendPacket(
      ethernetHeader = ethernetHeader,
      omniXtendHeader = omniXtendHeader,
      tileLinkMessages = Array(getMessage)
    )

    val packetBytes = packetToBytes(omniXtendPacket)
    val packetUInt = bytesToUInt(packetBytes)

    (packetBytes, packetUInt)
  }

  /**
   * Creates a PUT message for an OmniXtend packet.
   *
   * @param ethernetHeader  The Ethernet header
   * @param omniXtendHeader The OmniXtend header
   * @param source          The source identifier
   * @param address         The address for the PUT request
   * @param size            The size of the data
   * @param data            The data to be written
   * @return A tuple containing the packet as a byte array and as a UInt
   */
  def createPutMessage(
    ethernetHeader: EthernetHeader,
    omniXtendHeader: OmniXtendHeader,
    source: Int,
    address: Long,
    size: Byte,
    data: Array[Byte]
  ): (Array[Byte], UInt) = {
    val putMessage = TileLinkMessage(
      chan = 1, // Channel A
      opcode = 0, // PUT opcode
      param = 0,
      size = size,
      source = source,
      address = address,
      data = data,
      mask = Array.fill[Byte](data.length)(0xFF.toByte)
    )

    val omniXtendPacket = createOmniXtendPacket(
      ethernetHeader = ethernetHeader,
      omniXtendHeader = omniXtendHeader,
      tileLinkMessages = Array(putMessage)
    )

    val packetBytes = packetToBytes(omniXtendPacket)
    val packetUInt = bytesToUInt(packetBytes)

    (packetBytes, packetUInt)
  }

  /**
   * Creates an OmniXtend packet from the given headers and TileLink messages.
   *
   * @param ethernetHeader   The Ethernet header
   * @param omniXtendHeader  The OmniXtend header
   * @param tileLinkMessages The array of TileLink messages
   * @return The constructed OmniXtend packet
   */
  def createOmniXtendPacket(
    ethernetHeader: EthernetHeader,
    omniXtendHeader: OmniXtendHeader,
    tileLinkMessages: Array[TileLinkMessage]
  ): OmniXtendPacket = {
    // Create padding and mask fields (initialized to zero in this example)
    val padding = Array.fill(0)(0.toByte)
    val frameMask = 0xFFFFFFFFFFFFFFFFL
    val fcs = Array.fill(0)(0.toByte)

    OmniXtendPacket(
      ethernetHeader = ethernetHeader,
      omniXtendHeader = omniXtendHeader,
      tileLinkMessages = tileLinkMessages,
      padding = padding,
      frameMask = frameMask,
      fcs = fcs
    )
  }

  /**
   * Converts an OmniXtend packet to a byte array.
   *
   * @param packet The OmniXtend packet
   * @return The byte array representation of the packet
   */
  def packetToBytes(packet: OmniXtendPacket): Array[Byte] = {
    val ethernetHeaderBytes = packet.ethernetHeader.preamble ++
      packet.ethernetHeader.destinationMac ++
      packet.ethernetHeader.sourceMac ++
      packet.ethernetHeader.etherType

    val omniXtendHeaderBytes = Array(
      packet.omniXtendHeader.virtualChannel,
      (packet.omniXtendHeader.sequenceNumber >> 16).toByte,
      (packet.omniXtendHeader.sequenceNumber >> 8).toByte,
      packet.omniXtendHeader.sequenceNumber.toByte,
      (packet.omniXtendHeader.sequenceNumberAck >> 16).toByte,
      (packet.omniXtendHeader.sequenceNumberAck >> 8).toByte,
      packet.omniXtendHeader.sequenceNumberAck.toByte,
      ((packet.omniXtendHeader.ack << 7) | (packet.omniXtendHeader.credit << 2) | packet.omniXtendHeader.chan).toByte
    )

    val tileLinkMessageBytes = packet.tileLinkMessages.flatMap { msg =>
      val headerBytes = Array(
        msg.chan,
        msg.opcode,
        msg.param,
        msg.size,
        (msg.source >> 24).toByte,
        (msg.source >> 16).toByte,
        (msg.source >> 8).toByte,
        msg.source.toByte,
        (msg.address >> 56).toByte,
        (msg.address >> 48).toByte,
        (msg.address >> 40).toByte,
        (msg.address >> 32).toByte,
        (msg.address >> 24).toByte,
        (msg.address >> 16).toByte,
        (msg.address >> 8).toByte,
        msg.address.toByte
      )

      headerBytes ++ msg.mask ++ msg.data
    }

    val frameMaskBytes = Array(
      (packet.frameMask >> 56).toByte,
      (packet.frameMask >> 48).toByte,
      (packet.frameMask >> 40).toByte,
      (packet.frameMask >> 32).toByte,
      (packet.frameMask >> 24).toByte,
      (packet.frameMask >> 16).toByte,
      (packet.frameMask >> 8).toByte,
      packet.frameMask.toByte
    )

    ethernetHeaderBytes ++ omniXtendHeaderBytes ++ tileLinkMessageBytes ++ packet.padding ++ frameMaskBytes ++ packet.fcs
  }

  /**
   * Converts a byte array to a Chisel UInt.
   *
   * @param bytes The byte array
   * @return The UInt representation of the byte array
   */
  def bytesToUInt(bytes: Array[Byte]): UInt = {
    val uintValue = bytes.foldLeft(BigInt(0)) { (acc, byte) =>
      (acc << 8) | (byte & 0xFF)
    }
    uintValue.U((bytes.length * 8).W)
  }

  /**
   * Analyzes an OmniXtend packet represented as a UInt.
   *
   * @param packet The OmniXtend packet as a UInt
   * @return The result of the analysis
   */
  def analyzePacket(packet: UInt): UInt = {
    // Convert the OmniXtend packet to a byte array
    val packetBytes = packet.asTypeOf(Vec(packet.getWidth / 8, UInt(8.W)))
    val result = 100.U

/*
    // Extract OmniXtend header (hardcoded positions in this example)
    val omniXtendHeader = OmniXtendHeader(
      virtualChannel = packetBytes(0).litValue().toByte,
      sequenceNumber = ((packetBytes(1) << 16) | (packetBytes(2) << 8) | packetBytes(3)).litValue().toInt,
      sequenceNumberAck = ((packetBytes(4) << 16) | (packetBytes(5) << 8) | packetBytes(6)).litValue().toInt,
      ack = packetBytes(7)(7).litValue().toByte,
      credit = packetBytes(7)(6, 2).litValue().toByte,
      chan = packetBytes(7)(1, 0).litValue().toByte
    )

    // Extract TileLink message (first message in this example)
    val tileLinkMessage = TileLinkMessage(
      chan = packetBytes(8).litValue().toByte,
      opcode = packetBytes(9).litValue().toByte,
      param = packetBytes(10).litValue().toByte,
      size = packetBytes(11).litValue().toByte,
      source = ((packetBytes(12) << 24) | (packetBytes(13) << 16) | (packetBytes(14) << 8) | packetBytes(15)).litValue().toInt,
      address = ((packetBytes(16) << 56) | (packetBytes(17) << 48) | (packetBytes(18) << 40) | (packetBytes(19) << 32) |
                 (packetBytes(20) << 24) | (packetBytes(21) << 16) | (packetBytes(22) << 8) | packetBytes(23)).litValue().toLong,
      data = packetBytes.slice(24, 24 + 8).map(_.litValue().toByte).toArray,
      mask = packetBytes.slice(32, 32 + 8).map(_.litValue().toByte).toArray
    )

    // Extract different values based on GET and PUT messages
    val extractedData = Wire(UInt(64.W))
    when(tileLinkMessage.opcode.asUInt === 4.U) { // GET
      extractedData := tileLinkMessage.address.U
    } .elsewhen(tileLinkMessage.opcode.asUInt === 0.U) { // PUT
      extractedData := tileLinkMessage.data.map(_.toLong).reduceLeft(_ | _).U
    } .otherwise {
      extractedData := 0.U
    }
    extractedData
*/
    result
  }
}
