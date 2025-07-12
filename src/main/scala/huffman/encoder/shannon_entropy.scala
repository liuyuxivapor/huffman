package huffman.encoder

import chisel3._
import chisel3.util._

class ShannonEntropy(val depth: Int, val wtWidth: Int) extends Module {
    val io = IO(new Bundle {
        val freq_in = Input(Vec(depth, UInt(wtWidth.W)))
        val valid_in = Input(Bool())
        val entropy_out = Output(UInt(32.W))
        val valid_out = Output(Bool())
        val busy = Output(Bool())
        val flush = Input(Bool())
    })

    val calc_index = RegInit(0.U(log2Ceil(depth).W))
    val entropy_acc = RegInit(0.U(32.W))
    val calc_done = RegInit(false.B)
    val total_symbols = io.freq_in.reduce(_ + _)

    // 假设的查找表
    val log2_lut = VecInit(Seq.fill(16)(0.U(16.W))) 

    io.busy := !calc_done
    io.entropy_out := entropy_acc
    io.valid_out := calc_done

    // Shannon entropy calculation state machine
    when(io.valid_in && !calc_done) {
        when(calc_index < depth.U) {
            val freq = io.freq_in(calc_index)
            when(freq =/= 0.U && total_symbols =/= 0.U) {
                // Calculate p_i = freq_i / total_symbols (fixed-point representation)
                val prob = (freq << 16) / total_symbols
                
                // Bounds check for lookup table access
                val lut_index = Mux(prob >> (16 - 3) < log2_lut.length.U, 
                                   prob >> (16 - 3), 
                                   (log2_lut.length - 1).U)
                
                // Get log2(prob) from lookup table
                val log_prob = log2_lut(lut_index)
                
                // Accumulate -p_i * log2(p_i) for Shannon entropy
                // Note: We calculate positive value here, negate at output if needed
                val entropy_term = (prob * log_prob) >> 16
                entropy_acc := entropy_acc + entropy_term
            }
            calc_index := calc_index + 1.U
        }.otherwise {
            // Calculation complete
            calc_done := true.B
        }
    }
    
    // Reset calculation when valid_in goes low
    when(!io.valid_in || io.flush) {
        calc_done := false.B
        calc_index := 0.U
        entropy_acc := 0.U
    }
}