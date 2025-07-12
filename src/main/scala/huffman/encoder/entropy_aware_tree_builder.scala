package huffman.encoder

import chisel3._
import chisel3.util._

/**
 * 熵感知霍夫曼树构建器
 * 集成基础树构建、深度调整和质量分析
 */
class EntropyAwareTreeBuilder(val depth: Int, val wtWidth: Int) extends Module {
    val io = IO(new Bundle {
        // 输入频率数据
        val freq_in = Input(Vec(depth, UInt(wtWidth.W)))
        
        // 熵感知配置
        val entropy_config = Input(new Bundle {
            val encoding_mode = UInt(2.W)
            val tree_depth_limit = UInt(log2Ceil(depth).W)
            val min_freq_threshold = UInt(wtWidth.W)
            val config_valid = Bool()
        })
        
        val start = Input(Bool())
        val done = Output(Bool())
        
        // 输出霍夫曼码表
        val code_out = Output(Vec(depth, UInt(16.W)))
        val length_out = Output(Vec(depth, UInt(6.W)))
        
        // 质量反馈
        val tree_efficiency = Output(UInt(16.W))
        val avg_code_length = Output(UInt(16.W))
    })

    // 内部模块实例化
    val basic_tree_builder = Module(new tree_build(depth, log2Ceil(depth*2), wtWidth))
    val tree_adjuster = Module(new tree_adjust(depth, 16)) // 最大深度16
    val quality_analyzer = Module(new TreeQualityAnalyzer(depth, wtWidth))
    
    // 状态机
    val sIdle :: sFilterFreq :: sBuildTree :: sAdjustDepth :: sAnalyzeQuality :: sGenerateCodes :: sDone :: Nil = Enum(7)
    val state = RegInit(sIdle)
    
    // 过滤后的频率数据
    val filtered_freq = RegInit(VecInit(Seq.fill(depth)(0.U(wtWidth.W))))

    val efficiency_threshold = (0.8 * (1 << 16)).toInt.U  // 80% 效率阈值
    
    // 连接逻辑
    switch(state) {
        is(sIdle) {
            when(io.start && io.entropy_config.config_valid) {
                state := sFilterFreq
            }
        }
        
        is(sFilterFreq) {
            // 根据熵配置过滤低频符号
            for(i <- 0 until depth) {
                filtered_freq(i) := Mux(
                    io.freq_in(i) >= io.entropy_config.min_freq_threshold,
                    io.freq_in(i),
                    0.U
                )
            }
            state := sBuildTree
        }
        
        is(sBuildTree) {
            val sorted_freq = symbol_sort.io.sorted_out.bits
            basic_tree_builder.io.sorted_in.valid := true.B
            basic_tree_builder.io.sorted_in.bits := filtered_freq
            basic_tree_builder.io.start := true.B
            
            when(basic_tree_builder.io.done) {
                state := sAdjustDepth
            }
        }
        
        is(sAdjustDepth) {
            // 根据熵配置进行深度调整
            tree_adjuster.io.start := true.B
            // 从基础树构建器获取深度直方图
            tree_adjuster.io.hist_in := basic_tree_builder.io.depth_histogram
            
            when(tree_adjuster.io.done) {
                state := sAnalyzeQuality
            }
        }
        
        is(sAnalyzeQuality) {
            // 质量分析
            quality_analyzer.io.freq_in := filtered_freq
            quality_analyzer.io.valid := true.B
            
            when(quality_analyzer.io.efficiency > efficiency_threshold) {
                state := sGenerateCodes
            }.otherwise {
                // 质量不达标，重新调整参数
                state := sAdjustDepth
            }
        }
        
        is(sGenerateCodes) {
            // 生成最终码表
            state := sDone
        }
        
        is(sDone) {
            io.done := true.B
            when(!io.start) {
                state := sIdle
            }
        }
    }
}
