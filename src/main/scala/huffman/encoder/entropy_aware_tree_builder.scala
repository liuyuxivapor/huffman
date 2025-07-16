package huffman.encoder

import chisel3._
import chisel3.util._

class EntropyAwareTreeBuilder(val depth: Int, val wtWidth: Int) extends Module {
    val io = IO(new Bundle {
        val freq_in = Input(Vec(depth, UInt(wtWidth.W)))
        val entropy_config = Input(new Bundle {
            val encoding_mode    = UInt(1.W)
            val tree_depth_limit = UInt(log2Ceil(depth).W)
            val min_freq_threshold = UInt(wtWidth.W)
        })
        val start = Input(Bool())
        val flush = Input(Bool())
        val busy = Output(Bool())
        val done = Output(Bool())
        val code_out = Output(Vec(depth, UInt(16.W)))
        val length_out = Output(Vec(depth, UInt(6.W)))
    })

    val builder = Module(new HuffmanTreeBuilder(depth, wtWidth))
    // val adjust  = Module(new tree_adjust(depth, io.entropy_config.tree_depth_limit.litValue.toInt))
    val adjust  = Module(new tree_adjust(depth, 16)) //手动限制16

    val filtered = Reg(Vec(depth, UInt(wtWidth.W)))
    val depthsIn = Reg(Vec(depth, UInt(adjust.depthWidth.W)))
    val depthsOut= Reg(Vec(depth, UInt(adjust.depthWidth.W)))
    val codes    = Reg(Vec(depth, UInt(16.W)))
    val lengths  = Reg(Vec(depth, UInt(6.W)))

    val sIdle :: sFilter :: sBuild :: sAdjust :: sGen :: sDone :: Nil = Enum(6)
    val state = RegInit(sIdle)

    io.busy := state =/= sIdle
    io.done := state === sDone
    io.code_out := codes
    io.length_out := lengths
    builder.io.start := false.B
    builder.io.freqs := filtered
    adjust.io.start := false.B
    adjust.io.depths_in := depthsIn

    switch(state) {
        is(sIdle) { 
            when(io.start) { 
                state := sFilter 
            } 
        }

        is(sFilter) {
            for(i <- 0 until depth) {
                filtered(i) := Mux(io.freq_in(i) >= io.entropy_config.min_freq_threshold, io.freq_in(i), 0.U)
            }
            state := sBuild
        }

        is(sBuild) {
            when(io.start) { 
                builder.io.start := true.B 
            }
            when(builder.io.done) {
                depthsIn := builder.io.depths
                state := sAdjust
            }
        }

        is(sAdjust) {
            when(io.start) { 
                adjust.io.start := true.B 
            }
            when(adjust.io.done) {
                depthsOut := adjust.io.depths_out
                state := sGen
            }
        }

        is(sGen) {
            // canonical Huffman code generation
            // val maxL = io.entropy_config.tree_depth_limit
            val maxL = 16
            val wlen = adjust.depthWidth
            val count = Wire(Vec(maxL+1, UInt(wlen.W)))
            for(d <- 0 to maxL) count(d) := 0.U
            for(i <- 0 until depth) count(depthsOut(i)) := count(depthsOut(i)) + 1.U

            val codeStart = Wire(Vec(maxL+1, UInt(16.W)))
            var code = 0.U(16.W)
            for(d <- 0 to maxL) {
                codeStart(d) := code
                code = (code + count(d)) << 1
            }

            val nextCode = RegInit(VecInit(Seq.fill(maxL+1)(0.U(16.W))))
            for(d <- 0 to maxL) nextCode(d) := codeStart(d)
            for(i <- 0 until depth) {
                lengths(i) := depthsOut(i)
                codes(i) := nextCode(depthsOut(i))
                nextCode(depthsOut(i)) := nextCode(depthsOut(i)) + 1.U
            }
            state := sDone
        }

        is(sDone) {
            when(!io.start) { 
                state := sIdle 
            }
        }
    }

    when(io.flush) { 
        state := sIdle 
    }
}