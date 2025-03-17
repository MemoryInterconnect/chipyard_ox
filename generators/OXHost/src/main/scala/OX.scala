package omnixtend

import chisel3._
import chisel3.util._

import chisel3.experimental.{IntParam, BaseModule}
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.subsystem.BaseSubsystem
import freechips.rocketchip.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper.{HasRegMap, RegField}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.UIntIsOneOf

case class OXParams(
  address: BigInt = 0x1000,
  width: Int = 32,
  useAXI4: Boolean = false,
  useBlackBox: Boolean = true
)

case object OXKey extends Field[Option[OXParams]](None)

// Definition of OmniXtend Bundle
class OmniXtendBundle extends Bundle {
  val addr        = Input(UInt(64.W))
  val valid       = Input(Bool())   // signals if the transaction is valid
  val ready       = Output(Bool())  // signals if the transaction can proceed
  val in          = Input(UInt(8.W))

  // Connected to Ethernet IP
  val txdata      = Output(UInt(512.W))
  val txvalid     = Output(Bool())
  val txlast      = Output(Bool())
  val txkeep      = Output(UInt(8.W))
  val txready     = Input(Bool())
  val rxdata      = Input(UInt(512.W))
  val rxvalid     = Input(Bool())
  val rxlast      = Input(Bool())

  val ox_open     = Input(Bool())
  val ox_close    = Input(Bool())
  val debug1      = Input(Bool())
  val debug2      = Input(Bool())
}

/**
 * OmniXtendNode is a LazyModule that defines a TileLink manager node
 * which supports OmniXtend protocol operations. It handles Get and PutFullData
 * requests by interfacing with a Transceiver module.
 */
class OmniXtendNode(implicit p: Parameters) extends LazyModule {
  val beatBytes = 64 // The size of each data beat in bytes
  val node = TLManagerNode(Seq(TLSlavePortParameters.v1(Seq(TLSlaveParameters.v1(
    address            = Seq(AddressSet(0x500000000L, 0x01FFFFFFL)), // Address range this node responds to
    resources          = new SimpleDevice("mem", Seq("example,mem")).reg, // Device resources
    regionType         = RegionType.UNCACHED, // Memory region type
    executable         = true, // Memory is executable
    supportsGet        = TransferSizes(1, beatBytes), // Supported transfer sizes for Get operations
    supportsPutFull    = TransferSizes(1, beatBytes), // Supported transfer sizes for PutFull operations
    supportsPutPartial = TransferSizes(1, beatBytes), // Supported transfer sizes for PutPartial operations
    fifoId             = Some(0) // FIFO ID
  )),
    beatBytes          = 64, // Beat size for the port
    minLatency         = 1 // Minimum latency for the port
  )))

  lazy val module = new LazyModuleImp(this) {
    val io = IO(new OmniXtendBundle) // Input/Output bundle

    val (in, edge) = node.in(0) // Getting the input node and its edge

    // Registers for storing the validity of Get and PutFullData operations
    val getValidReg = RegNext(in.a.valid && in.a.bits.opcode === TLMessages.Get, init = false.B)
    val putValidReg = RegNext(in.a.valid && in.a.bits.opcode === TLMessages.PutFullData, init = false.B)
    val aValidReg   = RegInit(false.B) // Register to store the validity of any operation
    val opcodeReg   = RegInit(0.U(3.W)) // Register to store the opcode
    val sourceReg   = RegInit(0.U(4.W)) // Register to store the source ID
    val sizeReg     = RegInit(0.U(3.W)) // Register to store the size
    val paramReg    = RegInit(0.U(2.W)) // Register to store the parameter

    // Instantiate TLOEEndpoint
    val endpoint = Module(new TLOEEndpoint)

    // Create TxBundle for endpoint
    val txBundle = Wire(new endpoint.TxBundle)
    txBundle.addr := 0.U
    txBundle.data := 0.U
    txBundle.size := 0.U
    txBundle.opcode := 0.U

    // OX <-> TLOEEndpoint IO
    io.txdata := endpoint.io.txdata
    io.txvalid := endpoint.io.txvalid
    io.txlast := endpoint.io.txlast
    io.txkeep := endpoint.io.txkeep

    endpoint.io.txready := io.txready
    endpoint.io.rxdata := io.rxdata
    endpoint.io.rxvalid := io.rxvalid
    endpoint.io.rxlast := io.rxlast

    endpoint.io.ox_open := io.ox_open
    endpoint.io.ox_close := io.ox_close
    endpoint.io.debug1 := io.debug1
    endpoint.io.debug2 := io.debug2

    // Default values for the endpoint tx interface
    endpoint.io.tx.valid := false.B
    endpoint.io.tx.bits := txBundle
    endpoint.io.rx.ready := false.B

    // When the input channel 'a' is ready and valid
    when (in.a.fire()) {
      // Transmit the address, data, and opcode from the input channel
      txBundle.addr := in.a.bits.address
      txBundle.data := in.a.bits.data
      txBundle.size := in.a.bits.size
      txBundle.opcode := in.a.bits.opcode

      // Store the opcode, source, size, and parameter for response
      opcodeReg := Mux(in.a.bits.opcode === TLMessages.Get, TLMessages.AccessAckData, TLMessages.AccessAck)
      sourceReg := in.a.bits.source
      sizeReg   := in.a.bits.size
      paramReg  := in.a.bits.param

      endpoint.io.tx.valid := true.B // Mark the transmission as valid
    }

    endpoint.io.rx.ready := true.B // Always ready to receive data

    // Mark the input channel 'a' as valid
    when (in.a.valid) {
        aValidReg := true.B
    }

    // Default values for the response channel 'd'
    in.d.valid        := false.B
    in.d.bits.opcode  := 0.U
    in.d.bits.param   := 0.U
    in.d.bits.size    := 0.U
    in.d.bits.source  := 0.U
    in.d.bits.sink    := 0.U
    in.d.bits.denied  := false.B
    in.d.bits.data    := 0.U
    in.d.bits.corrupt := false.B

    // When received data is not zero, prepare the response
    when (endpoint.io.rx.valid) {                // RX valid signal received from Ethernet IP
        in.d.valid        := true.B                    // Mark the response as valid
        in.d.bits         := edge.AccessAck(in.a.bits) // Generate an AccessAck response
        in.d.bits.opcode  := opcodeReg                 // Set the opcode from the register
        in.d.bits.param   := paramReg                  // Set the parameter from the register
        in.d.bits.size    := sizeReg                   // Set the size from the register
        in.d.bits.source  := sourceReg                 // Set the source ID from the register
        in.d.bits.sink    := 0.U                       // Set sink to 0
        in.d.bits.denied  := false.B                   // Mark as not denied
 
      when (opcodeReg === TLMessages.AccessAckData) {
        switch (sizeReg) {
          is (1.U) { in.d.bits.data := endpoint.io.rx.bits.data(15, 0) } 
          is (2.U) { in.d.bits.data := endpoint.io.rx.bits.data(31, 0) }
          is (3.U) { in.d.bits.data := endpoint.io.rx.bits.data(63, 0) }
          // Remove larger sizes as they exceed the 64-bit data width
          is (4.U) { in.d.bits.data := endpoint.io.rx.bits.data }
          is (5.U) { in.d.bits.data := endpoint.io.rx.bits.data }
          is (6.U) { in.d.bits.data := endpoint.io.rx.bits.data }
        }
        in.d.bits.corrupt := false.B // Mark as not corrupt
      }.elsewhen (opcodeReg === TLMessages.AccessAck) {
        in.d.bits.data    := 0.U
      }
    }

    // Ready conditions for the input channel 'a' and response channel 'd'
    in.a.ready := in.a.valid || aValidReg
    in.d.ready := in.a.valid || aValidReg

    // IO ready signal is asserted when input is valid and opcode is Get or PutFullData
    io.ready := in.a.valid && (in.a.bits.opcode === TLMessages.Get || in.a.bits.opcode === TLMessages.PutFullData)
  }
}

// OmniXtend Trait
trait OmniXtend { this: BaseSubsystem =>
  private val portName = "OmniXtend"
  implicit val p: Parameters

  val ox = LazyModule(new OmniXtendNode()(p))

  mbus.coupleTo(portName) { (ox.node
    :*= TLBuffer()
    :*= TLWidthWidget(mbus.beatBytes)
    :*= _)
  }
}

// OmniXtendModuleImp Trait
trait OmniXtendModuleImp extends LazyModuleImp {
  val outer: OmniXtend
  implicit val p: Parameters

  val io = IO(new OmniXtendBundle)

  io <> outer.ox.module.io
}

class WithOX(useAXI4: Boolean = false, useBlackBox: Boolean = false) extends Config((site, here, up) => {
  case OXKey => Some(OXParams(useAXI4 = useAXI4, useBlackBox = useBlackBox))
})

