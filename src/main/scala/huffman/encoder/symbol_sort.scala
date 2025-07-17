package huffman.encoder

import chisel3._
import chisel3.util._

class SymbolSort(val depth: Int, val wtWidth: Int) extends Module {
    val io = IO(new Bundle {
        val freq_in     = Flipped(Decoupled(Vec(depth, UInt(wtWidth.W))))
        val sorted_out  = Output(Vec(depth, UInt(wtWidth.W)))
        val start       = Input(Bool())
        val done        = Output(Bool())
        val flush       = Input(Bool())
    })

    val sIdle :: sSort :: sOutput :: Nil = Enum(3)
    val state = RegInit(sIdle)
    
    val work_array = Reg(Vec(depth, UInt(wtWidth.W)))
    val sort_counter = RegInit(0.U(log2Ceil(depth + 1).W))
    val phase = RegInit(false.B)  // false: 奇数索引对, true: 偶数索引对
    val output_ready = RegInit(false.B)

    io.freq_in.ready := (state === sIdle) && io.start
    io.sorted_out := work_array
    io.done := (state === sOutput)

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
            
            // 严禁动态分配
            // for(i <- 0 until depth-1 by 2) {
            //     val idx1 = Mux(phase, i.U, (i+1).U)
            //     val idx2 = idx1 + 1.U
                
            //     when(idx2 < depth.U) {
            //         val val1 = work_array(idx1)
            //         val val2 = work_array(idx2)
            //         when(val1 < val2) {
            //             work_array(idx1) := val2
            //             work_array(idx2) := val1
            //         }
            //     }
            // }
            val idxWidth = log2Ceil(depth)
            
            for(i <- 0 until depth-1 by 2) {
                // 1) 用 .U(idxWidth.W) 明确给 i 和 i+1 制定宽度
                val cand1 = i.U(idxWidth.W)
                val cand2 = (i+1).U(idxWidth.W)

                // 2) 通过 Mux 选出 idx1，idx2，宽度都是 idxWidth
                val idx1 = Mux(phase, cand1, cand2)
                val idx2 = idx1 + 1.U  // 依然是 idxWidth 位
                
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
            state := sIdle
            output_ready := false.B
        }
    }

    when(io.flush) {
        state := sIdle
        sort_counter := 0.U
        phase := false.B
        output_ready := false.B
    }
}

