package huffman_encoder

import chisel3._
import chisel3.util._

// Bitonic Sort for symbol frequencies
class SymbolSort(val depth: Int, val wtWidth: Int) extends Module {
    require(isPow2(depth), "Depth must be a power of 2 for bitonic sort")
    val io = IO(new Bundle {
        val freq_in    = Input(Vec(depth, UInt(wtWidth.W)))
        val sorted_out = Decoupled(Vec(depth, UInt(wtWidth.W)))
        val start      = Input(Bool())
        val done       = Output(Bool())
    })

    // Comparator cell for ascending order
    def cmp_swap(a: UInt, b: UInt): (UInt, UInt) = {
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
    def bitonic_merge(data: Seq[UInt], up: Boolean): Seq[UInt] = {
        val n = data.length
        if(n == 1) data
        else {
            val k = n/2
            val paired = (0 until k).map{i =>
                val (x, y) = cmp_swap(data(i), data(i+k))
                if(up) (x, y) else (y, x)
            }
            val first = bitonic_merge(paired.map(_._1), up)
            val second = bitonic_merge(paired.map(_._2), up)
            first ++ second
        }
    }

    // Recursive bitonic sort
    def bitonic_sort(data: Seq[UInt], up: Boolean): Seq[UInt] = {
        val n = data.length
        if(n == 1) data
        else {
            val k = n/2
            val first  = bitonic_sort(data.take(k), true)
            val second = bitonic_sort(data.drop(k), false)
            bitonic_merge(first ++ second, up)
        }
    }

    val sortedVec = Wire(Vec(depth, UInt(wtWidth.W)))
    val sortedSeq = bitonic_sort(io.freq_in, true)
    for(i <- 0 until depth) sortedVec(i) := sortedSeq(i)

    val regOut = RegNext(sortedVec)

    io.sorted_out.bits  := regOut
    io.sorted_out.valid := RegNext(io.start)
    io.done             := io.sorted_out.valid
}