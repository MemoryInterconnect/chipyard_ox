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
    val axi_rxdata  = Output(UInt(64.W))
    val axi_rxvalid = Output(Bool())
    val txdata      = Output(UInt(64.W))
    val txvalid     = Output(Bool())
    val txlast      = Output(Bool())
    val txkeep      = Output(UInt(8.W))
    val txready     = Input(Bool())
    val rxdata      = Input(UInt(64.W))
    val rxvalid     = Input(Bool())
    val rxlast      = Input(Bool())

    val ox_open     = Input(Bool())
    val ox_close    = Input(Bool())
    val debug1   = Input(Bool())
    val debug2   = Input(Bool())
  })

  val conn    = RegInit(0.U(8.W))

  val A_channel = RegInit(0.U(16.W))  // 0~65535
  val C_channel = RegInit(0.U(16.W))  // 0~65535
  val E_channel = RegInit(0.U(16.W))  // 0~65535

  // Configuration for packet replication cycles
  val replicationCycles = 10

  // TX AXI-Stream to Tilelink (Transmission Path)
  val axi_txdata  = RegInit(0.U(64.W))
  val axi_txvalid = RegInit(false.B)
  val axi_txlast  = RegInit(false.B)
  val axi_txkeep  = RegInit(0.U(8.W))

  /*
  rs_status :
    1. connection ready
    2. disconnection ready
  */
  val rx_status = RegInit(0.U(4.W))

  val seq     = RegInit(2.U(16.W))   // Sequence number (0~65535)
  val seqAck  = RegInit(3.U(16.W))   // Sequence number Ack (0~65535)
  val addr    = RegInit(0.U(64.W))

  val next_tx_seq = RegInit(0.U(22.W))
  val ackd_seq    = RegInit("h3FFFFF".U(22.W))
  val next_rx_seq = RegInit(0.U(22.W))

  val cPacket     = RegInit(0.U(576.W))

  // Registers to hold the AXI-Stream signals
  val axi_rxdata  = RegInit(0.U(64.W))
  val axi_rxvalid = RegInit(false.B)

  val rxcount     = RegInit(0.U(log2Ceil(replicationCycles).W))
  val rPacketVec  = RegInit(VecInit(Seq.fill(9)(0.U(64.W))))

  val txPacketVec = RegInit(VecInit(Seq.fill(9)(0.U(64.W))))
  val txPacketVecSize = RegInit(0.U(4.W))  // 0~15

  val rxPacketReceived = RegInit(false.B)

  //////////////////////////////////////////////////////////////////
  // State Machines
  val oidle :: opacket1_ready :: opacket1_sent :: opacket2_ready :: opacket2_sent :: owaiting_ack1 :: owaiting_ack2 :: osending_ack :: owaiting_ack3 :: Nil = Enum(9)
  val state = RegInit(oidle)

  val cidle :: cpacket_sent :: cwaiting_ack :: Nil = Enum(3)
  val cstate = RegInit(cidle)

  val ridle :: sendRequest :: waitResponse :: processResponse :: waitAck :: done :: Nil = Enum(6)
  val rstate = RegInit(ridle)

  val widle :: wsendRequest :: wwaitResponse :: wprocessResponse :: wwaitAck :: wdone :: Nil = Enum(6)
  val wstate = RegInit(ridle)

  val idx = RegInit(0.U(16.W))  // 패킷 인덱스 저장 레지스터

  //////////////////////////////////////////////////////////////////
  // Senging Packet
  val sendPacket = RegInit(false.B)
  val txComplete = RegInit(false.B)

  when (sendPacket) {
    when (idx < txPacketVecSize) {
      // 현재 인덱스에 해당하는 패킷을 axi_txdata에 저장
      axi_txdata := TloePacketGenerator.toBigEndian(txPacketVec(idx))
      axi_txvalid := true.B
      idx := idx + 1.U      // 다음 인덱스로 이동

      // 마지막 패킷인지 확인
      when (idx === (txPacketVecSize - 1.U)) {
        axi_txlast := true.B
        axi_txkeep := 0x3F.U  // 마지막 패킷 신호
        idx := 20.U
      } .otherwise {
        axi_txlast := false.B
        axi_txkeep := 0xFF.U  // 일반 패킷 신호
      }
    }.otherwise {
      axi_txdata := 0.U
      axi_txvalid := false.B
      axi_txlast := false.B
      axi_txkeep := 0.U

      idx := 0.U
      sendPacket := false.B
      txComplete := true.B  // 전송 완료 플래그 설정
    }
  }

  //////////////////////////////////////////////////////////////////
  // Connect
  when (io.ox_open) {
    when (state === oidle) {
      state := opacket1_ready
      cPacket := OXconnect.openConnection(next_tx_seq+1.U, 2.U, 9.U)  // Credit 9
    }
  }

  when (state === opacket1_ready) {
    txPacketVec := VecInit(Seq.tabulate(9) { i =>
      val packetWidth = 576
      val high = packetWidth - (64 * i) - 1
      val low = math.max(packetWidth - 64 * (i + 1), 0)
      cPacket(high, low)
    })
    next_tx_seq := next_tx_seq + 1.U

    txPacketVecSize := 9.U

    sendPacket := true.B
    txComplete := false.B
    state := opacket1_sent
  }

  when (state === opacket1_sent) {
    when (txComplete) {
      state := opacket2_ready
      cPacket := OXconnect.openConnection(next_tx_seq+1.U, 4.U, 9.U)  // Credit 9

      txComplete := false.B
    }
  }

  when (state === opacket2_ready) {
    txPacketVec := VecInit(Seq.tabulate(9) { i =>
      val packetWidth = 576
      val high = packetWidth - (64 * i) - 1
      val low = math.max(packetWidth - 64 * (i + 1), 0)
      cPacket(high, low)
    })
    next_tx_seq := next_tx_seq + 1.U

    txPacketVecSize := 9.U

    sendPacket := true.B
    txComplete := false.B
    state := opacket2_sent
  }

  when (state === opacket2_sent) {
    when (txComplete) {
      state := owaiting_ack1
      txComplete := false.B

      cstate := cready
    }
  }

  when (cstate === crunning) {
    // if normal packet
    switch((TloePacketGenerator.toBigEndian(rPacketVec(3)))(23, 21)) {
      is(1.U) {
        A_channel := (TloePacketGenerator.toBigEndian(rPacketVec(3)))(20, 16)
        next_rx_seq := next_rx_seq + 1.U // TODO from rx packet??
      }
      is(3.U) {
        C_channel := (TloePacketGenerator.toBigEndian(rPacketVec(3)))(20, 16) 
        next_rx_seq := next_rx_seq + 1.U // TODO from rx packet??
      }
      is(5.U) {
        E_channel := (TloePacketGenerator.toBigEndian(rPacketVec(3)))(20, 16) 
        next_rx_seq := next_rx_seq + 1.U // TODO from rx packet??
      }
    }

    // TODO A, C, E check??? 
    when (A_channel =/= 0.U && C_channel =/= 0.U && E_channel =/= 0.U) {

    }
  }

  // if Ackonly Packet
  val rx_seq_num = RegInit(1.U(2.W))

  when (state === owaiting_ack1) {
    //TODO handling recv packet
    when (rx_seq_num === 1.U) {
      state := owaiting_ack2
      rx_seq_num := 2.U
    }
  }

  when (state === owaiting_ack2) {
    //TODO handling recv packet
    when (rx_seq_num === 2.U) {
      state := osending_ack
      rx_seq_num := 3.U

      cPacket := OXconnect.normalAck(next_tx_seq+1.U, next_rx_seq, 1.U)  //TODO ack number
    }
  }

  when (state === osending_ack) {

    txPacketVec := VecInit(Seq.tabulate(9) { i =>
      val packetWidth = 576
      val high = packetWidth - (64 * i) - 1
      val low = math.max(packetWidth - 64 * (i + 1), 0)
      cPacket(high, low)
    })
    next_tx_seq := next_tx_seq + 1.U

    txPacketVecSize := 9.U

    sendPacket := true.B
    txComplete := false.B
    state := owaiting_ack3
  }

  when (state === owaiting_ack3 && rx_seq_num === 3.U) {
    //TODO handling recv packet
    when (txComplete && rx_seq_num === 3.U) {
      state := oidle
    }
  }

  //////////////////////////////////////////////////////////////////
  // Close Connection
  val cPacket     = RegInit(0.U(576.W))

  when (io.ox_close) {
    cstate := cpacket_sent 
    cPacket := OXconnect.closeConnection(next_tx_seq +1.U)
  }

  switch (cstate) {
    is (cpacket_sent) {
      txPacketVec := VecInit(Seq.tabulate(9) { i =>
        val packetWidth = 576
        val high = packetWidth - (64 * i) - 1
        val low = math.max(packetWidth - 64 * (i + 1), 0)
        cPacket(high, low)
      })
      next_tx_seq := next_tx_seq + 1.U

      txPacketVecSize := 9.U

      sendPacket := true.B
      txComplete := false.B
      cstate := cwaiting_ack
    }

    is (cwaiting_ack) {
      //TODO handling recv packet
      when (txComplete) {
        cstate := cidle
      }
    }
  }

  //////////////////////////////////////////////////////////////////
  // RX

  when (!io.rxvalid) {
    rxcount := 0.U
  }
 
  when (io.rxvalid) {
    rxcount := rxcount + 1.U

    rPacketVec(rxcount) := io.rxdata

    when (rxcount === 7.U){ // TODO: check 8, 9

      when (cstate === cready) {
        cstate := crunning
      }.elsewhen (rstate === waitResponse || rstate === waitAck) {
        rxPacketReceived := true.B
      }.elsewhen (wstate === waitResponse || wstate === waitAck) {
        rxPacketReceived := true.B
      }

      rxcount := 0.U
    }
  }
 

/*
  // Handling the valid and last signals in AXI-Stream
  // Reply for Open Connection
  when (io.rxvalid && rx_status === 1.U) {

    rxcount := rxcount + 1.U
    rPacketVec(rxcount) := io.rxdata

    when (rxcount === 7.U) { // TODO: check 8, 9
      val msg_type = (TloePacketGenerator.toBigEndian(rPacketVec(1)))(12, 9)

      when (msg_type === 0.U) {
        val chan_tmp = (TloePacketGenerator.toBigEndian(rPacketVec(3)))(23, 21)
        val credit_tmp = (TloePacketGenerator.toBigEndian(rPacketVec(3)))(20, 16)

        switch(chan_tmp) {
          is(1.U) {
            A_channel := credit_tmp
          }
          is(3.U) {
            C_channel := credit_tmp 
          }
          is(5.U) {
            E_channel := credit_tmp 
          }
        }      
        next_rx_seq := next_rx_seq + 1.U
      }.elsewhen (msg_type === 3.U) {
        //TODO
        val seq_ack = (TloePacketGenerator.toBigEndian(rPacketVec(2)))(47, 16)

        when (seq_ack === 2.U) {
          // Send Normal Packet

        }
      }
    }
*/

/*
    when (rxcount === 7.U) { // TODO: check 8, 9
      switch((TloePacketGenerator.toBigEndian(rPacketVec(3)))(23, 21)) {
        is(1.U) {
          A_channel := (TloePacketGenerator.toBigEndian(rPacketVec(3)))(20, 16)
        }
        is(3.U) {
          C_channel := (TloePacketGenerator.toBigEndian(rPacketVec(3)))(20, 16) 
        }
        is(5.U) {
          E_channel := (TloePacketGenerator.toBigEndian(rPacketVec(3)))(20, 16) 
        }
      }      
    }
*/
/*

    // Open Connection Done
    when (A_channel =/= 0.U && C_channel =/= 0.U && E_channel =/= 0.U) {

      //TODO
      axi_rxdata := Cat(
        (TloePacketGenerator.toBigEndian(rPacketVec(3)))(15, 0), 
        (TloePacketGenerator.toBigEndian(rPacketVec(4)))(63, 16)
      ) 
      axi_rxvalid := true.B

      rx_status := 0.U
    }
  }

  // Reply for Close Connection
  when (io.rxvalid && rx_status === 2.U) {
    rxcount := rxcount + 1.U

    rPacketVec(rxcount) := io.rxdata

    when (rxcount === 7.U){ // TODO: check 8, 9
      axi_rxdata := Cat(
        (TloePacketGenerator.toBigEndian(rPacketVec(3)))(15, 0), 
        (TloePacketGenerator.toBigEndian(rPacketVec(4)))(63, 16)
      ) 
      axi_rxvalid := true.B
    } .otherwise{
      axi_rxvalid := false.B
    }
  }

 //TODO NORMAL??
  when (io.rxvalid && rx_status === 0.U) {
    // Clear everything
    rxcount := rxcount + 1.U

    rPacketVec(rxcount) := io.rxdata

    when (rxcount === 7.U){ // TODO: check 8, 9
      axi_rxdata := Cat(
        (TloePacketGenerator.toBigEndian(rPacketVec(3)))(15, 0), 
        (TloePacketGenerator.toBigEndian(rPacketVec(4)))(63, 16)
      ) 
      axi_rxvalid := true.B
    } .otherwise{
      axi_rxvalid := false.B
    }
  }
*/

  // Connecting internal signals to output interface
  io.axi_rxdata  := axi_rxdata
  io.axi_rxvalid := axi_rxvalid

  val txcount     = RegInit(0.U(log2Ceil(replicationCycles).W))
  val tPacket     = Wire(UInt(576.W)) 
  val tPacketVec  = RegInit(VecInit(Seq.fill(9)(0.U(64.W))))

  tPacket := 0.U

  // TX AXI-Stream data/valid/last
  when (io.txValid) {

    when (io.txOpcode === 4.U) {		// READ
      //rstate := sendRequest
      tPacket := OXread.readPacket(io.txAddr, seq, seqAck)

      txPacketVec := VecInit(Seq.tabulate(9) (i => {
        val packetWidth = OXread.readPacket(io.txAddr, seq, seqAck).getWidth
        val high = packetWidth - (64 * i) - 1
	    val low = math.max(packetWidth - 64 * (i + 1), 0)

        tPacket (high, low)
      }))
      seq := seq + 1.U

      axi_txlast := false.B

      txPacketVecSize := 9.U
      sendPacket := true.B
      //txcount := 1.U
      //count := 1.U

      when (txComplete) {
        txComplete := false.B
      }
    }.elsewhen (io.txOpcode === 0.U) {	// WRITE
      wstate := wsendRequest
/*
      tPacket := OXread.writePacket(io.txAddr, io.txData, seq, seqAck)
      // tPacket := OXread.writePacket(io.txAddr, ox_cnt, seq, seqAck) // vio test

      tPacketVec := VecInit(Seq.tabulate(9) (i => {
        val packetWidth = OXread.readPacket(io.txAddr, seq, seqAck).getWidth
        val high = packetWidth - (64 * i) - 1
	    val low = math.max(packetWidth - 64 * (i + 1), 0)

        tPacket (high, low)
      }))
      seq := seq + 1.U

      axi_txlast := false.B
      txcount := 1.U
*/
    }.otherwise {
      //TODO
    }
  }

  when(txcount > 0.U && txcount < replicationCycles.U) {
    axi_txdata := TloePacketGenerator.toBigEndian(tPacketVec(txcount - 1.U))
    txcount := txcount + 1.U
    axi_txvalid := true.B

    when (txcount === (replicationCycles-1).U) {
      axi_txlast := true.B
      axi_txkeep := 0x3F.U
    } .otherwise {
      axi_txkeep := 0xFF.U
    }
  } .elsewhen (txcount === replicationCycles.U) {
    // Reset signals after transmission
    axi_txdata := 0.U
    axi_txvalid := false.B
    axi_txlast := false.B
    axi_txkeep := 0.U
    txcont := 0.U
  }

  // Connecting internal signals to output interface
  io.txvalid := axi_txvalid
  io.txdata := axi_txdata
  io.txlast := axi_txlast
  io.txkeep := axi_txkeep





  switch (rstate) {
    is (sendRequest) {
      // make packet
      tPacket := OXread.readPacket(io.txAddr, seq, seqAck)

      tPacketVec := VecInit(Seq.tabulate(9) (i => {
        val packetWidth = OXread.readPacket(io.txAddr, seq, seqAck).getWidth
        val high = packetWidth - (64 * i) - 1
	    val low = math.max(packetWidth - 64 * (i + 1), 0)

        tPacket (high, low)
      }))
      seq := seq + 1.U

      axi_txlast := false.B

      // send packet
      sendPacket := true.B

      // change state
      when (txComplete) {
        txComplete := false.B
        rstate := waitResponse
      }
    }

    is (waitResponse) {
      when (rxPacketReceived) {
        rstate := processResponse
        rxPacketReceived := false.B
      }
    }

    is (processResponse) {
      // return TL message

      // make packet

      // send packet

      rstate := waitAck
    }

    is (waitAck) {
      when (rxPacketReceived) {

        // check exptected Ack

        rstate := done
        rxPacketReceived := false.B
      }
    }

    is (done) {
      rstate := ridle
    }
  }


  switch (wstate) {
    is (wsendRequest) {
      // make packet
      tPacket := OXread.writePacket(io.txAddr, io.txData, seq, seqAck)

      txPacketVec := VecInit(Seq.tabulate(9) (i => {
        val packetWidth = OXread.readPacket(io.txAddr, seq, seqAck).getWidth
        val high = packetWidth - (64 * i) - 1
	    val low = math.max(packetWidth - 64 * (i + 1), 0)

        tPacket (high, low)
      }))
      seq := seq + 1.U

      axi_txlast := false.B

      // send packet
      txPacketVecSize := 9.U

      wstate := wwaitResponse
    }

    is (wwaitResponse) {

      sendPacket := true.B

      // change state
      when (txComplete) {
        wstate := wprocessResponse
        txComplete := false.B
      }
 
//      when (rxPacketReceived) {
//        rxPacketReceived := false.B
//      }
    }

    is (wprocessResponse) {
      // return TL message

      // make packet

      // send packet

      wstate := wwaitAck
    }

    is (wwaitAck) {
      when (rxPacketReceived) {

        // check exptected Ack

        wstate := wdone
        rxPacketReceived := false.B
      }
    }

    is (wdone) {
      wstate := ridle
    }
  }















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

  val tx_packet_vec = RegInit(VecInit(Seq.fill(9)(0.U(64.W))))
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

    tx_packet_vec := VecInit(Seq.tabulate(9) (i => {
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

  // Enqueue the TLoE packet into txQueue when txValid is high
  when(txCount > 0.U && txCount < replicationCycles.U) {
    txQueue.io.enq.bits := tPacketVec(txCount - 1.U)
    txCount := txCount + 1.U
  } .elsewhen (txCount === replicationCycles.U) {
    txQueue.io.enq.valid := 1.U
    txCount := txCount + 1.U
  } .elsewhen (txCount === (replicationCycles + 1).U) {
    // Reset signals after transmission
    txCount := 0.U
  }
 
  txQueue.io.enq.valid := io.txValid
  io.txReady           := txQueue.io.enq.ready

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
