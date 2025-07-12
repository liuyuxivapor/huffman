package huffman_encoder

import chisel3._
import chisel3.util._

/**
 * Tree Quality Analyzer
 * Evaluates Huffman tree quality against theoretical entropy limit
 */
class TreeQualityAnalyzer(val depth: Int, val wtWidth: Int) extends Module {
    val io = IO(new Bundle {
        val freq_in = Input(Vec(depth, UInt(wtWidth.W)))
        val code_length = Input(Vec(depth, UInt(6.W)))
        val valid = Input(Bool())
        
        val avg_length = Output(UInt(16.W))
        val efficiency = Output(UInt(16.W))  // Ratio to theoretical minimum
        val compression_ratio = Output(UInt(16.W))
    })

    val total_symbols = io.freq_in.reduce(_ + _)
    val weighted_length = Wire(UInt(32.W))
    val theoretical_entropy = Wire(UInt(16.W))

    // Calculate average code length
    val length_acc = (0 until depth).map(i => 
        io.freq_in(i) * io.code_length(i)
    ).reduce(_ + _)
    
    weighted_length := length_acc
    
    // Fixed-point division for average length
    val avg_length_calc = RegNext((weighted_length << 8) / total_symbols)
    io.avg_length := avg_length_calc

    // Estimate theoretical entropy (simplified)
    // In practice, would use the Shannon entropy calculator
    theoretical_entropy := log2Ceil(depth).U << 8  // Simplified estimate

    // Calculate efficiency (actual vs theoretical)
    val efficiency_calc = RegNext((theoretical_entropy << 8) / avg_length_calc)
    io.efficiency := efficiency_calc

    // Compression ratio vs uncompressed
    val uncompressed_bits = log2Ceil(depth).U
    val compression_calc = RegNext((avg_length_calc << 8) / (uncompressed_bits << 8))
    io.compression_ratio := compression_calc
}
