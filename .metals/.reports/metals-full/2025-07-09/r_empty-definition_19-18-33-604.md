error id: file://<WORKSPACE>/src/main/scala/huffman/encoder/symbol_stat.scala:`<none>`.
file://<WORKSPACE>/src/main/scala/huffman/encoder/symbol_stat.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -chisel3/Decoupled.
	 -chisel3/Decoupled#
	 -chisel3/Decoupled().
	 -chisel3/util/Decoupled.
	 -chisel3/util/Decoupled#
	 -chisel3/util/Decoupled().
	 -Decoupled.
	 -Decoupled#
	 -Decoupled().
	 -scala/Predef.Decoupled.
	 -scala/Predef.Decoupled#
	 -scala/Predef.Decoupled().
offset: 497
uri: file://<WORKSPACE>/src/main/scala/huffman/encoder/symbol_stat.scala
text:
```scala
package huffman_encoder

import chisel3._
import chisel3.util._

/**
  * SymbolStat reads a stream of symbols, counts their occurrences, and outputs the frequency vector.
  * FSM states:
  *  - idle: wait for start
  *  - count: increment counters on each symbol input
  *  - output: stream out frequency values
  *  - done: signal completion
  */

class SymbolStat(val symWidth: Int, val wtWidth: Int, val depth: Int) extends Module {
    val io = IO(new Bundle {
    val data_in  = Flipped(Decou@@pled(UInt(symWidth.W)))
    val freq_out = Decoupled(Vec(depth, UInt(wtWidth.W)))
    val start    = Input(Bool())
    val done     = Output(Bool())
    })

  // Frequency registers
  val freq = RegInit(VecInit(Seq.fill(depth)(0.U(wtWidth.W))))
  // state: 0-idle,1-count,2-output
  val sIdle :: sCount :: sOutput :: Nil = Enum(3)
  val state = RegInit(sIdle)
  // output index
  val outIdx = RegInit(0.U(log2Ceil(depth).W))

  // Default signals
  io.data_in.ready := false.B
  io.freq_out.valid := false.B
  io.freq_out.bits := freq
  io.done := false.B

  switch(state) {
    is(sIdle) {
      when(io.start) {
        // clear frequencies
        for(i <- 0 until depth) { freq(i) := 0.U }
        state := sCount
      }
    }
    is(sCount) {
      io.data_in.ready := true.B
      when(io.data_in.fire()) {
        // increment counter for symbol
        val sym = io.data_in.bits
        freq(sym) := freq(sym) + 1.U
      }
      when(!io.start && !io.data_in.valid) {
        // when input ends (start deasserted and no data), move to output
        state := sOutput
        outIdx := 0.U
      }
    }
    is(sOutput) {
      io.freq_out.valid := true.B
      io.freq_out.bits := freq
      when(io.freq_out.fire()) {
        outIdx := outIdx + 1.U
        when(outIdx === (depth-1).U) {
          state := sIdle
          io.done := true.B
        }
      }
    }
  }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.