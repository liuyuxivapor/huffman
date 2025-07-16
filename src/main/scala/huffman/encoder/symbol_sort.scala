package huffman.encoder

import chisel3._
import chisel3.util._

class SymbolSort(val depth: Int, val wtWidth: Int) extends Module {
    val io = IO(new Bundle {
        val freq_in     = Flipped(Decoupled(Vec(depth, UInt(wtWidth.W))))
        val sorted_out  = Decoupled(Vec(depth, UInt(wtWidth.W)))
        val start       = Input(Bool())
        val done        = Output(Bool())
        val busy        = Output(Bool())
        val flush       = Input(Bool())
    })

    val sIdle :: sSort :: sOutput :: Nil = Enum(3)
    val state = RegInit(sIdle)
    
    val work_array = Reg(Vec(depth, UInt(wtWidth.W)))
    val sort_counter = RegInit(0.U(log2Ceil(depth + 1).W))
    val phase = RegInit(false.B)  // false: 奇数索引对, true: 偶数索引对
    val output_ready = RegInit(false.B)

    io.freq_in.ready := (state === sIdle) && io.start
    io.sorted_out.valid := (state === sOutput) && output_ready
    io.sorted_out.bits := work_array
    io.done := (state === sOutput) && io.sorted_out.fire
    io.busy := (state =/= sIdle)
    
    switch(state) {
        is(sIdle) {
            when(io.start && io.freq_in.valid) {
                work_array := io.freq_in.bits
                sort_counter := 0.U
                phase := false.B
                output_ready := false.B
                state := sSort
            }
        }
        
        is(sSort) {
            // 奇偶转置排序，每个时钟周期执行一轮比较交换
            val start_idx = Mux(phase, 0.U, 1.U)
            
            for(i <- 0 until depth-1 by 2) {
                val idx1 = Mux(phase, i.U, (i+1).U)
                val idx2 = idx1 + 1.U
                
                when(idx2 < depth.U) {
                    val val1 = work_array(idx1)
                    val val2 = work_array(idx2)
                    when(val1 < val2) {
                        work_array(idx1) := val2
                        work_array(idx2) := val1
                    }
                }
            }
            
            phase := !phase
            
            when(phase) {
                sort_counter := sort_counter + 1.U
                when(sort_counter >= depth.U) {
                    output_ready := true.B
                    state := sOutput
                }
            }
        }
        
        is(sOutput) {
            when(io.sorted_out.fire) {
                state := sIdle
                output_ready := false.B
            }
        }
    }

    when(io.flush) {
        state := sIdle
        sort_counter := 0.U
        phase := false.B
        output_ready := false.B
    }
}

