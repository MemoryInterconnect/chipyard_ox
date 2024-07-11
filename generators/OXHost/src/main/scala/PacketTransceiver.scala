package omnixtend

import chisel3._
import chisel3.util._

/**
 * Transceiver
 *
 * This class implements a transceiver module using Chisel, which handles
 * transmission and reception of data packets. It includes logic to enqueue
 * and dequeue data, as well as to interface with an Endpoint module for 
 * packet processing.
 *
 * The transceiver supports operations to send and receive OmniXtend packets,
 * and also includes logic to generate specific types of messages (e.g., GET and PUT)
 * based on the opcode. Additionally, it analyzes received packets and outputs 
 * the result of the analysis.
 *
 * I/O:
 * - txAddr: Input address for transmission
 * - txData: Input data for transmission
 * - txOpcode: Opcode indicating the type of transaction (e.g., GET, PUT)
 * - txValid: Indicates if the transmission data is valid
 * - txReady: Indicates if the transceiver is ready to accept transmission data
 * - rxData: Output data received
 * - rxValid: Indicates if the received data is valid
 * - rxReady: Input indicating if the receiver is ready to accept data
 * - analysisResult: Output result of packet analysis
 */
class Transceiver extends Module {
  val io = IO(new Bundle {
    val txAddr = Input(UInt(64.W))    // Input address for transmission
    val txData = Input(UInt(64.W))    // Input data for transmission
    val txOpcode = Input(UInt(3.W))   // Opcode for transaction type (e.g., GET, PUT)
    val txValid = Input(Bool())       // Valid signal for transmission data
    val txReady = Output(Bool())      // Ready signal for transmission

    val rxData = Output(UInt(64.W))   // Output data received
    val rxValid = Output(Bool())      // Valid signal for received data
    val rxReady = Input(Bool())       // Ready signal for receiver

    val analysisResult = Output(UInt(64.W)) // Output signal for analysis result
  })

  // Define transmission and reception queues
  val txQueue = Module(new Queue(UInt(64.W), 16)) // Transmission queue
  val rxQueue = Module(new Queue(UInt(64.W), 16)) // Reception queue

  // Logic to enqueue data into the transmission queue
  txQueue.io.enq.valid := io.txValid
  txQueue.io.enq.bits := io.txData
  io.txReady := txQueue.io.enq.ready

  // Logic to dequeue data from the reception queue
  io.rxData := rxQueue.io.deq.bits
  io.rxValid := rxQueue.io.deq.valid
  rxQueue.io.deq.ready := io.rxReady

  // Instantiate the Endpoint module
  val endpoint = Module(new Endpoint)

  // Pass data from the transmission queue to the Endpoint
  endpoint.io.in.valid := txQueue.io.deq.valid
  endpoint.io.in.bits := txQueue.io.deq.bits
  txQueue.io.deq.ready := endpoint.io.in.ready

  // Pass data from the Endpoint to the reception queue
  rxQueue.io.enq.valid := endpoint.io.out.valid
  rxQueue.io.enq.bits := endpoint.io.out.bits
  endpoint.io.out.ready := rxQueue.io.enq.ready

  // Logic to generate messages based on the opcode
  when(io.txValid) {
    val ethernetHeader = EthernetHeader(
      preamble = Array.fill(8)(0xAA.toByte), // Example value
      destinationMac = Array.fill(6)(0xFF.toByte), // Example value
      sourceMac = Array.fill(6)(0x00.toByte), // Example value
      etherType = Array(0x08.toByte, 0x00.toByte) // Example value
    )
    val omniXtendHeader = OmniXtendHeader(
      virtualChannel = 0,
      sequenceNumber = 0,
      sequenceNumberAck = 0,
      ack = 0,
      credit = 0,
      chan = 0
    )

    val packetUInt = Wire(UInt(64.W))

    when(io.txOpcode === 4.U) { // GET opcode
      val getMessage = TloeMessage.createGetMessage(
        ethernetHeader = ethernetHeader,
        omniXtendHeader = omniXtendHeader,
        source = 0, // Example value
        address = 1, // Address value
        //address = io.txAddr.litValue().toLong,
        size = 8 // Example value
      )
      packetUInt := getMessage._2
    } .elsewhen(io.txOpcode === 0.U) { // PUT opcode
      val putMessage = TloeMessage.createPutMessage(
        ethernetHeader = ethernetHeader,
        omniXtendHeader = omniXtendHeader,
        source = 0, // Example value
        address = 1, // Address value
        //address = io.txAddr.litValue().toLong,
        size = 8, // Example value
        data = Array(2.toByte) // Data value
        //data = io.txData.litValue().toByteArray
      )
      packetUInt := putMessage._2
    } .otherwise {
      packetUInt := 0.U
    }

    // Pass the packet data to the Endpoint
    endpoint.io.in.bits := packetUInt
  }

  // Logic to analyze received packet data
  val receivedPacket = Wire(UInt(64.W))
  receivedPacket := io.rxData

  val analysisResult = TloeMessage.analyzePacket(receivedPacket)

  //io.analysisResult := analysisResult
  io.analysisResult := TloeMessage.analyzePacket(receivedPacket)

  // Initialize all inputs of the Endpoint module
  endpoint.io.txAddr := io.txAddr
  endpoint.io.txData := io.txData
  endpoint.io.txOpcode := io.txOpcode
  endpoint.io.memReadData := 0.U // Initialize with default value
}
