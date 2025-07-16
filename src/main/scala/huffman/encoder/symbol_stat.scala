package huffman.encoder

import chisel3._
import chisel3.util._

class SymbolStat(val sym_width: Int, val wtWidth: Int, val depth: Int) extends Module {
    val io = IO(new Bundle {
        val data_in  = Flipped(Decoupled(UInt(sym_width.W)))
        val freq_out = Decoupled(Vec(depth, UInt(wtWidth.W)))
        val start    = Input(Bool())
        val done     = Output(Bool())
        val busy     = Output(Bool())
        val flush    = Input(Bool())
    })

    val freq = RegInit(VecInit(Seq.fill(depth)(0.U(wtWidth.W))))
    val sIdle :: sCount :: sOutput :: Nil = Enum(3)
    val state = RegInit(sIdle)
    val outIdx = RegInit(0.U(log2Ceil(depth).W))

    io.data_in.ready := (state === sCount)
    io.freq_out.valid := (state === sOutput)
    io.freq_out.bits := freq
    io.done := (state === sOutput && io.freq_out.fire)
    io.busy := (state =/= sIdle)

    switch(state) {
        is(sIdle) {
            when(io.start) {
                for(i <- 0 until depth) { 
                    freq(i) := 0.U
                }
                state := sCount
            }
        }
        is(sCount) {
            when(io.data_in.fire) {
                val sym = io.data_in.bits
                freq(sym) := freq(sym) + 1.U
            }
            when(!io.start && !io.data_in.valid) {
                state := sOutput
                outIdx := 0.U
            }
        }
        is(sOutput) {
            when(io.freq_out.fire) {
                state := sIdle
            }
        }
    }

    when(io.flush) {
        state := sIdle
        for(i <- 0 until depth) { 
            freq(i) := 0.U
        }
        outIdx := 0.U
    }
}