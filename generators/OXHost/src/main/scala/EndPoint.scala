package omnixtend

import chisel3._
import chisel3.util._

/**
 * Endpoint
 *
 * This class represents a memory node in the OmniXtend protocol.
 * It handles memory read and write requests, extracts TileLink messages from incoming packets,
 * and provides the read data or acknowledges the write operation.
 *
 * I/O:
 * - in: Input Decoupled interface for incoming data packets
 * - out: Output Decoupled interface for outgoing data packets
 * - txAddr: Input address for transmission
 * - txData: Input data for transmission
 * - txOpcode: Opcode indicating the type of transaction (e.g., GET, PUT)
 * - memReadAddr: Output address for memory read
 * - memReadData: Input data read from memory
 * - memWriteAddr: Output address for memory write
 * - memWriteData: Output data to be written to memory
 * - memWriteEn: Output enable signal for memory write
 * - input: Additional input signal (32 bits)
 * - output: Additional output signal (32 bits)
 */
class Endpoint extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(UInt(64.W)))  // Input Decoupled interface for incoming data packets
    val out = Decoupled(UInt(64.W))          // Output Decoupled interface for outgoing data packets
    val txAddr = Input(UInt(64.W))           // Input address for transmission
    val txData = Input(UInt(64.W))           // Input data for transmission
    val txOpcode = Input(UInt(3.W))          // Opcode for transaction type (e.g., GET, PUT)
    val memReadAddr = Output(UInt(64.W))     // Output address for memory read
    val memReadData = Input(UInt(64.W))      // Input data read from memory
    val memWriteAddr = Output(UInt(64.W))    // Output address for memory write
    val memWriteData = Output(UInt(64.W))    // Output data to be written to memory
    val memWriteEn = Output(Bool())          // Output enable signal for memory write
/*
    val input = Input(UInt(32.W))            // Additional input signal (32 bits)
    val output = Output(UInt(32.W))          // Additional output signal (32 bits)
*/
  })

  // Instantiate a 64-bit memory module with a depth of 1024
  val mem = SyncReadMem(1024, UInt(64.W))

  // Default values for output signals
  val readData = Wire(UInt(64.W))
  val writeData = Wire(UInt(64.W))
  val writeEn = Wire(Bool())

  readData := 0.U
  writeData := 0.U
  writeEn := false.B

  // Packet analysis logic
  // Convert the 64-bit packet into a vector of bytes
  val packetBytes = io.in.bits.asTypeOf(Vec(8, UInt(8.W)))

  // Extract TileLink message from the packet (example extracts only the first message)
  val tileLinkMessage = Wire(new Bundle {
    val chan = UInt(1.W)
    val opcode = UInt(3.W)
    val param = UInt(3.W)
    val size = UInt(3.W)
    val source = UInt(6.W)
    val address = UInt(64.W)
    val data = UInt(64.W)
  })

  tileLinkMessage.chan := packetBytes(0)
  tileLinkMessage.opcode := packetBytes(1)
  tileLinkMessage.param := packetBytes(2)
  tileLinkMessage.size := packetBytes(3)
  tileLinkMessage.source := packetBytes(4)
  tileLinkMessage.address := 100000.U
  tileLinkMessage.data := 12345.U


//  tileLinkMessage.address := Cat(packetBytes(5), packetBytes(6), packetBytes(7), packetBytes(8), packetBytes(9), packetBytes(10), packetBytes(11), packetBytes(12))
//  tileLinkMessage.data := Cat(packetBytes(13), packetBytes(14), packetBytes(15), packetBytes(16), packetBytes(17), packetBytes(18), packetBytes(19), packetBytes(20))

/*
  // Delay simulation
  val delayCycles = 10000
  val reg = RegInit(0.U(32.W))

  io.input := 0.U
  // Process GET request
  when(tileLinkMessage.opcode === 4.U) { // GET opcode
    readData := mem.read(500.U)

    reg := io.input
    for (i <- 0 until delayCycles) {
      reg := reg
    }
  }
  io.output := reg
*/

  // Process PUT request
  when(tileLinkMessage.opcode === 0.U) { // PUT opcode
    mem.write(tileLinkMessage.address, tileLinkMessage.data)
    writeData := tileLinkMessage.data
    writeEn := true.B
  }

  // Connect IO signals
  io.memReadAddr := tileLinkMessage.address
  io.memWriteAddr := tileLinkMessage.address
  //io.memReadData := readData
  io.memWriteData := writeData
  io.memWriteEn := writeEn

  // Set the default value for the out signal to the read data
  //io.out.bits := readData
  io.out.bits := 12345.U
  io.out.valid := io.in.valid
  io.in.ready := io.out.ready
}
