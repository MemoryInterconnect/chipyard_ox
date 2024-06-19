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

// DOC include start: OX params
case class OXParams(
  address: BigInt = 0x1000,
  width: Int = 32,
  useAXI4: Boolean = false,
  useBlackBox: Boolean = true)
// DOC include end: OX params

// DOC include start: OX key
case object OXKey extends Field[Option[OXParams]](None)
// DOC include end: OX key

class OXIO(val w: Int) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Bool())
  val input_ready = Output(Bool())
  val input_valid = Input(Bool())
  val x = Input(UInt(w.W))
  val y = Input(UInt(w.W))
  val output_ready = Input(Bool())
  val output_valid = Output(Bool())
  val ox = Output(UInt(w.W))
  val busy = Output(Bool())
}

trait OXTopIO extends Bundle {
  val ox_busy = Output(Bool())
}

trait HasOXIO extends BaseModule {
  val w: Int
  val io = IO(new OXIO(w))
}

/*
// DOC include start: OX blackbox
class OXMMIOBlackBox(val w: Int) extends BlackBox(Map("WIDTH" -> IntParam(w))) with HasBlackBoxResource
  with HasOXIO
{
  addResource("/vsrc/OXMMIOBlackBox.v")
}
// DOC include end: OX blackbox
*/

// DOC include start: OX chisel
class OXMMIOChiselModule(val w: Int) extends Module
  with HasOXIO
{
  val s_idle :: s_run :: s_done :: Nil = Enum(3)

  val state = RegInit(s_idle)
  val tmp   = Reg(UInt(w.W))
  val ox   = Reg(UInt(w.W))

  io.input_ready := state === s_idle
  io.output_valid := state === s_done
  io.ox := ox

  when (state === s_idle && io.input_valid) {
    state := s_run
  } .elsewhen (state === s_run && tmp === 0.U) {
    state := s_done
  } .elsewhen (state === s_done && io.output_ready) {
    state := s_idle
  }

  when (state === s_idle && io.input_valid) {
    ox := io.x
    tmp := io.y
  } .elsewhen (state === s_run) {
    when (ox > tmp) {
      ox := ox - tmp
    } .otherwise {
      tmp := tmp - ox
    }
  }

  io.busy := state =/= s_idle

  println("SCALA OX CODE IN HERE");

}
// DOC include end: OX chisel

// DOC include start: OX instance regmap

trait OXModule extends HasRegMap {
  val io: OXTopIO

  implicit val p: Parameters
  def params: OXParams
  val clock: Clock
  val reset: Reset


  // How many clock cycles in a PWM cycle?
  val x = Reg(UInt(params.width.W))
  val y = Wire(new DecoupledIO(UInt(params.width.W)))
  val ox = Wire(new DecoupledIO(UInt(params.width.W)))
  val status = Wire(UInt(2.W))

  val impl = if (params.useBlackBox) {
//    Module(new OXMMIOBlackBox(params.width))
    Module(new OXMMIOChiselModule(params.width))
  } else {
    Module(new OXMMIOChiselModule(params.width))
  }

  impl.io.clock := clock
  impl.io.reset := reset.asBool

  impl.io.x := x
  impl.io.y := y.bits
  impl.io.input_valid := y.valid
  y.ready := impl.io.input_ready

  ox.bits := impl.io.ox
  ox.valid := impl.io.output_valid
  impl.io.output_ready := ox.ready

  status := Cat(impl.io.input_ready, impl.io.output_valid)
  io.ox_busy := impl.io.busy

  regmap(
    0x00 -> Seq(
      RegField.r(2, status)), // a read-only register capturing current status
    0x04 -> Seq(
      RegField.w(params.width, x)), // a plain, write-only register
    0x08 -> Seq(
      RegField.w(params.width, y)), // write-only, y.valid is set on write
    0x0C -> Seq(
      RegField.r(params.width, ox))) // read-only, ox.ready is set on read

  println("OXModule");
}
// DOC include end: OX instance regmap

// DOC include start: OX router
class OXTL(params: OXParams, beatBytes: Int)(implicit p: Parameters)
  extends TLRegisterRouter(
    params.address, "ox", Seq("ucbbar,ox"),
    beatBytes = beatBytes)(
      new TLRegBundle(params, _) with OXTopIO)(
      new TLRegModule(params, _, _) with OXModule)

class OXAXI4(params: OXParams, beatBytes: Int)(implicit p: Parameters)
  extends AXI4RegisterRouter(
    params.address,
    beatBytes=beatBytes)(
      new AXI4RegBundle(params, _) with OXTopIO)(
      new AXI4RegModule(params, _, _) with OXModule)
// DOC include end: OX router

// DOC include start: OX lazy trait
trait CanHaveOX { this: BaseSubsystem =>
  private val portName = "ox"

  // Only build if we are using the TL (nonAXI4) version
  val ox = p(OXKey) match {
    case Some(params) => {
      if (params.useAXI4) {
        val ox = LazyModule(new OXAXI4(params, pbus.beatBytes)(p))
        pbus.toSlave(Some(portName)) {
          ox.node :=
          AXI4Buffer () :=
          TLToAXI4 () :=
          // toVariableWidthSlave doesn't use holdFirstDeny, which TLToAXI4() needsx
          TLFragmenter(pbus.beatBytes, pbus.blockBytes, holdFirstDeny = true)
        }
        Some(ox)
      } else {
        val ox = LazyModule(new OXTL(params, pbus.beatBytes)(p))
        pbus.toVariableWidthSlave(Some(portName)) { ox.node }
        Some(ox)
      }
    }
    case None => None
  }
}
// DOC include end: OX lazy trait

// DOC include start: OX imp trait
trait CanHaveOXModuleImp extends LazyModuleImp {
  val outer: CanHaveOX
  val ox_busy = outer.ox match {
    case Some(ox) => {
      val busy = IO(Output(Bool()))
      busy := ox.module.io.ox_busy
      Some(busy)
    }
    case None => None
  }
}

// DOC include end: OX imp trait


// DOC include start: OX config fragment
class WithOX(useAXI4: Boolean = false, useBlackBox: Boolean = false) extends Config((site, here, up) => {
  case OXKey => Some(OXParams(useAXI4 = useAXI4, useBlackBox = useBlackBox))
})
// DOC include end: OX config fragment
