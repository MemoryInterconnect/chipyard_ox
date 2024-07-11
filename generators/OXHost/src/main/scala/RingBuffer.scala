package omnixtend

import chisel3._
import chisel3.util._

/**
 * RingBuffer
 *
 * This class implements a ring buffer (circular buffer) using Chisel.
 * A ring buffer is a fixed-size data structure that uses a single, 
 * contiguous block of memory as if it were connected end-to-end.
 *
 * The buffer supports two main operations:
 * 1. Enqueue (write): Adding data to the buffer.
 * 2. Dequeue (read): Removing data from the buffer.
 *
 * The buffer maintains head and tail pointers to track the read and write positions,
 * respectively. It also keeps track of the number of elements currently stored in the buffer.
 *
 * Parameters:
 * @param depth The size of the ring buffer (i.e., the maximum number of elements it can hold).
 */
class RingBuffer(depth: Int) extends Module {
  val io = IO(new Bundle {
    val enq = Flipped(Decoupled(UInt(64.W)))
    val deq = Decoupled(UInt(64.W))
  })

  val buffer = Reg(Vec(depth, UInt(64.W)))
  val head = RegInit(0.U(log2Ceil(depth).W))
  val tail = RegInit(0.U(log2Ceil(depth).W))
  val count = RegInit(0.U(log2Ceil(depth + 1).W))

  // Write logic: executed when data is enqueued (when both valid and ready signals are asserted)
  when(io.enq.fire()) {
    buffer(tail) := io.enq.bits
    tail := tail + 1.U
    when(tail === (depth - 1).U) {
      tail := 0.U
    }
    count := count + 1.U
  }

  io.enq.ready := count =/= depth.U

  // Read logic: executed when data is dequeued (when both valid and ready signals are asserted)
  when(io.deq.fire()) {
    head := head + 1.U
    when(head === (depth - 1).U) {
      head := 0.U
    }
    count := count - 1.U
  }

  io.deq.valid := count =/= 0.U
  io.deq.bits := buffer(head)
}

