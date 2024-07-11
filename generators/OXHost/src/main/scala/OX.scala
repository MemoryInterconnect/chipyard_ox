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
  useBlackBox: Boolean = true
)
// DOC include end: OX params

// DOC include start: OX key
case object OXKey extends Field[Option[OXParams]](None)
// DOC include end: OX key

// OmniXtend 번들 정의
class OmniXtendBundle extends Bundle {
  val addr = Input(UInt(64.W))
  val dataIn = Input(UInt(64.W))
  val dataOut = Output(UInt(64.W))
  val rw = Input(Bool()) // true for write, false for read
  val valid = Input(Bool()) // signals if the transaction is valid
  val ready = Output(Bool()) // signals if the transaction can proceed
  val analysisResult = Input(UInt(64.W)) // 분석 결과를 받기 위한 입력 신호
}

// OmniXtendNode 클래스 정의
class OmniXtendNode(implicit p: Parameters) extends LazyModule {
  val beatBytes = 64
  val node = TLManagerNode(Seq(TLSlavePortParameters.v1(Seq(TLSlaveParameters.v1(
    address = Seq(AddressSet(0x100000000L, 0x01FFFFFFFL)),
    resources = new SimpleDevice("omnixtend", Seq("example,omnixtend")).reg,
    regionType = RegionType.UNCACHED,
    executable = true,
    supportsGet = TransferSizes(1, beatBytes),
    supportsPutFull = TransferSizes(1, beatBytes),
    supportsPutPartial = TransferSizes(1, beatBytes),
    fifoId = Some(0) // FIFO ordering requirement
  )),
    beatBytes = 64,
    minLatency = 1
  )))

  lazy val module = new LazyModuleImp(this) {
    val io = IO(new OmniXtendBundle)

    val (in, edge) = node.in(0)

    // 상태 레지스터 정의
    val dataReg = RegInit(0.U(64.W))
    val addrReg = RegInit(0.U(64.W))
    val validReg = RegInit(false.B)
    val resultReg = RegInit(0.U(64.W))

    // 요청 레지스터를 사용하여 한 클럭 사이클 지연
    val getValidReg = RegNext(in.a.valid && in.a.bits.opcode === TLMessages.Get, init = false.B)
    val putValidReg = RegNext(in.a.valid && in.a.bits.opcode === TLMessages.PutFullData, init = false.B)
    val dataRegNext = RegNext(io.dataIn)
    val putDataReg = RegNext(in.a.bits.data)
    val sourceReg = RegNext(in.a.bits.source)
    val sizeReg = RegNext(in.a.bits.size)
    val paramReg = RegNext(in.a.bits.param)
    val addressReg = RegNext(in.a.bits.address)
    val opcodeReg = RegNext(in.a.bits.opcode)

    val transceiver = Module(new Transceiver)

    // 모든 transceiver 입출력 포트를 초기화
    transceiver.io.txAddr := 0.U
    transceiver.io.txData := 0.U
    transceiver.io.txOpcode := 0.U
    transceiver.io.txValid := false.B
    transceiver.io.rxReady := false.B

    // 요청 핸들링
    when (in.a.fire() && in.a.bits.opcode === TLMessages.Get) {
      transceiver.io.txAddr := in.a.bits.address
      transceiver.io.txData := in.a.bits.data
      transceiver.io.txOpcode := in.a.bits.opcode
      transceiver.io.txValid := true.B
    }.elsewhen(in.a.fire() && in.a.bits.opcode === TLMessages.PutFullData) {
      transceiver.io.txAddr := in.a.bits.address
      transceiver.io.txData := in.a.bits.data
      transceiver.io.txOpcode := in.a.bits.opcode
      transceiver.io.txValid := true.B
    }

    // 분석된 결과를 받기 위해 rxReady 설정
    transceiver.io.rxReady := true.B

    // TileLink 채널 'd'로 응답 생성 (한 클럭 사이클 뒤에)
    in.d.valid := getValidReg || putValidReg
    in.a.ready := !getValidReg && !putValidReg

    in.d.bits := edge.AccessAck(in.a.bits)
    in.d.bits.opcode := Mux(getValidReg, TLMessages.AccessAckData, TLMessages.AccessAck)
    in.d.bits.param := paramReg
    in.d.bits.size := sizeReg
    in.d.bits.source := sourceReg
    in.d.bits.sink := 0.U // 사용할 필요가 없는 경우 0으로 설정
    in.d.bits.denied := false.B // 예제에서는 데이터가 항상 유효하다고 가정
    in.d.bits.data := transceiver.io.analysisResult
    in.d.bits.corrupt := false.B // 예제에서는 데이터가 항상 유효하다고 가정

    // OmniXtend 인터페이스 설정
    io.ready := in.a.valid && (in.a.bits.opcode === TLMessages.Get || in.a.bits.opcode === TLMessages.PutFullData)
  }
}

// OmniXtend Trait 정의
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

// OmniXtendModuleImp Trait 정의
trait OmniXtendModuleImp extends LazyModuleImp {
  val outer: OmniXtend
  implicit val p: Parameters

  val io = IO(new OmniXtendBundle)

  io <> outer.ox.module.io
}

// DOC include start: OX config fragment
class WithOX(useAXI4: Boolean = false, useBlackBox: Boolean = false) extends Config((site, here, up) => {
  case OXKey => Some(OXParams(useAXI4 = useAXI4, useBlackBox = useBlackBox))
})
// DOC include end: OX config fragment

