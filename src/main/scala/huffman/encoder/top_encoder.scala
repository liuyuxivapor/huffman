package huffman.encoder

import chisel3._
import chisel3.util._

class EntropyAwareHuffmanSystem(val symWidth: Int, val wtWidth: Int, val depth: Int, val maxCodeLen: Int) extends Module {
    val io = IO(new Bundle {
        val data_in = Flipped(Decoupled(UInt(symWidth.W)))
        
        // Encoded output stream
        val encoded_out = Decoupled(new Bundle {
            val code = UInt(maxCodeLen.W)
            val length = UInt(log2Ceil(maxCodeLen+1).W)
            val compression_mode = UInt(1.W)
        })
        
        // Control and status
        val start = Input(Bool())
        val done  = Output(Bool())
        val flush = Input(Bool())
    })

    val stage_valid = RegInit(VecInit(Seq.fill(4)(false.B)))
    val stage_ready = Wire(Vec(4, Bool()))

    val symbol_stat = Module(new SymbolStat(symWidth, wtWidth, depth))
    val symbol_sort = Module(new SymbolSort(depth, wtWidth))
    val entropy_calc = Module(new ShannonEntropy(depth, wtWidth))
    val tree_builder = Module(new EntropyAwareTreeBuilder(depth, wtWidth))

    val freqs1 = Reg(Vec(depth, UInt(wtWidth.W)))
    val freqs2 = Reg(Vec(depth, UInt(wtWidth.W)))
    val mode3  = Reg(UInt(2.W))
    val codes4 = Reg(Vec(depth, UInt(maxCodeLen.W)))
    val lens4  = Reg(Vec(depth, UInt(log2Ceil(maxCodeLen+1).W)))

    val busy = symbol_stat.io.busy || symbol_sort.io.busy || entropy_calc.io.busy || tree_builder.io.busy
    stage_ready(0) := symbol_stat.io.done && !symbol_stat.io.busy
    stage_ready(1) := symbol_sort.io.done && !symbol_sort.io.busy
    stage_ready(2) := entropy_calc.io.done && !entropy_calc.io.busy
    stage_ready(3) := tree_builder.io.done && !tree_builder.io.busy

    val advance = stage_ready.asUInt.andR && !busy && !io.flush

    // Initialize
    symbol_stat.io.data_in.bits := io.data_in.bits
    symbol_stat.io.data_in.valid := stage_valid(0) && io.data_in.valid
    symbol_stat.io.start := stage_valid(0)
    symbol_stat.io.flush := io.flush

    symbol_sort.io.freq_in.valid := stage_valid(1)
    symbol_sort.io.freq_in.bits := freqs1
    symbol_sort.io.start := stage_valid(1)
    symbol_sort.io.flush := io.flush

    entropy_calc.io.freq_in := freqs2
    entropy_calc.io.start := stage_valid(2)
    entropy_calc.io.flush := io.flush

    tree_builder.io.freq_in := freqs2
    tree_builder.io.entropy_config.encoding_mode := Mux(stage_valid(3) && mode3 =/= 0.U, mode3, 0.U)
    tree_builder.io.entropy_config.tree_depth_limit := Mux(stage_valid(3) && mode3 =/= 0.U, log2Ceil(depth).U, 0.U)
    tree_builder.io.entropy_config.min_freq_threshold := Mux(stage_valid(3) && mode3 =/= 0.U, (0.5 * (1 << 16)).toInt.U, 0.U)
    tree_builder.io.start := stage_valid(3) && mode3 =/= 0.U
    tree_builder.io.flush := io.flush

    // Initialize stage control
    stage_valid(0) := io.start && io.data_in.valid

    // Stage 1: SymbolStat
    when(stage_valid(0)) {
        when(symbol_stat.io.done && advance) {
            freqs1 := symbol_stat.io.freq_out.bits
            stage_valid(1) := true.B
            stage_valid(0) := false.B
        }
    }

    // Stage 2: SymbolSort  
    when(stage_valid(1)) {
        when(symbol_sort.io.done && advance) {
            freqs2 := symbol_sort.io.sorted_out.bits
            stage_valid(2) := true.B
            stage_valid(1) := false.B
        }
    }

    // Stage 3: Entropy Calculation
    when(stage_valid(2)) {
        when(entropy_calc.io.done && advance) {
            mode3 := entropy_calc.io.compression_mode
            stage_valid(3) := true.B
            stage_valid(2) := false.B
        }
    }

    // Stage 4: Tree Building
    when(stage_valid(3)) {
        when(mode3 =/= 0.U) {
            when(tree_builder.io.done && advance) {
                codes4 := tree_builder.io.code_out
                lens4 := tree_builder.io.length_out
                stage_valid(3) := false.B
            }
        }.otherwise {
            // Bypass mode
            for(i <- 0 until depth) {
                codes4(i) := i.U
                lens4(i) := symWidth.U
            }
            stage_valid(3) := false.B
        }
    }


    // Output
    io.data_in.ready := stage_valid(0) && io.encoded_out.ready

    when(io.data_in.valid && !io.flush) {
        io.encoded_out.valid := true.B
        when(mode3 === 0.U) {
            io.encoded_out.bits.code := io.data_in.bits
            io.encoded_out.bits.length := symWidth.U
            io.encoded_out.bits.compression_mode := 0.U
        }.otherwise {
            val addr = io.data_in.bits
            io.encoded_out.bits.code := codes4(addr)
            io.encoded_out.bits.length := lens4(addr)
            io.encoded_out.bits.compression_mode := mode3
        }
    }.otherwise {
        io.encoded_out.valid := false.B
        io.encoded_out.bits.code := 0.U
        io.encoded_out.bits.length := 0.U
        io.encoded_out.bits.compression_mode := 0.U
    }

    // Done when pipeline empties
    io.done := !stage_valid.asUInt.orR

    when(io.flush) {
        for(i <- 0 until 4) stage_valid(i) := false.B
    }
}
