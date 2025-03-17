package omnixtend

import chisel3._
import chisel3.util._

/**
 * TLOEEndpoint manages the OmniXtend protocol endpoint.
 * It handles sequence management, queuing, and coordinates between TileLinkHandler and OmniXtend modules.
 */
class TLOEEndpoint extends Module {
  val io = IO(new Bundle {
    // Interface with TileLinkHandler
    val tx = Flipped(Decoupled(new TxBundle()))  // Interface for receiving transactions
    val rx = Decoupled(new RxBundle())          // Interface for sending responses

    // Ethernet interface
    val txdata   = Output(UInt(64.W))
    val txvalid  = Output(Bool())
    val txlast   = Output(Bool())
    val txkeep   = Output(UInt(8.W))
    val txready  = Input(Bool())
    val rxdata   = Input(UInt(64.W))
    val rxvalid  = Input(Bool())
    val rxlast   = Input(Bool())

    // Control signals
    val ox_open  = Input(Bool())
    val ox_close = Input(Bool())
    val debug1   = Input(Bool())
    val debug2   = Input(Bool())
    
    // Connection status
    val connected = Output(Bool())
  })

  // Transaction bundle definition
  class TxBundle extends Bundle {
    val addr   = UInt(64.W)
    val opcode = UInt(3.W)
    val size   = UInt(3.W)
    val data   = UInt(512.W)
  }

  // Reception bundle definition
  class RxBundle extends Bundle {
    val data   = UInt(64.W)
    val valid  = Bool()
  }

  // Internal queues for buffering
  val txQueue = Module(new Queue(new TxBundle, 16))  // Queue for outgoing transactions
  val rxQueue = Module(new Queue(new RxBundle, 16))  // Queue for incoming responses

  // Connect external interfaces to queues
  txQueue.io.enq <> io.tx
  io.rx <> rxQueue.io.deq

  // Sequence management registers
  val next_tx_seq = RegInit(0.U(22.W))
  val ackd_seq = RegInit("h3FFFFF".U(22.W))
  val next_rx_seq = RegInit(0.U(22.W))

  // Instantiate submodules
  val transmitter = Module(new TLOETransmitter)
  val receiver = Module(new TLOEReceiver)
  val connection = Module(new TLOEConnection)
  val flowControl = Module(new FlowControl)

  // Internal queue for Tx->Rx communication
  val ackQueue = Module(new Queue(new transmitter.AckBundle, 2))

  // Connect connection module
  connection.io.ox_open := io.ox_open
  connection.io.ox_close := io.ox_close
  connection.io.debug1 := io.debug1
  connection.io.debug2 := io.debug2
  connection.io.txready := io.txready
  connection.io.rxdata := io.rxdata
  connection.io.rxvalid := io.rxvalid
  connection.io.rxlast := io.rxlast
  io.connected := connection.io.connected

  // Connect flow control
  flowControl.io.set_credit := connection.io.set_credit
  flowControl.io.channel := connection.io.channel
  flowControl.io.credit := connection.io.credit

  // Connect sequence management
  connection.io.next_tx_seq := next_tx_seq
  connection.io.ackd_seq := ackd_seq
  connection.io.next_rx_seq := next_rx_seq

  // Mux between connection and transmitter outputs
  io.txdata := Mux(connection.io.txvalid,
                   connection.io.txdata,
                   transmitter.io.txdata)
  io.txvalid := connection.io.txvalid || transmitter.io.txvalid
  io.txlast := Mux(connection.io.txvalid,
                   connection.io.txlast,
                   transmitter.io.txlast)
  io.txkeep := Mux(connection.io.txvalid,
                   connection.io.txkeep,
                   transmitter.io.txkeep)

  // Connect transmitter to queues and external interface
  transmitter.io.txQueue <> txQueue.io.deq
  transmitter.io.txready := io.txready && !connection.io.txvalid

  // Connect receiver to queues and external interface
  receiver.io.rxdata := io.rxdata
  receiver.io.rxvalid := io.rxvalid && connection.io.connected
  receiver.io.rxlast := io.rxlast
  rxQueue.io.enq <> receiver.io.rxQueue

  // Connect control signals to transmitter
  transmitter.io.ox_open := connection.io.connected
  transmitter.io.ox_close := io.ox_close
  transmitter.io.debug1 := io.debug1
  transmitter.io.debug2 := io.debug2

  // Connect sequence management to transmitter
  transmitter.io.next_tx_seq := next_tx_seq
  transmitter.io.ackd_seq := ackd_seq
  transmitter.io.next_rx_seq := next_rx_seq

  // Connect sequence management to receiver
  receiver.io.next_tx_seq := next_tx_seq
  receiver.io.ackd_seq := ackd_seq
  receiver.io.next_rx_seq := next_rx_seq

  // Connect ackQueue between transmitter and receiver
  ackQueue.io.enq <> transmitter.io.ackQueue
  receiver.io.ackQueue <> ackQueue.io.deq

  // Update sequence registers based on connection, transmitter, and receiver updates
  when(connection.io.seq_update) {
    next_tx_seq := connection.io.next_tx_seq_out
    ackd_seq := connection.io.ackd_seq_out
    next_rx_seq := connection.io.next_rx_seq_out
  }.elsewhen(transmitter.io.seq_update) {
    next_tx_seq := transmitter.io.next_tx_seq_out
    ackd_seq := transmitter.io.ackd_seq_out
    next_rx_seq := transmitter.io.next_rx_seq_out
  }.elsewhen(receiver.io.seq_update) {
    next_tx_seq := receiver.io.next_tx_seq_out
    ackd_seq := receiver.io.ackd_seq_out
    next_rx_seq := receiver.io.next_rx_seq_out
  }
} 