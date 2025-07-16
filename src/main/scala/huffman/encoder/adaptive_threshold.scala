package huffman.encoder

import chisel3._
import chisel3.util._

/**
 * Entropy-Aware Adaptive Threshold Controller
 * Uses Shannon entropy to dynamically adjust encoding parameters
 */

class AdaptiveThreshold(val depth: Int, val wtWidth: Int, val fracBits: Int = 16) extends Module {
    val io = IO(new Bundle {
        val entropy_in = Input(UInt((wtWidth + fracBits).W))  // Shannon entropy input
        val entropy_valid = Input(Bool())  // Validity signal for entropy
        
        // Adaptive parameters output
        // val compression_threshold = Output(UInt(fracBits.W))
        val encoding_mode = Output(UInt(1.W))  // 0: bypass, 1: huffman
        // val tree_depth_limit = Output(UInt(log2Ceil(depth).W))
        // val min_freq_threshold = Output(UInt(wtWidth.W))
        
        // val config_valid = Output(Bool())  // Validity signal for config
    })

    // Entropy thresholds (fixed-point), made as parameters for flexibility
    val low_entropy_threshold = (0.5 * (1 << fracBits)).toInt.U   // 0.5 bits/symbol
    val high_entropy_threshold = (6.0 * (1 << fracBits)).toInt.U  // 6.0 bits/symbol
    val optimal_entropy_threshold = (4.0 * (1 << fracBits)).toInt.U // 4.0 bits/symbol

    // Define registers for the configuration parameters
    val encoding_mode_reg = RegInit(0.U(2.W))  // Default encoding mode (0: bypass)
    val compression_threshold_reg = RegInit(0.U(fracBits.W))  // Default compression threshold
    val tree_depth_limit_reg = RegInit(0.U(log2Ceil(depth).W))  // Default tree depth limit
    val min_freq_threshold_reg = RegInit(0.U(wtWidth.W))  // Default minimum frequency threshold
    val config_valid_reg = RegInit(false.B)  // Default config validity signal

    val entropy_reg = RegNext(io.entropy_in)  // Delay entropy value for one cycle
    val valid_reg = RegNext(io.entropy_valid)  // Delay entropy valid signal for one cycle

    // Function to set config for low entropy
    def setLowEntropyConfig(): Unit = {
        encoding_mode_reg := 1.U  // Use Huffman
        compression_threshold_reg := (0.3 * (1 << fracBits)).toInt.U
        tree_depth_limit_reg := (depth / 4).U
        min_freq_threshold_reg := 1.U
    }

    // Function to set config for high entropy
    def setHighEntropyConfig(): Unit = {
        encoding_mode_reg := 0.U  // Bypass or minimal compression
        compression_threshold_reg := (0.9 * (1 << fracBits)).toInt.U
        tree_depth_limit_reg := (depth / 8).U
        min_freq_threshold_reg := (1 << (wtWidth - 2)).U
    }

    // Function to set config for medium entropy
    def setMediumEntropyConfig(): Unit = {
        encoding_mode_reg := 1.U  // Standard Huffman
        compression_threshold_reg := (0.6 * (1 << fracBits)).toInt.U
        tree_depth_limit_reg := (depth / 2).U
        min_freq_threshold_reg := 2.U
    }

    // Set the config based on entropy value
    when(valid_reg) {
        // Low entropy: data is highly compressible
        when(entropy_reg < low_entropy_threshold) {
            setLowEntropyConfig()
        }
        // High entropy: data is less compressible
        .elsewhen(entropy_reg > high_entropy_threshold) {
            setHighEntropyConfig()
        }
        // Medium entropy: optimal Huffman coding
        .otherwise {
            setMediumEntropyConfig()
        }
        config_valid_reg := true.B
    }

    // Output the computed configuration values
    // io.compression_threshold := compression_threshold_reg
    io.encoding_mode := encoding_mode_reg
    // io.tree_depth_limit := tree_depth_limit_reg
    // io.min_freq_threshold := min_freq_threshold_reg
    // io.config_valid := config_valid_reg
}
