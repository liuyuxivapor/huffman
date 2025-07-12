package huffman.encoder

import chisel3._
import chisel3.util._

/**
 * 流水线化的符号排序器
 * 使用奇偶转置排序算法，适合硬件实现
 * 支持Decoupled接口和流水线控制
 */

class SymbolSort(val depth: Int, val wtWidth: Int) extends Module {
    val io = IO(new Bundle {
        val freq_in     = Flipped(Decoupled(Vec(depth, UInt(wtWidth.W))))
        val sorted_out  = Decoupled(Vec(depth, UInt(wtWidth.W)))
        // 控制信号
        val start       = Input(Bool())
        val done        = Output(Bool())
        val busy        = Output(Bool())
        val flush       = Input(Bool())
    })

    // 状态机定义
    val sIdle :: sSort :: sOutput :: Nil = Enum(3)
    val state = RegInit(sIdle)
    
    // 工作寄存器
    val work_array = Reg(Vec(depth, UInt(wtWidth.W)))
    val sort_counter = RegInit(0.U(log2Ceil(depth + 1).W))
    val phase = RegInit(false.B)  // false: 奇数索引对, true: 偶数索引对
    val output_ready = RegInit(false.B)
    
    // 控制信号
    io.freq_in.ready := (state === sIdle) && io.start
    io.sorted_out.valid := (state === sOutput) && output_ready
    io.sorted_out.bits := work_array
    io.done := (state === sOutput) && io.sorted_out.fire
    io.busy := (state =/= sIdle)
    
    // 主状态机
    switch(state) {
        is(sIdle) {
            when(io.start && io.freq_in.valid) {
                // 加载输入数据
                work_array := io.freq_in.bits
                sort_counter := 0.U
                phase := false.B
                output_ready := false.B
                state := sSort
            }
        }
        
        is(sSort) {
            // 奇偶转置排序 - 每个时钟周期执行一轮比较交换
            val start_idx = Mux(phase, 0.U, 1.U)
            
            // 执行当前轮次的比较交换
            for(i <- 0 until depth-1) {
                val should_compare = Wire(Bool())
                val idx = i.U
                val next_idx = (i+1).U
                
                // 确定是否需要比较这一对
                should_compare := Mux(phase, 
                    idx % 2.U === 0.U,  // 偶数轮：比较 (0,1), (2,3), (4,5)...
                    idx % 2.U === 1.U   // 奇数轮：比较 (1,2), (3,4), (5,6)...
                )
                
                when(should_compare && next_idx < depth.U) {
                    val val1 = work_array(idx)
                    val val2 = work_array(next_idx)
                    
                    // 降序排列：频率高的在前
                    when(val1 < val2) {
                        work_array(idx) := val2
                        work_array(next_idx) := val1
                    }
                }
            }
            
            // 切换奇偶相位
            phase := !phase
            
            // 当完成一轮奇偶对比较时，增加计数器
            when(phase) {
                sort_counter := sort_counter + 1.U
                // 排序完成条件：执行了足够的轮次
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

