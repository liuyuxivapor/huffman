error id: file://<WORKSPACE>/src/main/scala/huffman/encoder/tree_adjust.scala:`<none>`.
file://<WORKSPACE>/src/main/scala/huffman/encoder/tree_adjust.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 486
uri: file://<WORKSPACE>/src/main/scala/huffman/encoder/tree_adjust.scala
text:
```scala
package huffman_encoder

import chisel3._
import chisel3.util._

/**
  * TreeAdjust implements the 3-step depth limiting algorithm:
  * L: maximum allowed depth
  * numLeaves: number of leaves (symbols)
  * Steps:
  * 1) Compute K = (N(L+1)+...+N(maxD))/2, where N(d) is node count at level d
  * 2) Migrate nodes from levels 1..L-1 down to level L until emptySlots(L) >= K
  * 3) Move K nodes to level L; then apply Observation 3 to eliminate any remaining holes
  */
class TreeAdjust(@@val numLeaves: Int, val maxDepth: Int) extends Module {
  val lvlWidth = log2Ceil(numLeaves*2)
  val io = IO(new Bundle {
    val hist_in   = Input(Vec(maxDepth*2+1, UInt(lvlWidth.W))) // N(d) for d=0..maxD
    val start     = Input(Bool())
    val hist_out  = Output(Vec(maxDepth*2+1, UInt(lvlWidth.W))) // adjusted N(d)
    val done      = Output(Bool())
  })

  // Registers
  val hist      = Reg(Vec(maxDepth*2+1, UInt(lvlWidth.W)))
  val emptySlots= Wire(UInt(lvlWidth.W))
  val K         = Wire(UInt(lvlWidth.W))
  val state     = RegInit(0.U(2.W)) // 0=Step1,1=Step2,2=Step3
  val migrateD  = Reg(UInt(log2Ceil(maxDepth+1).W))
  val outHist   = Wire(Vec(maxDepth*2+1, UInt(lvlWidth.W)))

  // Defaults
  io.hist_out := hist
  io.done := (state === 2.U)

  // Compute empty slots at level L: 2^L - N(L)
  emptySlots := (1.U << maxDepth) - hist(maxDepth)
  // Compute sum of nodes above L
  val sumHigh = hist.zipWithIndex.map{ case (v,d) => Mux(d.U > maxDepth.U, v, 0.U) }.reduce(_+_)
  K := sumHigh >> 1  // Step1

  switch(state) {
    is(0.U) {
      when(io.start) {
        // load histogram
        for(d <- 0 until hist.length) hist(d) := io.hist_in(d)
        migrateD := (maxDepth-1).U
        state := 1.U
      }
    }
    is(1.U) {
      // migrate from migrateD down to 1
      when(emptySlots < K && migrateD =/= 0.U) {
        when(hist(migrateD) > 0.U) {
          // move one node: decrease at migrateD, increase at maxDepth, increase holes at migrateD-1
          hist(migrateD)         := hist(migrateD) - 1.U
          hist(maxDepth)         := hist(maxDepth) + 1.U
          hist(migrateD - 1.U)   := hist(migrateD - 1.U) + 2.U
        }
        migrateD := Mux(migrateD === 1.U, (maxDepth-1).U, migrateD - 1.U)
      }.otherwise {
        state := 2.U
      }
    }
    is(2.U) {
      // Step3: nothing more in this simplistic model
      // hist(maxDepth) already increased by K through migrations
      io.hist_out := hist
    }
  }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.