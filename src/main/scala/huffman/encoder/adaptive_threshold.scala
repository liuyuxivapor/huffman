package huffman.encoder

import chisel3._
import chisel3.util._

class AdaptiveThreshold(val depth: Int, val wtWidth: Int, val fracBits: Int = 16) extends Module {
    val io = IO(new Bundle {
        val entropy_in = Input(UInt((wtWidth + fracBits).W))
        val entropy_valid = Input(Bool())
        
        // Adaptive parameters output
        val compression_threshold = Output(UInt(fracBits.W))
        val encoding_mode = Output(UInt(2.W))  // 0: bypass, 1: huffman, 2: hybrid
        val tree_depth_limit = Output(UInt(log2Ceil(depth).W))
        val min_freq_threshold = Output(UInt(wtWidth.W))
        
        val config_valid = Output(Bool())
        val busy = Output(Bool())
        val flush = Input(Bool())
    })

    // Entropy thresholds (fixed-point)
    val low_entropy_threshold = (0.5 * (1 << fracBits)).toInt.U   // 0.5 bits/symbol
    val high_entropy_threshold = (6.0 * (1 << fracBits)).toInt.U  // 6.0 bits/symbol
    val optimal_entropy_threshold = (4.0 * (1 << fracBits)).toInt.U // 4.0 bits/symbol

    val config_reg = RegInit(0.U.asTypeOf(io))
    val entropy_reg = RegNext(io.entropy_in)
    val valid_reg = RegNext(io.entropy_valid)

    io.busy := valid_reg
    io.config_valid := valid_reg

    when(valid_reg) {
        // Low entropy: data is highly compressible
        when(entropy_reg < low_entropy_threshold) {
            config_reg.encoding_mode := 1.U  // Use Huffman
            config_reg.compression_threshold := (0.3 * (1 << fracBits)).toInt.U
            config_reg.tree_depth_limit := (depth/4).U
        }
        // High entropy: data is less compressible
        when(entropy_reg > high_entropy_threshold) {
            config_reg.encoding_mode := 0.U  // Bypass
            config_reg.compression_threshold := (0.8 * (1 << fracBits)).toInt.U
            config_reg.tree_depth_limit := depth.U
        }
        // Optimal entropy: use hybrid mode
        when(entropy_reg >= low_entropy_threshold && entropy_reg <= high_entropy_threshold) {
            config_reg.encoding_mode := 2.U  // Hybrid
            config_reg.compression_threshold := (0.6 * (1 << fracBits)).toInt.U
            config_reg.tree_depth_limit := (depth/2).U
        }
    }

    when(io.flush) {
        config_reg := 0.U.asTypeOf(io)
        entropy_reg := 0.U
        valid_reg := false.B
    }
}