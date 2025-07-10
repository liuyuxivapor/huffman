error id: file://<WORKSPACE>/src/main/scala/huffman/encoder/symbol_sort.scala:`<none>`.
file://<WORKSPACE>/src/main/scala/huffman/encoder/symbol_sort.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -chisel3/done.
	 -chisel3/done#
	 -chisel3/done().
	 -chisel3/util/done.
	 -chisel3/util/done#
	 -chisel3/util/done().
	 -done.
	 -done#
	 -done().
	 -scala/Predef.done.
	 -scala/Predef.done#
	 -scala/Predef.done().
offset: 395
uri: file://<WORKSPACE>/src/main/scala/huffman/encoder/symbol_sort.scala
text:
```scala
package huffman_encoder

import chisel3._
import chisel3.util._

class SymbolSort(val depth: Int, val wtWidth: Int) extends Module {
 `` require(isPow2(depth), "Depth must be a power of 2 for bitonic sort")
  val io = IO(new Bundle {
    val freq_in    = Input(Vec(depth, UInt(wtWidth.W)))
    val sorted_out = Decoupled(Vec(depth, UInt(wtWidth.W)))
    val start      = Input(Bool())
    val do@@ne       = Output(Bool())
  })``

  // Comparator cell for ascending order
  def cmpSwap(a: UInt, b: UInt): (UInt, UInt) = {
    val min = Wire(UInt(wtWidth.W))
    val max = Wire(UInt(wtWidth.W))
    when(a <= b) {
      min := a; max := b
    }.otherwise {
      min := b; max := a
    }
    (min, max)
  }

  // Recursive bitonic merge
  def bitonicMerge(data: Seq[UInt], up: Boolean): Seq[UInt] = {
    val n = data.length
    if(n == 1) data
    else {
      val k = n/2
      val paired = (0 until k).map{i =>
        val (x, y) = cmpSwap(data(i), data(i+k))
        if(up) (x, y) else (y, x)
      }
      val first = bitonicMerge(paired.map(_._1), up)
      val second= bitonicMerge(paired.map(_._2), up)
      first ++ second
    }
  }

  // Recursive bitonic sort
  def bitonicSort(data: Seq[UInt], up: Boolean): Seq[UInt] = {
    val n = data.length
    if(n == 1) data
    else {
      val k = n/2
      val first  = bitonicSort(data.take(k), true)
      val second = bitonicSort(data.drop(k), false)
      bitonicMerge(first ++ second, up)
    }
  }

  // wires for combinational network
  val sortedVec = Wire(Vec(depth, UInt(wtWidth.W)))
  val sortedSeq = bitonicSort(io.freq_in, true)
  for(i <- 0 until depth) sortedVec(i) := sortedSeq(i)

  // Pipeline registers could be added here for timing
  val regOut = RegNext(sortedVec)

  // Output logic
  io.sorted_out.bits  := regOut
  io.sorted_out.valid := RegNext(io.start)
  io.done             := io.sorted_out.valid
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.