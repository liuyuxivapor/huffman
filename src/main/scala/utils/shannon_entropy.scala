package huffman_encoder

import chisel3._
import chisel3.util._

/**
 * Shannon Entropy Calculator
 * Calculates H = -¦²(p_i * log2(p_i)) where p_i = freq_i / total_symbols
 */
class ShannonEntropy(val depth: Int, val wtWidth: Int, val fracBits: Int = 16) extends Module {
    val io = IO(new Bundle {
        val freq_in = Input(Vec(depth, UInt(wtWidth.W)))
        val entropy_out = Output(UInt((wtWidth + fracBits).W))
        val valid_in = Input(Bool())
        val valid_out = Output(Bool())
    })

    // Total symbol count
    val total_symbols = freq_in.reduce(_ + _)
    
    // Entropy accumulator (fixed-point arithmetic)
    val entropy_acc = RegInit(0.U((wtWidth + fracBits).W))
    val calc_done = RegInit(false.B)
    val calc_index = RegInit(0.U(log2Ceil(depth).W))
    
    // Log2 lookup table (simplified - in practice would use more sophisticated method)
    val log2_lut = VecInit(Seq(
        0.U,     // log2(0) = undefined, use 0
        0.U,     // log2(1) = 0
        1.U << fracBits,     // log2(2) = 1.0
        (1.585 * (1 << fracBits)).toInt.U,  // log2(3) ¡Ö 1.585
        2.U << fracBits,     // log2(4) = 2.0
        // ... more entries would be needed for full implementation
    ))
    
    when(io.valid_in && !calc_done) {
        when(calc_index < depth.U) {
            val freq = io.freq_in(calc_index)
            when(freq =/= 0.U) {
                // Calculate p_i = freq_i / total_symbols (fixed-point)
                val prob = (freq << fracBits) / total_symbols
                // Approximate log2(prob) and accumulate -p_i * log2(p_i)
                val log_prob = log2_lut(prob >> (fracBits - 3)) // Simplified lookup
                entropy_acc := entropy_acc + (prob * log_prob) >> fracBits
            }
            calc_index := calc_index + 1.U
        }.otherwise {
            calc_done := true.B
        }
    }
    
    when(!io.valid_in) {
        calc_done := false.B
        calc_index := 0.U
        entropy_acc := 0.U
    }
    
    io.entropy_out := entropy_acc
    io.valid_out := calc_done
}
