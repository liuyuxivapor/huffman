package huffman.encoder

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
        val flush = Input(Bool())
        
        // Performance metrics
        val entropy_value = Output(UInt(32.W))
        val compression_ratio = Output(UInt(16.W))
        val encoding_efficiency = Output(UInt(16.W))
        val total_symbols = Output(UInt(32.W))
        val total_encoded_bits = Output(UInt(32.W))
    })

    val sIdle :: sRunning :: sDone :: Nil = Enum(3)
    val state = RegInit(sIdle)

    val stage_valid = RegInit(VecInit(Seq.fill(5)(false.B)))
    val stage_ready = Wire(Vec(5, Bool()))


    // Component instantiation
    val symbol_stat = Module(new SymbolStat(symWidth, wtWidth, depth))
    val symbol_sort = Module(new SymbolSort(depth, wtWidth))
    val entropy_calc = Module(new ShannonEntropy(depth, wtWidth))
    val adaptive_ctrl = Module(new AdaptiveThreshold(depth, wtWidth))
    val tree_builder = Module(new EntropyAwareTreeBuilder(depth, wtWidth))
    val mem_wrapper = Module(new MemWrapper(depth, maxCodeLen, log2Ceil(depth)))

    // Internal registers
    val symbol_count = RegInit(0.U(32.W))
    val encoded_bits = RegInit(0.U(32.W))
    val current_mode = RegInit(0.U(2.W))
    val bypass_active = RegInit(false.B)

    // Default connections
    io.data_in.ready := false.B
    io.encoded_out.valid := false.B
    io.encoded_out.bits := DontCare
    io.done := false.B
    io.bypass_mode := bypass_active

    // Statistics output
    io.entropy_value := entropy_calc.io.entropy_out
    io.total_symbols := symbol_count
    io.total_encoded_bits := encoded_bits

    val stage1_data = RegInit(VecInit(Seq.fill(depth)(0.U(wtWidth.W))))
    val stage2_data = RegInit(VecInit(Seq.fill(depth)(0.U(wtWidth.W))))
    val stage3_entropy = RegInit(0.U(32.W))
    val stage3_mode = RegInit(0.U(2.W))
    val stage4_codes = RegInit(VecInit(Seq.fill(depth)(0.U(maxCodeLen.W))))
    val stage4_lengths = RegInit(VecInit(Seq.fill(depth)(0.U(log2Ceil(maxCodeLen+1).W))))

    // 添加各级busy信号收集
    val stage_busy = Wire(Vec(5, Bool()))
    stage_busy(0) := symbol_stat.io.busy
    stage_busy(1) := symbol_sort.io.busy  
    stage_busy(2) := entropy_calc.io.busy || adaptive_ctrl.io.busy
    stage_busy(3) := tree_builder.io.busy
    stage_busy(4) := mem_wrapper.io.busy

    val system_busy = stage_busy.asUInt.orR || stage_valid.asUInt.orR
    
    // 改进流水线推进逻辑
    val pipeline_stall = stage_busy.asUInt.orR
    val pipeline_advance = Wire(Bool())
    pipeline_advance := stage_ready.asUInt.andR && !pipeline_stall && !io.flush

    // 每级推进条件包含busy检查
    stage_ready(0) := symbol_stat.io.done && !symbol_stat.io.busy
    stage_ready(1) := symbol_sort.io.done && !symbol_sort.io.busy
    stage_ready(2) := entropy_calc.io.valid_out && adaptive_ctrl.io.config_valid && 
                    !entropy_calc.io.busy && !adaptive_ctrl.io.busy
    stage_ready(3) := tree_builder.io.done && !tree_builder.io.busy
    stage_ready(4) := !mem_wrapper.io.busy

    // 第1级：符号统计
    when(stage_valid(0) || io.start) {
        symbol_stat.io.data_in <> io.data_in
        symbol_stat.io.start := stage_valid(0) || io.start
        
        when(symbol_stat.io.done && pipeline_advance) {
            stage1_data := symbol_stat.io.freq_out.bits
            stage_valid(1) := true.B
            stage_valid(0) := false.B
        }
    }

    // 第2级：符号排序
    when(stage_valid(1)) {
        symbol_sort.io.freq_in.valid := true.B
        symbol_sort.io.freq_in.bits := stage1_data
        symbol_sort.io.start := true.B
        
        when(symbol_sort.io.done && pipeline_advance) {
            stage2_data := symbol_sort.io.sorted_out.bits
            stage_valid(2) := true.B
            stage_valid(1) := false.B
        }
    }

    // 第3级：熵计算与自适应配置
    when(stage_valid(2)) {
        entropy_calc.io.freq_in := stage2_data
        entropy_calc.io.valid_in := true.B
        
        adaptive_ctrl.io.entropy_in := entropy_calc.io.entropy_out
        adaptive_ctrl.io.entropy_valid := entropy_calc.io.valid_out
        
        when(entropy_calc.io.valid_out && adaptive_ctrl.io.config_valid && pipeline_advance) {
            stage3_entropy := entropy_calc.io.entropy_out
            stage3_mode := adaptive_ctrl.io.encoding_mode
            stage_valid(3) := true.B
            stage_valid(2) := false.B
        }
    }

    // 第4级：树构建
    when(stage_valid(3)) {
        when(stage3_mode =/= 0.U) {
            tree_builder.io.freq_in := stage2_data
            tree_builder.io.entropy_config.encoding_mode := stage3_mode
            tree_builder.io.start := true.B
            
            when(tree_builder.io.done && pipeline_advance) {
                stage4_codes := tree_builder.io.code_out
                stage4_lengths := tree_builder.io.length_out
                stage_valid(4) := true.B
                stage_valid(3) := false.B
            }
        }.otherwise {
            // 旁路模式直接推进
            stage_valid(4) := true.B
            stage_valid(3) := false.B
        }
    }

    // 第5级：编码输出
    when(stage_valid(4)) {
        when(stage3_mode === 0.U) {
            // 旁路模式
            io.encoded_out.valid := io.data_in.valid
            io.encoded_out.bits.code := io.data_in.bits
            io.encoded_out.bits.length := symWidth.U
            io.encoded_out.bits.compression_mode := 0.U
            io.data_in.ready := io.encoded_out.ready
        }.otherwise {
            // Huffman编码模式
            mem_wrapper.io.read_addr := io.data_in.bits
            
            io.encoded_out.valid := io.data_in.valid && mem_wrapper.io.read_valid
            io.encoded_out.bits.code := mem_wrapper.io.read_code
            io.encoded_out.bits.length := mem_wrapper.io.read_length
            io.encoded_out.bits.compression_mode := stage3_mode
            io.data_in.ready := io.encoded_out.ready
        }
        
        when(io.encoded_out.fire) {
            encoded_bits := encoded_bits + io.encoded_out.bits.length
        }
    }

    // 简化的主控制状态机
    switch(state) {
        is(sIdle) {
            when(io.start) {
                state := sRunning
                stage_valid(0) := true.B
                symbol_count := 0.U
                encoded_bits := 0.U
            }
        }
        
        is(sRunning) {
            // 流水线运行中，统计符号数量
            when(io.data_in.fire) {
                symbol_count := symbol_count + 1.U
            }
            
            // 检查结束条件
            when(!io.start && !stage_valid.asUInt.orR) {
                state := sDone
            }
        }
        
        is(sDone) {
            io.done := true.B
            // 计算压缩比等统计信息
            val theoretical_bits = symbol_count * symWidth.U
            io.compression_ratio := (encoded_bits << 8) / theoretical_bits
            
            when(!io.start) {
                state := sIdle
            }
        }
    }

    // 为所有级添加flush处理
    when(io.flush) {
        // 清空所有流水线级的valid信号
        for(i <- 0 until 5) {
            stage_valid(i) := false.B
        }
        
        // 向所有子模块传播flush信号
        symbol_stat.io.flush := true.B
        symbol_sort.io.flush := true.B
        entropy_calc.io.flush := true.B
        adaptive_ctrl.io.flush := true.B  
        tree_builder.io.flush := true.B
        mem_wrapper.io.flush := true.B
        
        // 重置流水线寄存器
        stage1_data := VecInit(Seq.fill(depth)(0.U(wtWidth.W)))
        stage2_data := VecInit(Seq.fill(depth)(0.U(wtWidth.W)))
        stage3_entropy := 0.U
        stage3_mode := 0.U
        
        // 重置状态机到idle
        state := sIdle
    }
}
