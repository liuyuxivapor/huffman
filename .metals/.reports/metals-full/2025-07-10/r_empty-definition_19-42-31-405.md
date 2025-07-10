error id: file://<WORKSPACE>/src/main/scala/huffman/encoder/encoder.scala:`<none>`.
file://<WORKSPACE>/src/main/scala/huffman/encoder/encoder.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -chisel3/io/data_in/ready.
	 -chisel3/io/data_in/ready#
	 -chisel3/io/data_in/ready().
	 -chisel3/util/io/data_in/ready.
	 -chisel3/util/io/data_in/ready#
	 -chisel3/util/io/data_in/ready().
	 -io/data_in/ready.
	 -io/data_in/ready#
	 -io/data_in/ready().
	 -scala/Predef.io.data_in.ready.
	 -scala/Predef.io.data_in.ready#
	 -scala/Predef.io.data_in.ready().
offset: 1997
uri: file://<WORKSPACE>/src/main/scala/huffman/encoder/encoder.scala
text:
```scala
package huffman_encoder

import chisel3._
import chisel3.util._

/**
 * Complete Entropy-Aware Huffman Encoding System
 * Integrates all components with entropy-based adaptive control
 */
class EntropyAwareHuffmanSystem(val symWidth: Int, val wtWidth: Int, val depth: Int, val maxCodeLen: Int) extends Module {
    val io = IO(new Bundle {
        // Input data stream
        val data_in = Flipped(Decoupled(UInt(symWidth.W)))
        
        // Encoded output stream
        val encoded_out = Decoupled(new Bundle {
            val code = UInt(maxCodeLen.W)
            val length = UInt(log2Ceil(maxCodeLen+1).W)
            val compression_mode = UInt(2.W)
        })
        
        // Control and status
        val start = Input(Bool())
        val done = Output(Bool())
        val bypass_mode = Output(Bool())
        
        // Performance metrics
        val entropy_value = Output(UInt(32.W))
        val compression_ratio = Output(UInt(16.W))
        val encoding_efficiency = Output(UInt(16.W))
        val total_symbols = Output(UInt(32.W))
        val total_encoded_bits = Output(UInt(32.W))
    })

    // System state machine
    val sIdle :: sStatistics :: sEntropyCalc :: sAdaptConfig :: sTreeBuild :: sEncode :: sDone :: Nil = Enum(7)
    val state = RegInit(sIdle)

    // Component instantiation
    val symbol_stat = Module(new SymbolStat(symWidth, wtWidth, depth))
    val entropy_calc = Module(new ShannonEntropy(depth, wtWidth))
    val adaptive_ctrl = Module(new AdaptiveThreshold(depth, wtWidth))
    val tree_builder = Module(new EntropyAwareTreeBuilder(depth, wtWidth))
    val encoder = Module(new HuffmanEncoder(symWidth, maxCodeLen, depth))
    val mem_wrapper = Module(new MemWrapper(depth, maxCodeLen, log2Ceil(depth)))

    // Internal registers
    val symbol_count = RegInit(0.U(32.W))
    val encoded_bits = RegInit(0.U(32.W))
    val current_mode = RegInit(0.U(2.W))
    val bypass_active = RegInit(false.B)

    // Default connections
    io.data_in.re@@ady := false.B
    io.encoded_out.valid := false.B
    io.encoded_out.bits := DontCare
    io.done := false.B
    io.bypass_mode := bypass_active

    // Statistics output
    io.entropy_value := entropy_calc.io.entropy_out
    io.total_symbols := symbol_count
    io.total_encoded_bits := encoded_bits
    
    // Main system FSM
    switch(state) {
        is(sIdle) {
            when(io.start) {
                state := sStatistics
                symbol_count := 0.U
                encoded_bits := 0.U
                bypass_active := false.B
            }
        }

        is(sStatistics) {
            // Symbol statistics collection phase
            io.data_in <> symbol_stat.io.data_in
            symbol_stat.io.start := true.B
            
            when(symbol_stat.io.done) {
                state := sEntropyCalc
            }
            
            // Count symbols
            when(symbol_stat.io.data_in.fire()) {
                symbol_count := symbol_count + 1.U
            }
        }

        is(sEntropyCalc) {
            // Calculate Shannon entropy
            entropy_calc.io.freq_in := symbol_stat.io.freq_out.bits
            entropy_calc.io.valid_in := symbol_stat.io.freq_out.valid
            
            when(entropy_calc.io.valid_out) {
                state := sAdaptConfig
            }
        }

        is(sAdaptConfig) {
            // Generate adaptive configuration
            adaptive_ctrl.io.entropy_in := entropy_calc.io.entropy_out
            adaptive_ctrl.io.entropy_valid := true.B
            
            when(adaptive_ctrl.io.config_valid) {
                current_mode := adaptive_ctrl.io.encoding_mode
                
                // Check if bypass mode should be used
                when(adaptive_ctrl.io.encoding_mode === 0.U) {
                    bypass_active := true.B
                    state := sEncode  // Skip tree building
                }.otherwise {
                    state := sTreeBuild
                }
            }
        }

        is(sTreeBuild) {
            // Build Huffman tree with entropy-aware parameters
            tree_builder.io.freq_in := symbol_stat.io.freq_out.bits
            tree_builder.io.entropy_config.encoding_mode := adaptive_ctrl.io.encoding_mode
            tree_builder.io.entropy_config.tree_depth_limit := adaptive_ctrl.io.tree_depth_limit
            tree_builder.io.entropy_config.min_freq_threshold := adaptive_ctrl.io.min_freq_threshold
            tree_builder.io.entropy_config.config_valid := adaptive_ctrl.io.config_valid
            tree_builder.io.start := true.B
            
            // Store codes in memory
            when(tree_builder.io.done) {
                // Write tree to memory wrapper
                for(i <- 0 until depth) {
                    mem_wrapper.io.write_en := true.B
                    mem_wrapper.io.write_addr := i.U
                    mem_wrapper.io.write_code := tree_builder.io.code_out(i)
                    mem_wrapper.io.write_length := tree_builder.io.length_out(i)
                }
                state := sEncode
            }
        }

        is(sEncode) {
            when(bypass_active) {
                // Bypass mode: direct output without compression
                io.data_in <> io.encoded_out
                io.encoded_out.bits.code := io.data_in.bits
                io.encoded_out.bits.length := symWidth.U
                io.encoded_out.bits.compression_mode := 0.U
                
                when(io.encoded_out.fire()) {
                    encoded_bits := encoded_bits + symWidth.U
                }
                
                when(!io.start && !io.data_in.valid) {
                    state := sDone
                }
            }.otherwise {
                // Huffman encoding mode
                io.data_in <> encoder.io.symbol_in
                encoder.io.start := true.B
                
                // Connect memory interface
                encoder.io.mem_read_addr <> mem_wrapper.io.read_addr
                encoder.io.mem_code_data := mem_wrapper.io.read_code
                encoder.io.mem_length_data := mem_wrapper.io.read_length
                encoder.io.mem_valid := mem_wrapper.io.read_valid
                
                // Output encoded data
                io.encoded_out.valid := encoder.io.code_out.valid
                io.encoded_out.bits.code := encoder.io.code_out.bits.code
                io.encoded_out.bits.length := encoder.io.code_out.bits.length
                io.encoded_out.bits.compression_mode := current_mode
                encoder.io.code_out.ready := io.encoded_out.ready
                
                when(encoder.io.code_out.fire()) {
                    encoded_bits := encoded_bits + encoder.io.code_out.bits.length
                }
                
                when(encoder.io.done) {
                    state := sDone
                }
            }
        }

        is(sDone) {
            io.done := true.B
            
            // Calculate final compression metrics
            val theoretical_bits = symbol_count * symWidth.U
            val compression_ratio_calc = (encoded_bits << 8) / theoretical_bits
            io.compression_ratio := compression_ratio_calc
            
            val entropy_efficiency = (entropy_calc.io.entropy_out << 8) / (encoded_bits << 8)
            io.encoding_efficiency := entropy_efficiency
            
            when(!io.start) {
                state := sIdle
            }
        }
    }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.