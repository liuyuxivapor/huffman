error id: file://<WORKSPACE>/src/main/scala/huffman/encoder/tree_build.scala:map.
file://<WORKSPACE>/src/main/scala/huffman/encoder/tree_build.scala
empty definition using pc, found symbol in pc: map.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -chisel3/masked/map.
	 -chisel3/masked/map#
	 -chisel3/masked/map().
	 -chisel3/util/masked/map.
	 -chisel3/util/masked/map#
	 -chisel3/util/masked/map().
	 -masked/map.
	 -masked/map#
	 -masked/map().
	 -scala/Predef.masked.map.
	 -scala/Predef.masked.map#
	 -scala/Predef.masked.map().
offset: 1210
uri: file://<WORKSPACE>/src/main/scala/huffman/encoder/tree_build.scala
text:
```scala
package huffman_encoder

import chisel3._
import chisel3.util._

class tree_build(val depth: Int, val idxWidth: Int, val wtWidth: Int) extends Module {
    val maxNodes = 2*depth-1
    val io = IO(new Bundle {
        val sorted_in = Flipped(Decoupled(Vec(depth, UInt(wtWidth.W))))
        val root_idx  = Output(UInt(idxWidth.W))
        val start     = Input(Bool())
        val done      = Output(Bool())
    })

    // storage arrays
    val weight   = RegInit(VecInit(Seq.fill(maxNodes)(UIntMax(wtWidth.W))))
    val depthArr = RegInit(VecInit(Seq.fill(maxNodes)(0.U(log2Ceil(depth).W))))
    val valid    = RegInit(VecInit(Seq.fill(maxNodes)(false.B)))
    val left     = Reg(Vec(maxNodes, UInt(idxWidth.W)))
    val right    = Reg(Vec(maxNodes, UInt(idxWidth.W)))

    // state machine
    val sIdle :: sLoad :: sMerge :: sDone :: Nil = Enum(4)
    val state = RegInit(sIdle)
    val nodeCnt = RegInit(0.U(log2Ceil(maxNodes+1).W))

    // helper functions
    def findTwoMin(vals: Seq[UInt], mask: Seq[Bool]): (UInt, UInt) = {
        val masked = vals.zip(mask).map{ case (v, m) => Mux(m, v, UIntMax(wtWidth.W)) }
        val m1 = masked.reduce((x,y) => Mux(x<=y, x, y))
        val masked2 = masked.ma@@p(v => Mux(v===m1, UIntMax(wtWidth.W), v))
        val m2 = masked2.reduce((x,y) => Mux(x<=y, x, y))
        (m1, m2)
    }

    def idxOf(vals: Seq[UInt], target: UInt): UInt = {
        val idx = Wire(UInt(log2Ceil(maxNodes).W)); idx := 0.U
        for(i <- vals.indices) when(vals(i) === target) {
            idx := i.U 
        }
        idx
    }

    // default params
    io.done := false.B;
    io.root_idx := 0.U;
    io.sorted_in.ready := false.B

    switch(state) {
        is(sIdle) {
            when(io.start) {
                state := sLoad
            }
        }

        is(sLoad) {
            io.sorted_in.ready := true.B
            when(io.sorted_in.fire()) {
                for(i <- 0 until depth) {
                    weight(i) := io.sorted_in.bits(i)
                    depthArr(i):= 0.U
                    valid(i)  := true.B
                }
                nodeCnt := depth.U
                state := sMerge
            }
        }

        is(sMerge) {
            when(nodeCnt < maxNodes.U) {
                val (w1, w2) = findTwoMin(weight, valid)
                val i1 = idxOf(weight, w1); val i2 = idxOf(weight, w2)
                weight(nodeCnt) := w1 + w2
                val d1 = depthArr(i1); val d2 = depthArr(i2)
                depthArr(nodeCnt) := Mux(d1 >= d2, d1 + 1.U, d2 + 1.U)
                left(nodeCnt) := i1; right(nodeCnt) := i2
                valid(i1) := false.B; valid(i2) := false.B; valid(nodeCnt) := true.B
                nodeCnt := nodeCnt + 1.U
            } .otherwise {
                io.root_idx := nodeCnt - 1.U
                state := sDone
            }
        }

        is(sDone) {
            io.done := true.B; io.root_idx := nodeCnt - 1.U
            when(!io.start) {
                state := sIdle
            }
        }
    }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: map.