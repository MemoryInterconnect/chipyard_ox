package omnixtend

import chisel3._
import chisel3.util._

/**
 * Transceiver module interfaces with the TileLink messages and manages the
 * serialization and deserialization of data for transmission and reception.
 * This module acts as a bridge between TileLink and Ethernet, handling both
 * transmission and reception of data packets.
 */
class Transceiver extends Module {
  val io = IO(new Bundle {
    // TileLink interface for transmission
    val txAddr      = Input(UInt(64.W))   // Input address for transmission
    val txData      = Input(UInt(64.W))   // Input data for transmission
    val txOpcode    = Input(UInt(3.W))    // Input opcode for transmission
    val txValid     = Input(Bool())       // Valid signal for transmission
    val txReady     = Output(Bool())      // Ready signal for transmission

    // TileLink interface for reception
    val rxData      = Output(UInt(64.W))  // Output data received
    val rxValid     = Output(Bool())      // Valid signal for received data
    val rxReady     = Input(Bool())       // Ready signal for receiver

    // Ethernet IP core interface
    val toggle_last = Output(Bool())
    val axi_rxdata  = Output(UInt(64.W))
    val axi_rxvalid = Output(Bool())
    val txdata      = Output(UInt(64.W))
    val txvalid     = Output(Bool())
    val txlast      = Output(Bool())
    val txready     = Input(Bool())
    val rxdata      = Input(UInt(64.W))
    val rxvalid     = Input(Bool())
    val rxlast      = Input(Bool())
  })

  val seq     = RegInit(2.U(16.W))   // Sequence number (0~65535)
  val seqAck = RegInit(3.U(16.W))   // Sequence number Ack (0~65535)
  val addr = RegInit(0.U(64.W))

  /////////////////////////////////////////////////
  // Connect to Ethnenet IP

  // Configuration for packet replication cycles
  val replicationCycles = 10
   
  // Registers to hold the AXI-Stream signals
  val toggle_last = RegInit(false.B)
  val axi_rxdata = RegInit(0.U(64.W))
  val axi_rxvalid = RegInit(false.B)
  val rxcount = RegInit(0.U(log2Ceil(replicationCycles).W))

  // Handling the valid and last signals in AXI-Stream
  when (io.rxlast) {
    toggle_last := true.B
  }
  when (io.rxvalid) {
    rxcount := rxcount + 1.U
    when (rxcount === 0.U){
      axi_rxdata := io.rxdata
      axi_rxvalid := true.B
    } .otherwise{
      axi_rxvalid := false.B
    }
  } .otherwise {
    rxcount := 0.U
  }

  // Connecting internal signals to output interface
  io.toggle_last := toggle_last
  io.axi_rxdata := axi_rxdata
  io.axi_rxvalid := axi_rxvalid

  // TX AXI-Stream to Tilelink (Transmission Path)
  val axi_txdata  = RegInit(0.U(64.W))
  val axi_txvalid = RegInit(false.B)
  val axi_txlast  = RegInit(false.B)
  val txcount     = RegInit(0.U(log2Ceil(replicationCycles).W))

  //val buf_vec     = RegInit(VecInit(Seq.fill(10)(0.U(64.W))))
  val buf_vec     = RegInit(VecInit(Seq.fill(9)(0.U(64.W))))

  val txaddrReg = RegInit(io.txAddr)
  val pPacket = Wire(UInt(576.W)) 
  //val pPacket = RegInit(0.U(576.W)) 
  val bPacket = RegInit(false.B)
  val addrReg = RegNext(io.txAddr)

  pPacket := 0.U

  // TX AXI-Stream data/valid/last
  when (io.txValid) {

    when (io.txOpcode === 4.U) {		// READ
      pPacket := OXread.createOXPacket(io.txAddr, seq, seqAck)
      //pPacket := OXread.readPacket("h100000000".U(64.W), seq, seqAck)

      buf_vec := VecInit(Seq.tabulate(9) (i => {
        val packetWidth = OXread.readPacket(io.txAddr, seq, seqAck).getWidth
        val high = packetWidth - (64 * i) - 1
	    val low = math.max(packetWidth - 64 * (i + 1), 0)

        pPacket (high, low)
      }))
      seq := seq + 1.U

      axi_txlast := false.B
      txcount := 1.U
    }.elsewhen (io.txOpcode === 0.U) {	// WRITE
      pPacket := OXread.writePacket(io.txAddr, io.txData, seq, seqAck)
      //pPacket := OXread.writePacket("h100000000".U(64.W), io.txData, seq, seqAck)

      buf_vec := VecInit(Seq.tabulate(9) (i => {
        val packetWidth = OXread.readPacket(io.txAddr, seq, seqAck).getWidth
        val high = packetWidth - (64 * i) - 1
	    val low = math.max(packetWidth - 64 * (i + 1), 0)

        pPacket (high, low)
      }))
      seq := seq + 1.U

      axi_txlast := false.B
      txcount := 1.U
    }.otherwise {
    }
  }

  when(txcount > 0.U && txcount < replicationCycles.U) {
    axi_txdata := TloePacketGenerator.toBigEndian(buf_vec(txcount - 1.U))
    txcount := txcount + 1.U
    axi_txvalid := true.B

    when (txcount === (replicationCycles-1).U) {
      axi_txlast := true.B
    }
  } .elsewhen (txcount === replicationCycles.U) {
    // Reset signals after transmission
    axi_txdata := 0.U
    axi_txvalid := false.B
    axi_txlast := false.B
    txcount := 0.U
  }

  // Connecting internal signals to output interface
  io.txvalid := axi_txvalid
  io.txdata := axi_txdata
  io.txlast := axi_txlast

  /////////////////////////////////////////////////

  /////////////////////////////////////////////////
  // Connect to Simulator with Endpoint module

  // Queue for outgoing TLoE packets
  // 16 entries of TloePacket type
  //val txQueue = Module(new Queue(UInt(640.W), 16))
  val txQueue = Module(new Queue(UInt(64.W), 16))
  
  // Queue for incoming TLoE packets
  // 16 entries of TloePacket type
  val rxQueue = Module(new Queue(UInt(640.W), 16))

  val bufVec     = RegInit(VecInit(Seq.fill(9)(0.U(64.W))))
  val txCount     = RegInit(0.U(log2Ceil(replicationCycles).W))

  val tmpP = RegInit(0.U(576.W))
  val tloePacket = RegInit(2.U(576.W))

  // Default values for transmission queue
  txQueue.io.enq.bits  := 0.U
  txQueue.io.enq.valid := false.B
  io.txReady           := false.B

  // Default values for reception queue
  rxQueue.io.enq.bits  := 0.U
  rxQueue.io.enq.valid := false.B
  io.rxData            := 0.U
  io.rxValid           := false.B

  val txAddrReg = RegNext(io.txAddr)

  // Enqueue a TLoE packet into the transmission queue when txValid is asserted
  when (io.txValid) {

    // Create a TLoE packet using input address, data, and opcode
    tloePacket := OXread.readPacket(txAddrReg, seq, seqAck)

    bufVec := VecInit(Seq.tabulate(9) (i => {
      //val packetWidth = OXread.createOXPacket(io.txAddr, seq, seqAck).getWidth
      val packetWidth = 576
      val high = packetWidth - (64 * i) - 1
      val low = math.max(packetWidth - 64 * (i + 1), 0)

      tloePacket (high, low)
    }))

    //tmpP := tloePacket
    txQueue.io.enq.bits := txAddrReg
    txCount := 1.U
}
/*
    val tloePacket = Cat(
      TloePacketGenerator.createTloePacket(io.txAddr, io.txData, io.txOpcode),
      0.U((640 - TloePacketGenerator.createTloePacket(io.txAddr, io.txData, io.txOpcode).getWidth).W)
    )

*/
    // Enqueue the TLoE packet into txQueue when txValid is high

    when(txCount > 0.U && txCount < replicationCycles.U) {
      txQueue.io.enq.bits := buf_vec(txCount - 1.U)
      txCount := txCount + 1.U
    } .elsewhen (txCount === replicationCycles.U) {
      txQueue.io.enq.valid := 1.U
      txCount := txCount + 1.U
    } .elsewhen (txCount === (replicationCycles + 1).U) {
      // Reset signals after transmission
      txCount := 0.U
    }
 
//    txQueue.io.enq.bits  := tloePacket
    txQueue.io.enq.valid := io.txValid
    io.txReady           := txQueue.io.enq.ready
//  }

  // Instantiate the Endpoint module
  val endpoint = Module(new Endpoint)

  // Connect txQueue to the Endpoint for transmission
  endpoint.io.txQueueData.bits  := txQueue.io.deq.bits
  endpoint.io.txQueueData.valid := txQueue.io.deq.valid
  txQueue.io.deq.ready          := endpoint.io.txQueueData.ready

  // Handle rxQueue and deserialize data
  endpoint.io.rxQueueData.ready := rxQueue.io.enq.ready

  // Enqueue received data into rxQueue when Endpoint valid signal is high
  when(endpoint.io.rxQueueData.valid) {
    rxQueue.io.enq.bits  := endpoint.io.rxQueueData.bits
    rxQueue.io.enq.valid := true.B
  }

  // Dequeue data from rxQueue and output when rxReady is high
  when(rxQueue.io.deq.valid && io.rxReady) {
    val rxPacket = rxQueue.io.deq.bits
    io.rxData            := rxPacket
    io.rxValid           := true.B
    rxQueue.io.deq.ready := io.rxReady
  }.otherwise {
    io.rxValid           := false.B
    rxQueue.io.deq.ready := false.B
  }
}
