package omnixtendagentetri

import chisel3._
import chisel3.util._
import chisel3.experimental.{IntParam, BaseModule}
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.subsystem.BaseSubsystem
//import org.chipsalliance.cde.config.{Parameters, Field, Config} // chipyard 1.10.1
import freechips.rocketchip.config.{Parameters, Field, Config} // chipyard 1.8.1
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper.{HasRegMap, RegField}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.UIntIsOneOf
import freechips.rocketchip.util.{UIntToAugmentedUInt, ElaborationArtefacts}
//import testchipip.TLHelper
import chipyard.iobinders._

// DOC include start: OMNI conf
case class OMNIconf(
  address: BigInt = 0x1000,
  width: Int = 32,
  MAC_WIDTH: Int = 48,
  //DEST_ADDR: BigInt = 0x001232FFFF18L,
  //SRC_ADDR: BigInt = 0xFF1232FFFF18L,
  mbus_sel: Boolean = false,
  MBUS_SEL: Int = 0,
  TL_AW: Int = 64,
  TL_DW: Int  = 512,
  TL_AIW: Int = 26,
  TL_DIW: Int = 26,
  TL_DBW: Int = 64, //(TL_DW >> 3),
  TL_SZW: Int = 4   //log2Ceil(TL_DBW)
)
// DOC include end: OMNI conf

// DOC include start: OMNI key
case object OMNIKey extends Field[Option[OMNIconf]](None)
// DOC include end: OMNI key

class TL_I(val conf: OMNIconf) extends Bundle {
  val a_valid   = Input(Bool())
  val a_opcode  = Input(UInt(3.W))
  val a_param   = Input(UInt(4.W))
  val a_size    = Input(UInt(conf.TL_SZW.W))
  val a_source  = Input(UInt(conf.TL_AIW.W))
  val a_address = Input(UInt(conf.TL_AW.W))
  val a_mask    = Input(UInt(conf.TL_DBW.W))
  val a_data    = Input(UInt(conf.TL_DW.W))

  val c_valid   = Input(Bool())
  val c_opcode  = Input(UInt(3.W))
  val c_param   = Input(UInt(4.W))
  val c_size    = Input(UInt(conf.TL_SZW.W))
  val c_source  = Input(UInt(conf.TL_AIW.W))
  val c_address = Input(UInt(conf.TL_AW.W))
  val c_data    = Input(UInt(conf.TL_DW.W))

  val e_valid   = Input(Bool())
  val e_sink    = Input(Bool())

  val b_ready   = Input(Bool())
  val d_ready   = Input(Bool())
}

class TL_O(val conf: OMNIconf) extends Bundle {
  val b_valid   = Output(Bool())
  val b_opcode  = Output(UInt(3.W))
  val b_param   = Output(UInt(4.W))
  val b_size    = Output(UInt(conf.TL_SZW.W))
  val b_source  = Output(UInt(conf.TL_AIW.W))
  val b_address = Output(UInt(conf.TL_AW.W))
  val b_mask    = Output(UInt(conf.TL_DBW.W))
  val b_data    = Output(UInt(conf.TL_DW.W))

  val d_valid   = Output(Bool())
  val d_opcode  = Output(UInt(3.W))
  val d_param   = Output(UInt(4.W))
  val d_size    = Output(UInt(conf.TL_SZW.W))
  val d_source  = Output(UInt(conf.TL_AIW.W))
  val d_sink    = Output(UInt(conf.TL_DIW.W))
  val d_data    = Output(UInt(conf.TL_DW.W))
  val d_denied  = Output(Bool())
  val d_corrupt = Output(Bool())

  val a_ready   = Output(Bool())
  val c_ready   = Output(Bool())
  val e_ready   = Output(Bool())
}

//=================================================

class OMNIIO(val conf : OMNIconf) extends Bundle{
  val clock = Input(Clock())
  val reset = Input(Bool())
  val tl_i = new TL_I(conf)
  val tl_o = new TL_O(conf)
  val GPIO_SW_N   = Input(Bool())
  val GPIO_SW_S   = Input(Bool())
  val GPIO_SW_E   = Input(Bool())
  val GPIO_SW_W   = Input(Bool())
  val GPIO_SW_C   = Input(Bool())
//  val GPIO_SW12_1 = Input(Bool())
  val gt_rxp_in = Input(UInt(1.W))
  val gt_rxn_in = Input(UInt(1.W))
  val gt_txp_out = Output(UInt(1.W))
  val gt_txn_out = Output(UInt(1.W))
  val mclk = Input(Clock())
  val gt_refclk_p = Input(Clock())
  val gt_refclk_n = Input(Clock())
//  val GPIO_LED0 = Output(UInt(1.W))
//  val GPIO_LED7 = Output(UInt(1.W))
}

class OMNIIOTop extends Bundle{
  val GPIO_SW_N   = Input(Bool())
  val GPIO_SW_S   = Input(Bool())
  val GPIO_SW_E   = Input(Bool())
  val GPIO_SW_W   = Input(Bool())
  val GPIO_SW_C   = Input(Bool())
//  val GPIO_SW12_1 = Input(Bool())
  val gt_rxp_in = Input(UInt(1.W))
  val gt_rxn_in = Input(UInt(1.W))
  val gt_txp_out = Output(UInt(1.W))
  val gt_txn_out = Output(UInt(1.W))
  val mclk = Input(Clock())
  val gt_refclk_p = Input(Clock())
  val gt_refclk_n = Input(Clock())
//  val GPIO_LED0 = Output(UInt(1.W))
//  val GPIO_LED7 = Output(UInt(1.W))
}


trait HasOMNIIO extends BaseModule {
  val conf : OMNIconf
  val io = IO(new OMNIIO(conf))
}

class omnisimpleBlackBox(val conf: OMNIconf)(implicit p : Parameters) extends BlackBox(
  Map (
    //"MBUS_SEL" -> IntParam(conf.mbus_sel),
    "MBUS_SEL"  -> IntParam(conf.MBUS_SEL),
//    "DEST_ADDR" -> IntParam(conf.DEST_ADDR),
 //   "SRC_ADDR"  -> IntParam(conf.SRC_ADDR),
    "TL_AW"     -> IntParam(conf.TL_AW),
    "TL_DW"     -> IntParam(conf.TL_DW),
    "TL_AIW"    -> IntParam(conf.TL_AIW),
    "TL_DIW"    -> IntParam(conf.TL_DIW),
    "TL_DBW"    -> IntParam(conf.TL_DBW),
    "TL_SZW"    -> IntParam(conf.TL_SZW)
  )
) 
with HasBlackBoxResource with HasOMNIIO 
{
//  addResource("vsrc/omnisimpleBlackBox.sv") // Original Code
//  addResource("vsrc/omnisimpleBlackBox_GPIO_SW12_1.sv") // For control muxing data of D channel
  addResource("vsrc/omnisimpleBlackBox_GPIO_SW12_verilator.sv") // For control muxing data of D channel
  println("[USER]BlackBox Included ")
}

class MyManager(val conf : OMNIconf)(implicit p : Parameters) extends LazyModule {
 val device = new SimpleDevice("my-ETRI", Seq("OMNIXTEND_ETRI, my-ETRI"))
 val logDevice = new SimpleDevice("LogRegisterNode", Seq("OMNIXTEND_ETRI, my-ETRI"))
 val regDevice = new SimpleDevice("RegisterNode", Seq("OMNIXTEND_ETRI, my-ETRI"))
 val beatBytes = 64 
 //val beatBytes = conf.TL_DW >> 3

 val node = TLManagerNode(Seq(TLSlavePortParameters.v1(Seq(TLManagerParameters(
    address = Seq(AddressSet(0x100000000L, 0x01FFFFFFFL)), // (Base, Range), that is, 0x1_0000_0000 ~ 0x1_1FFF_FFFF
    //address = Seq(AddressSet(0x800000, 0x0FFFFF)), // (Base, Range), that is, 0x800000 ~ 0x8FFFFF
    //address = Seq(AddressSet(0x400000000L, 0x1FFFFFFFL)), // (Base, Range), that is, 0x4_0000_0000 ~ 0x4_1FFF_FFFF
    //address = Seq(AddressSet(0x80000000L, 0x01FFFFFFL)), // (Base, Range), that is, 0x80000000 ~ 0x8FFFFFFF
    resources = device.reg,
    regionType = RegionType.UNCACHED,
    //regionType = RegionType.TRACKED,
    executable = true,
    supportsArithmetic = TransferSizes(1, beatBytes),
    supportsLogical = TransferSizes(1, beatBytes),
    supportsGet = TransferSizes(1, beatBytes),
    supportsPutFull = TransferSizes(1, beatBytes),
    supportsPutPartial = TransferSizes(1, beatBytes),
    supportsHint = TransferSizes(1, beatBytes),
    fifoId = Some(0))), beatBytes)))

// Register Router for Memory Access Logging Information
  val LoggingNode = TLRegisterNode(
    address = Seq(AddressSet(0x9026000, 0xfff)),
    device = logDevice,
    beatBytes = 64,
    concurrency = 1 )

  println("[USER]My Manager !!!!!!!")

  //Function of Rising Edge in Signal
  def risingedge(x : Bool) = x && !RegNext(x)


  lazy val module = new MyManagerModuleImp(conf,this)(p)
}


class MyManagerModuleImp(var conf:OMNIconf, outer : MyManager)(implicit p : Parameters) extends LazyModuleImp(outer){
  println("[USER]My Manager Module Implementation")  
  val io = IO(new Bundle {          
    val blackboxmode = new Bundle{  
      val clock = Input(Clock())
      val reset = Input(Bool())
      val tl_i = new TL_I(conf)
      val tl_o = new TL_O(conf)
      val GPIO_SW_N   = Input(Bool())
      val GPIO_SW_S   = Input(Bool())
      val GPIO_SW_E   = Input(Bool())
      val GPIO_SW_W   = Input(Bool())
      val GPIO_SW_C   = Input(Bool())
//      val GPIO_SW12_1   = Input(Bool())
      val gt_rxp_in = Input(UInt(1.W))
      val gt_rxn_in = Input(UInt(1.W))
      val gt_txp_out = Output(UInt(1.W))
      val gt_txn_out = Output(UInt(1.W))
      val mclk = Input(Clock())
      val gt_refclk_p = Input(Clock())
      val gt_refclk_n = Input(Clock())
      val GPIO_LDE0 = Output(UInt(1.W))
      val GPIO_LDE7 = Output(UInt(1.W))
    } 
  })

  // TileLink Connection Create

  val (tl, edge) = outer.node.in(0)
  //val eth_param : XXVEthernetParams
  
  //===================================================================// 
  // Module Implementation 
  //===================================================================// 
  val impl = Module(new omnisimpleBlackBox(conf)(p))
  //val impl = Module(new omnisimpleBlackBox(conf)(p))
  
//  val impl_eth = Module(new ethernetBlackBox)
//  val eth = Module(new XXVEthernetBlackBox(eth_param.speed := 10))


  //===================================================================// 
  // Registers of LoggingNode
  //===================================================================// 
      val addrBits = conf.TL_AW                 // Register Width
      val cache_Hit = RegInit(0.U(addrBits.W))  // Cache Hit Counter
      val mem_Access = RegInit(0.U(addrBits.W)) // Memory Access Counter
      val cache_Miss = RegInit(0.U(addrBits.W)) // Cache Miss Counter


      cache_Hit := 33.U
  //===================================================================// 
  // Register Map of LoggingNode
  //===================================================================// 
      outer.LoggingNode.regmap(
        0x00 -> Seq(RegField(addrBits, cache_Hit)),
        0x08 -> Seq(RegField(addrBits, mem_Access)),
        0x10 -> Seq(RegField(addrBits, cache_Miss))
      )
      // Accumulate the Cache Hit Event
//      when(tl.a.valid && tl.a.bits.opcode == 0)
      cache_Hit := outer.risingedge(tl.a.valid) + cache_Hit



  // IO Connection between blackbox and TileLink(Channel A, B, C, D, E)
  // blackbox <-> TL
  impl.io.clock := clock
  impl.io.reset := ~reset.asBool

  // A Channel Signals
  impl.io.tl_i.a_valid   := tl.a.valid  
  impl.io.tl_i.a_opcode  := tl.a.bits.opcode 
  impl.io.tl_i.a_param   := tl.a.bits.param  
  impl.io.tl_i.a_size    := tl.a.bits.size   
  impl.io.tl_i.a_source  := tl.a.bits.source 
  impl.io.tl_i.a_address := tl.a.bits.address
  impl.io.tl_i.a_mask    := tl.a.bits.mask   
  impl.io.tl_i.a_data    := tl.a.bits.data   

  // B Channel Signals
  tl.b.valid             := impl.io.tl_o.b_valid 
  tl.b.bits.opcode       := impl.io.tl_o.b_opcode
  tl.b.bits.param        := impl.io.tl_o.b_param
  tl.b.bits.size         := impl.io.tl_o.b_size
  tl.b.bits.source       := impl.io.tl_o.b_source
  tl.b.bits.address      := impl.io.tl_o.b_address
  tl.b.bits.mask         := impl.io.tl_o.b_mask
  tl.b.bits.data         := impl.io.tl_o.b_data

  // C Channel Signals
  impl.io.tl_i.c_valid   := tl.c.valid  
  impl.io.tl_i.c_opcode  := tl.c.bits.opcode 
  impl.io.tl_i.c_param   := tl.c.bits.param  
  impl.io.tl_i.c_size    := tl.c.bits.size   
  impl.io.tl_i.c_source  := tl.c.bits.source 
  impl.io.tl_i.c_address := tl.c.bits.address
  impl.io.tl_i.c_data    := tl.c.bits.data   

  // D Channel Signals
  tl.d.valid             := impl.io.tl_o.d_valid 
  tl.d.bits.opcode       := impl.io.tl_o.d_opcode
  tl.d.bits.param        := impl.io.tl_o.d_param
  tl.d.bits.size         := impl.io.tl_o.d_size
  tl.d.bits.source       := impl.io.tl_o.d_source
  tl.d.bits.sink         := impl.io.tl_o.d_sink
  tl.d.bits.data         := impl.io.tl_o.d_data
  tl.d.bits.denied       := impl.io.tl_o.d_denied
                         
  // E Channel Signals
  impl.io.tl_i.e_valid   := tl.e.valid  
  impl.io.tl_i.e_sink    := tl.e.bits.sink 

  // Ready Signals
  tl.a.ready             := impl.io.tl_o.a_ready
  impl.io.tl_i.d_ready   := tl.d.ready
  tl.c.ready             := impl.io.tl_o.c_ready
  impl.io.tl_i.b_ready   := tl.b.ready
  tl.e.ready             := impl.io.tl_o.e_ready

  // IO Connection between MyManager(blackboxmode) and blackbox
  // blackboxmode <-> blackbox
/*  io.blackboxmode.gt_txp_out := impl_eth.io.gt_txp_out
  io.blackboxmode.gt_txn_out := impl_eth.io.gt_txn_out
  impl_eth.io.gt_rxp_in          := io.blackboxmode.gt_rxp_in 
  impl_eth.io.gt_rxn_in          := io.blackboxmode.gt_rxn_in 
  impl_eth.io.mclk               := io.blackboxmode.mclk
  impl_eth.io.gt_refclk_p        := io.blackboxmode.gt_refclk_p
  impl_eth.io.gt_refclk_n        := io.blackboxmode.gt_refclk_n
  */
  io.blackboxmode.gt_txp_out := impl.io.gt_txp_out
  io.blackboxmode.gt_txn_out := impl.io.gt_txn_out
  impl.io.gt_rxp_in          := io.blackboxmode.gt_rxp_in 
  impl.io.gt_rxn_in          := io.blackboxmode.gt_rxn_in 
  impl.io.mclk               := io.blackboxmode.mclk
  impl.io.gt_refclk_p        := io.blackboxmode.gt_refclk_p
  impl.io.gt_refclk_n        := io.blackboxmode.gt_refclk_n

  impl.io.GPIO_SW_N          := io.blackboxmode.GPIO_SW_N
  impl.io.GPIO_SW_S          := io.blackboxmode.GPIO_SW_S
  impl.io.GPIO_SW_E          := io.blackboxmode.GPIO_SW_E
  impl.io.GPIO_SW_W          := io.blackboxmode.GPIO_SW_W
  impl.io.GPIO_SW_C          := io.blackboxmode.GPIO_SW_C
//  impl.io.GPIO_SW12_1        := io.blackboxmode.GPIO_SW12_1
//  impl.io.GPIO_LED0          := io.blackboxmode.GPIO_LED0
//  impl.io.GPIO_LED7          := io.blackboxmode.GPIO_LED7
}


trait CanHaveOmniXtendAgentTile { this : BaseSubsystem =>
  private val portName = "omni"
  implicit val p : Parameters
  val conf = OMNIconf(TL_DW = p(OMNIKey).get.TL_DW, MBUS_SEL = p(OMNIKey).get.MBUS_SEL)
  val mbus_sel :Boolean = p(OMNIKey).get.mbus_sel
  val omnimodule = LazyModule(new MyManager(conf)(p))

  if(mbus_sel) {
    println("[USER]Can Have OmniXtendAgent Tile, and Connect MyManager to MemoryBus(MBUS)")
    mbus.coupleTo(s"ETRI-OMNIXTENDAGENT-MANAGER_$portName"){
     (omnimodule.node
       :*= TLBuffer()
  //     :*= TLSourceShrinker(1 << idBits)
       :*= TLWidthWidget(mbus.beatBytes)
       :*= _)
    }
  } else {
    println("[USER]Can Have OmniXtendAgent Tile, and Connect MyManager to SystemBus(SBUS)")
    sbus.coupleTo(s"ETRI-OMNIXTENDAGENT-MANAGER_$portName"){
     (omnimodule.node
       :*= TLBuffer()
  //     :*= TLSourceShrinker(1 << idBits)
       :*= TLWidthWidget(sbus.beatBytes)
       :*= _)
    }
  }

  sbus.coupleTo(s"ETRI-OMNIXTENDAGENT-LOGGING_$portName"){
   (omnimodule.LoggingNode
     :*= TLBuffer()
     //:*= TLWidthWidget(mbus.beatBytes)
     :*= TLWidthWidget(8) //16
     :*= _)
  }
}

trait HasOMNIImp extends LazyModuleImp {
  implicit val p : Parameters
  val outer : CanHaveOmniXtendAgentTile
  val omnixtendIO = IO(new OMNIIOTop)

  // IO Connection between ChipTop and MyManager(blackboxmode)
  // ChipTop <-> blackboxmode
//  outer.omnimodule.module.io.blackboxmode.clock       := omnixtendIO.clock
//  outer.omnimodule.module.io.blackboxmode.reset       := omnixtendIO.reset
  omnixtendIO.gt_txp_out                              := outer.omnimodule.module.io.blackboxmode.gt_txp_out
  omnixtendIO.gt_txn_out                              := outer.omnimodule.module.io.blackboxmode.gt_txn_out
  outer.omnimodule.module.io.blackboxmode.gt_rxp_in   := omnixtendIO.gt_rxp_in
  outer.omnimodule.module.io.blackboxmode.gt_rxn_in   := omnixtendIO.gt_rxn_in
  outer.omnimodule.module.io.blackboxmode.mclk        := omnixtendIO.mclk
  outer.omnimodule.module.io.blackboxmode.gt_refclk_p := omnixtendIO.gt_refclk_p
  outer.omnimodule.module.io.blackboxmode.GPIO_SW_N   := omnixtendIO.GPIO_SW_N
  outer.omnimodule.module.io.blackboxmode.GPIO_SW_S   := omnixtendIO.GPIO_SW_S
  outer.omnimodule.module.io.blackboxmode.GPIO_SW_E   := omnixtendIO.GPIO_SW_E
  outer.omnimodule.module.io.blackboxmode.GPIO_SW_W   := omnixtendIO.GPIO_SW_W
  outer.omnimodule.module.io.blackboxmode.GPIO_SW_C   := omnixtendIO.GPIO_SW_C
//  outer.omnimodule.module.io.blackboxmode.GPIO_SW12_1 := omnixtendIO.GPIO_SW12_1
  outer.omnimodule.module.io.blackboxmode.gt_refclk_n := omnixtendIO.gt_refclk_n
//  outer.omnimodule.module.io.blackboxmode.GPIO_LED0   := omnixtendIO.GPIO_LED0
 // outer.omnimodule.module.io.blackboxmode.GPIO_LED7   := omnixtendIO.GPIO_LED7

  // If Some pins are only shown on ChipTop, do like this.
  // And comment ChipTop <-> blackboxmode lines.
  //  omnixtendIO.gt_txp_out := DontCare
  //  omnixtendIO.gt_txn_out := DontCare
}

// DOC include start: OMNI OMNIParamsig fragment
class WithOMNI(tl_dw: Int = 32, mbus_sel: Boolean = false) extends Config((site, here, up) => {
  //case OMNIKey => Some(OMNIconf(width = width))
  if(mbus_sel) {
    case OMNIKey => Some(OMNIconf(TL_DW = tl_dw, MBUS_SEL = 1, mbus_sel=mbus_sel))
  }else{
    case OMNIKey => Some(OMNIconf(TL_DW = tl_dw, MBUS_SEL = 0, mbus_sel=mbus_sel))
  }
})
// DOC include end: OMNI OMNIParamsig fragment
