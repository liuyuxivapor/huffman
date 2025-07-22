package huffman.encoder

import chisel3._
import chisel3.util._

class EntropyAwareHuffmanSystem(
    val symWidth: Int,
    val wtWidth: Int,
    val depth: Int,
    val maxCodeLen: Int
) extends Module {
    val io = IO(new Bundle {
        // 输入数据流
        val data_in = Flipped(Decoupled(UInt(symWidth.W)))

        // 编码输出流
        val encoded_out = Decoupled(new Bundle {
        val code = UInt(maxCodeLen.W)
        val length = UInt(log2Ceil(maxCodeLen + 1).W)
        val compression_mode = UInt(1.W)
        })

        // 控制信号
        val start = Input(Bool())
        val done = Output(Bool())
        val flush = Input(Bool())
    })

    // 子模块实例化
    val symbolStat = Module(new SymbolStat(symWidth, wtWidth, depth))
    val symbolSort = Module(new SymbolSort(depth, wtWidth))
    val entropyCalc = Module(new ShannonEntropy(depth, wtWidth))
    val treeBuilder = Module(new EntropyAwareTreeBuilder(depth, wtWidth))

    // 流水线阶段有效信号（控制每个阶段是否活跃） Ready for in, Valid for out
    val pipeValid = RegInit(VecInit(Seq.fill(4)(false.B)))
    val pipeReady = RegInit(VecInit(Seq.fill(4)(false.B)))

    // 管道寄存器（用于阶段间数据传递）
    val freqReg1 = Reg(Vec(depth, UInt(wtWidth.W)))
    val freqReg2 = Reg(Vec(depth, UInt(wtWidth.W)))
    val modeReg3 = Reg(UInt(2.W))
    val codeReg4 = Reg(Vec(depth, UInt(maxCodeLen.W)))
    val lenReg4 = Reg(Vec(depth, UInt(log2Ceil(maxCodeLen + 1).W)))

    // 各阶段就绪信号
    pipeValid(0) := symbolStat.io.done
    pipeValid(1) := symbolSort.io.done
    pipeValid(2) := entropyCalc.io.done
    pipeValid(3) := treeBuilder.io.done

    // === 子模块接口连接 ===

    // Stage 0: SymbolStat
    symbolStat.io.data_in <> io.data_in
    symbolStat.io.start := pipeReady(0)
    symbolStat.io.flush := io.flush

    // Stage 1: SymbolSort
    symbolSort.io.freq_in.bits := freqReg1
    symbolSort.io.freq_in.valid := symbolStat.io.freq_out.valid
    symbolStat.io.freq_out.ready := symbolSort.io.freq_in.ready
    symbolSort.io.start := pipeReady(1)
    symbolSort.io.flush := io.flush

    // Stage 2: Entropy Calculation
    entropyCalc.io.freq_in := freqReg2
    entropyCalc.io.start := pipeReady(2)
    entropyCalc.io.flush := io.flush

    // Stage 3: Tree Builder
    treeBuilder.io.freq_in := freqReg2
    treeBuilder.io.entropy_config.encoding_mode := Mux(
        pipeReady(3) && modeReg3 =/= 0.U,
        modeReg3,
        0.U
    )
    treeBuilder.io.entropy_config.tree_depth_limit := Mux(
        pipeReady(3) && modeReg3 =/= 0.U,
        log2Ceil(depth).U,
        0.U
    )
    treeBuilder.io.entropy_config.min_freq_threshold := Mux(
        pipeReady(3) && modeReg3 =/= 0.U,
        (0.5 * (1 << 16)).toInt.U,
        0.U
    )
    treeBuilder.io.start := pipeReady(3) && modeReg3 =/= 0.U
    treeBuilder.io.flush := io.flush

    // === 流水线控制逻辑 ===

    // Stage 0 使能条件
    pipeReady(0) := io.start && io.data_in.valid

    // Stage 1 转移
    freqReg1 := symbolStat.io.freq_out.bits
    when(pipeReady(0)) {
        when(pipeReady(0)) {
        pipeValid(1) := true.B
        pipeValid(0) := false.B
        }
    }

    // Stage 2 转移
    freqReg2 := symbolSort.io.sorted_out
    when(pipeReady(1)) {
        when(pipeReady(1)) {
        pipeValid(2) := true.B
        pipeValid(1) := false.B
        }
    }

    // Stage 3 转移
    modeReg3 := entropyCalc.io.compression_mode
    when(pipeReady(2)) {
        when(pipeReady(2)) {
        pipeValid(3) := true.B
        pipeValid(2) := false.B
        }
    }

    // Stage 4 转移
    when(pipeValid(3)) {
        when(modeReg3 =/= 0.U) {
        when(pipeReady(3)) {
            codeReg4 := treeBuilder.io.code_out
            lenReg4 := treeBuilder.io.length_out
            pipeValid(3) := false.B
        }
        }.otherwise {
        // Bypass
        for (i <- 0 until depth) {
            codeReg4(i) := i.U
            lenReg4(i) := symWidth.U
        }
        pipeValid(3) := false.B
        }
    }

    // === 输出逻辑 ===

    val encodedValid = RegInit(false.B)
    val encodedCode = Reg(UInt(maxCodeLen.W))
    val encodedLen = Reg(UInt(log2Ceil(maxCodeLen + 1).W))
    val encodedMode = Reg(UInt(1.W))

    when(io.data_in.valid && !io.flush) {
        encodedValid := true.B
        when(modeReg3 === 0.U) {
        encodedCode := io.data_in.bits
        encodedLen := symWidth.U
        encodedMode := 0.U
        }.otherwise {
        val addr = io.data_in.bits
        encodedCode := codeReg4(addr)
        encodedLen := lenReg4(addr)
        encodedMode := modeReg3(0)
        }
    }.otherwise {
        encodedValid := false.B
    }

    // 输出接口连接
    io.encoded_out.valid := encodedValid
    io.encoded_out.bits.code := encodedCode
    io.encoded_out.bits.length := encodedLen
    io.encoded_out.bits.compression_mode := encodedMode

    // 反压处理
    io.data_in.ready := pipeValid(0) && io.encoded_out.ready
    encodedValid := encodedValid && !io.encoded_out.ready

    // 完成信号
    io.done := !pipeValid.asUInt.orR

    // 清空信号
    when(io.flush) {
        for (i <- 0 until 4) {
        pipeValid(i) := false.B
        }
        encodedValid := false.B
    }
}
