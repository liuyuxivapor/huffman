package huffman.encoder

import chisel3._
import chisel3.util._

/**
 * Shannon Entropy Calculation with Adaptive Threshold Control
 * Integrates entropy calculation with adaptive threshold-based dynamic control.
 */

class ShannonEntropy(val depth: Int, val wtWidth: Int) extends Module {
    val io = IO(new Bundle {
        val freq_in = Input(Vec(depth, UInt(wtWidth.W)))  // Frequency input
        val start = Input(Bool())  // Valid signal for input data
        
        // Adaptive threshold control output
        val compression_mode = Output(UInt(1.W))  // 0: bypass, 1: huffman
        // val tree_depth_limit = Output(UInt(log2Ceil(depth).W))  // Depth limit for tree construction
        // val min_freq_threshold = Output(UInt(wtWidth.W))  // Minimum frequency threshold
        
        // Entropy output and control
        val entropy_out = Output(UInt(32.W))
        val done = Output(Bool())  // Valid signal for entropy output
        
        val busy = Output(Bool())  // Busy signal for the module
        val flush = Input(Bool())  // Flush signal for clearing the module state
    })

    val sIdle :: sCalculateEntropy :: sDone :: Nil = Enum(3)
    val state = RegInit(sIdle)

    val calc_index = RegInit(0.U(log2Ceil(depth).W))
    val entropy_acc = RegInit(0.U(32.W))
    val finalEntropy = RegInit(0.U(32.W))
    val total_symbols = RegInit(0.U(wtWidth.W))

    val INT_BITS = 8
    val FRAC_BITS = 16
    val FIXED_POINT_ONE = (1 << FRAC_BITS).U(32.W)

    // Adaptive Threshold module instantiation
    val threshold_ctrl = Module(new AdaptiveThreshold(depth, wtWidth, FRAC_BITS))
    threshold_ctrl.io.entropy_in    := finalEntropy
    threshold_ctrl.io.entropy_valid := false.B

    // Busy signal indicates whether the module is in processing state
    io.busy := state =/= sIdle
    io.done := state === sDone
    io.entropy_out := finalEntropy
    io.compression_mode := threshold_ctrl.io.encoding_mode
    // io.tree_depth_limit := threshold_ctrl.io.tree_depth_limit
    // io.min_freq_threshold := threshold_ctrl.io.min_freq_threshold

    // Function to calculate the position of the highest set bit in x (k(x))
    def calculateK(x: UInt): UInt = {
        PriorityEncoder((0 until wtWidth).map(i => x(i)).reverse)
    }

    // State machine for entropy calculation and adaptive control
    switch(state) {
        is(sIdle) {
            when(io.start) {
                calc_index := 0.U
                entropy_acc := 0.U
                total_symbols := io.freq_in.reduce(_ + _)
                state := sCalculateEntropy
            }
        }

        is(sCalculateEntropy) {
            when(calc_index < depth.U) {
                val freq = io.freq_in(calc_index)
                when(freq =/= 0.U && total_symbols =/= 0.U) {
                    // Calculate probability p_i = freq_i / total_symbols (fixed-point)
                    val prob = ((freq << FRAC_BITS) / total_symbols).asUInt
                    
                    // Calculate k(x) - find the highest set bit position of prob
                    val k = calculateK(prob)
                    
                    // Compute 2^(k+1) (fixed-point)
                    val pow2_k1 = (1.U << (k.asUInt + 1.U + FRAC_BITS.U - INT_BITS.U)).asUInt
                    
                    // Compute x * k(x) (fixed-point)
                    val x_times_k = (prob * k) >> FRAC_BITS
                    
                    // Compute 2x (fixed-point)
                    val two_x = prob << 1
                    
                    // Apply the approximation formula: x * log2(x) ≈ x * k(x) + 2x - 2^(k+1)
                    val x_log2_x_approx = x_times_k + two_x - pow2_k1
                    
                    // Add -p_i * log2(p_i) (negate the term)
                    val entropy_term = (prob * x_log2_x_approx) >> FRAC_BITS
                    entropy_acc := entropy_acc + entropy_term
                }
                calc_index := calc_index + 1.U
            }.otherwise {
                state := sDone
            }
        }

        is(sDone) {
            // Final entropy value is the negative of the accumulated entropy
            finalEntropy := (FIXED_POINT_ONE - entropy_acc) >> (FRAC_BITS - 16)

            // Pass entropy value to AdaptiveThreshold for dynamic control
            threshold_ctrl.io.entropy_valid := true.B

            when(!io.start) {
                state := sIdle
            }
        }
    }

    when(io.flush) {
        state := sIdle
        calc_index := 0.U
        entropy_acc := 0.U
        total_symbols := 0.U
    }
}
