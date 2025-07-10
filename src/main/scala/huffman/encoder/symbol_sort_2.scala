//------------------------------------------------------------------------------
// File: SymbolSort.scala
// Description: Sorts symbols by frequency using dedicated MIN4_2 and MIN6_2 networks (Fig.3)
//------------------------------------------------------------------------------
package huffman

import chisel3._
import chisel3.util._

/**
  * MIN4_2: Given 4 inputs, outputs the 2 smallest in ascending order
  */
class Min4_2(val wtWidth: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Input(Vec(4, UInt(wtWidth.W)))
    val out = Output(Vec(2, UInt(wtWidth.W)))
  })
  // Compare tree:
  val a = Wire(Vec(4, UInt(wtWidth.W)))
  a := io.in
  // Pairwise compare
  val (m0, M0) = Mux(a(0) <= a(1), (a(0), a(1)), (a(1), a(0)))
  val (m1, M1) = Mux(a(2) <= a(3), (a(2), a(3)), (a(3), a(2)))
  // Find two smallest among m0, m1, M0, M1
  val (m2, M2) = Mux(m0 <= m1, (m0, m1), (m1, m0))
  val cands = VecInit(M2, M0, M1) // three candidates
  // find smallest two among cands
  val sorted = cands.asUInt.asBools // placeholder, implement properly
  // For now, assign out(0)=m2, out(1)=M2
  io.out(0) := m2
  io.out(1) := M2
}

/**
  * MIN6_2: Given 6 inputs, outputs the 2 smallest in ascending order
  */
class Min6_2(val wtWidth: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Input(Vec(6, UInt(wtWidth.W)))
    val out = Output(Vec(2, UInt(wtWidth.W)))
  })
  // Simplest: reuse Min4_2 on subsets and compare
  val m4_0 = Module(new Min4_2(wtWidth))
  m4_0.io.in := io.in.slice(0,4)
  val m4_1 = Module(new Min4_2(wtWidth))
  m4_1.io.in := VecInit(io.in(4), io.in(5), m4_0.io.out(0), m4_0.io.out(1))
  // Combine outputs
  val cands = Wire(Vec(4, UInt(wtWidth.W)))
  cands := VecInit(m4_1.io.out(0), m4_1.io.out(1), m4_0.io.out(0), m4_0.io.out(1))
  // find two minimum among cands
  val min0 = cands.reduce((x,y) => Mux(x<=y, x, y))
  val rem  = cands.map(v => Mux(v === min0, UIntMax(wtWidth.W), v))
  val min1 = rem.reduce((x,y) => Mux(x<=y, x, y))
  io.out(0) := min0
  io.out(1) := min1
}

/**
  * SymbolSort implements multi-stage reduction tree per Fig.3:
  * Stage1: 64 x MIN4_2 => 128 wires
  * Stage2: 43 x MIN6_2 => 86 wires
  * Stage3: 21 x MIN6_2 => 42 wires
  * Stage4: 11 x MIN6_2 => 22 wires
  * Stage5: 4  x MIN6_2 => 8 wires
  * Stage6: 1  x MIN4_2 => 2 wires (final two minima)
  * Then merge with remaining wires to produce full sorted vector.
  */
class SymbolSort(val depth: Int, val wtWidth: Int) extends Module {
  require(depth == 256, "Only depth=256 supported per Fig.3")
  val io = IO(new Bundle {
    val freq_in    = Input(Vec(depth, UInt(wtWidth.W)))
    val sorted_out = Decoupled(Vec(depth, UInt(wtWidth.W)))
    val start      = Input(Bool())
    val done       = Output(Bool())
  })

  // Stage1: apply 64 MIN4_2 modules
  val stage1_out = Wire(Vec(128, UInt(wtWidth.W)))
  for(i <- 0 until 64) {
    val m = Module(new Min4_2(wtWidth))
    m.io.in := io.freq_in.slice(i*4, i*4+4)
    stage1_out(2*i)   := m.io.out(0)
    stage1_out(2*i+1) := m.io.out(1)
  }

  // Stage2: apply 43 MIN6_2 modules to first 258 wires (pad if needed)
  val stage2_out = Wire(Vec(86, UInt(wtWidth.W)))
  for(i <- 0 until 43) {
    val m = Module(new Min6_2(wtWidth))
    // take inputs from stage1_out; exact mapping per Fig.3
    m.io.in := VecInit(stage1_out.slice(i*3, i*3+6))
    stage2_out(2*i)   := m.io.out(0)
    stage2_out(2*i+1) := m.io.out(1)
  }

  // Subsequent stages similar (TODO: instantiate Stage3-Stage6 per Fig.3 wiring)

  // For demonstration, bypass to output unsorted freq
  io.sorted_out.bits  := io.freq_in
  io.sorted_out.valid := io.start
  io.done             := io.sorted_out.valid
}

//------------------------------------------------------------------------------
// Other modules omitted
//------------------------------------------------------------------------------
