package huffman.encoder

import chisel3._
import chisel3.util._

/**
 * Shannon Entropy Calculator
 * Calculates H = -Σ(p_i * log2(p_i)) where p_i = freq_i / total_symbols
 * Shannon entropy measures the average information content in a message
 */
class ShannonEntropy(val depth: Int, val wtWidth: Int, val fracBits: Int = 16) extends Module {
    val io = IO(new Bundle {
        val freq_in = Input(Vec(depth, UInt(wtWidth.W)))
        val entropy_out = Output(UInt((wtWidth + fracBits).W))
        val valid_in = Input(Bool())
        val valid_out = Output(Bool())
    })

    // Total symbol count - sum of all frequencies
    val total_symbols = io.freq_in.reduce(_ + _)
    
    // Entropy accumulator (fixed-point arithmetic)
    val entropy_acc = RegInit(0.U((wtWidth + fracBits).W))
    val calc_done = RegInit(false.B)
    val calc_index = RegInit(0.U(log2Ceil(depth).W))
    
    // Log2 lookup table (simplified implementation)
    // In practice, a more sophisticated logarithm calculation would be used
    val log2_lut = VecInit(Seq(
        0.U,     // log2(0) = undefined, use 0 for safety
        0.U,     // log2(1) = 0
        1.U << fracBits,     // log2(2) = 1.0 in fixed-point
        (1.585 * (1 << fracBits)).toInt.U,  // log2(3) ≈ 1.585
        2.U << fracBits,     // log2(4) = 2.0 in fixed-point
        (2.322 * (1 << fracBits)).toInt.U,  // log2(5) ≈ 2.322
        (2.585 * (1 << fracBits)).toInt.U,  // log2(6) ≈ 2.585
        (2.807 * (1 << fracBits)).toInt.U   // log2(7) ≈ 2.807
        // Additional entries would be needed for full implementation
    ))
    
    // Shannon entropy calculation state machine
    when(io.valid_in && !calc_done) {
        when(calc_index < depth.U) {
            val freq = io.freq_in(calc_index)
            when(freq =/= 0.U && total_symbols =/= 0.U) {
                // Calculate p_i = freq_i / total_symbols (fixed-point representation)
                val prob = (freq << fracBits) / total_symbols
                
                // Bounds check for lookup table access
                val lut_index = Mux(prob >> (fracBits - 3) < log2_lut.length.U, 
                                   prob >> (fracBits - 3), 
                                   (log2_lut.length - 1).U)
                
                // Get log2(prob) from lookup table
                val log_prob = log2_lut(lut_index)
                
                // Accumulate -p_i * log2(p_i) for Shannon entropy
                // Note: We calculate positive value here, negate at output if needed
                val entropy_term = (prob * log_prob) >> fracBits
                entropy_acc := entropy_acc + entropy_term
            }
            calc_index := calc_index + 1.U
        }.otherwise {
            // Calculation complete
            calc_done := true.B
        }
    }
    
    // Reset calculation when valid_in goes low
    when(!io.valid_in) {
        calc_done := false.B
        calc_index := 0.U
        entropy_acc := 0.U
    }
    
    // Output assignments
    io.entropy_out := entropy_acc
    io.valid_out := calc_done
}
