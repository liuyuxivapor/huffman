package huffman.encoder

import chisel3._
import chisel3.util._

class EntropyAwareTreeBuilder(val depth: Int, val wtWidth: Int) extends Module {
    val io = IO(new Bundle {
        val freq_in = Input(Vec(depth, UInt(wtWidth.W)))
        val entropy_config = Input(new Bundle {
            val encoding_mode    = UInt(1.W)
            val tree_depth_limit = UInt(log2Ceil(depth).W)
            val min_freq_threshold = UInt(wtWidth.W)
        })
        val start = Input(Bool())
        val flush = Input(Bool())
        val done = Output(Bool())
        val code_out = Output(Vec(depth, UInt(16.W)))
        val length_out = Output(Vec(depth, UInt(6.W)))
    })

    val builder = Module(new HuffmanTreeBuilder(depth, wtWidth))
    // val adjust  = Module(new tree_adjust(depth, io.entropy_config.tree_depth_limit.litValue.toInt))
    val adjust  = Module(new tree_adjust(depth, 16)) //手动限制16

    // val filtered = Reg(Vec(depth, UInt(wtWidth.W)))
    // val depthsIn = Reg(Vec(depth, UInt(adjust.depthWidth.W)))
    // val depthsOut= Reg(Vec(depth, UInt(adjust.depthWidth.W)))

    val filtered   = Reg(Vec(depth, UInt(wtWidth.W)))
    val depthsIn   = Reg(Vec(depth, UInt(adjust.depthWidth.W)))
    val depthsInReg= RegNext(depthsIn)  // pipeline stage
    val codes    = Reg(Vec(depth, UInt(16.W)))
    val lengths  = Reg(Vec(depth, UInt(6.W)))

    val count      = Wire(Vec(17, UInt(adjust.depthWidth.W)))
    val codeStart  = Wire(Vec(17, UInt(16.W)))
    val countReg   = RegNext(count)
    val codeStartReg = RegNext(codeStart)

    val sIdle :: sFilter :: sBuild :: sAdjust :: sGenPrep :: sGenExec :: sDone :: Nil = Enum(7)
    val state = RegInit(sIdle)

    io.done := state === sDone
    io.code_out := codes
    io.length_out := lengths
    builder.io.start := false.B
    builder.io.freqs := filtered
    adjust.io.start := false.B
    adjust.io.depths_in := depthsIn

    count     := VecInit(Seq.fill(17)(0.U))
    codeStart := VecInit(Seq.fill(17)(0.U))

    switch(state) {
        is(sIdle) { 
            when(io.start) { 
                state := sFilter 
            } 
        }

        is(sFilter) {
            for(i <- 0 until depth) {
                filtered(i) := Mux(io.freq_in(i) >= io.entropy_config.min_freq_threshold, io.freq_in(i), 0.U)
            }
            state := sBuild
        }

        is(sBuild) {
            when(io.start) { 
                builder.io.start := true.B 
            }
            when(builder.io.done) {
                depthsIn := builder.io.depths
                state := sAdjust
            }
        }

        is(sAdjust) {
            when(io.start) { 
                adjust.io.start := true.B 
            }
            when(adjust.io.done) {
                depthsIn := adjust.io.depths_out
                state := sGenPrep
            }
        }

        // is(sGen) {
        //     // canonical Huffman code generation
        //     // val maxL = io.entropy_config.tree_depth_limit
        //     val maxL = 16
        //     val wlen = adjust.depthWidth
        //     val count = Wire(Vec(maxL+1, UInt(wlen.W)))
        //     for(d <- 0 to maxL) count(d) := 0.U
        //     for(i <- 0 until depth) count(depthsOut(i)) := count(depthsOut(i)) + 1.U

        //     val codeStart = Wire(Vec(maxL+1, UInt(16.W)))
        //     // 这里放一个错误的写法做警示，下面的写法循环展开会有FIRRTL错误
        //     // var code = 0.U(16.W)
        //     // for(d <- 0 to maxL) {
        //     //     codeStart(d) := code
        //     //     code = (code + count(d)) << 1
        //     // }

        //     val nextCode = RegInit(VecInit(Seq.fill(maxL+1)(0.U(16.W))))
        //     for(d <- 0 to maxL) nextCode(d) := codeStart(d)
        //     for(i <- 0 until depth) {
        //         lengths(i) := depthsOut(i)
        //         codes(i) := nextCode(depthsOut(i))
        //         nextCode(depthsOut(i)) := nextCode(depthsOut(i)) + 1.U
        //     }
        //     state := sDone
        // }

        // --- Stage A: 计算 count 与 codeStart，然后打一拍 ---
        is(sGenPrep) {
            // 1. 直接对 depthsInReg 做“直方图”计数，**不** 使用自加
            for (d <- 0 until 17) {
                // 对所有 i，如果 depthsInReg(i) == d 就 +1
                val hits = depthsInReg.map(_ === d.U).map(_.asUInt).reduce(_ +& _)
                count(d) := hits
            }
            // 2. 计算 codeStart：纯组合，不引用 count 自身
            codeStart(0) := 0.U
            for (d <- 1 until 17) {
                codeStart(d) := (codeStart(d-1) + count(d-1)) << 1
            }
            state := sGenExec
        }
        // --- Stage B: 从寄存器中取出 countReg/codeStartReg/depthsInReg 生成 codes/lengths ---
        is(sGenExec) {
            // 1. 构造前缀和数组 prefixSum，其长度是 L+2
            val prefixSum = Wire(Vec(17+1, UInt(16.W))) // 0..17 共18个
            prefixSum(0) := 0.U
            for (d <- 0 to 16) {
                prefixSum(d+1) := prefixSum(d) + countReg(d)
            }

            // 2. 最终生成 codes 和 lengths
            for (i <- 0 until depth) {
                val lvl = depthsInReg(i)             // 该符号的码长
                lengths(i) := lvl
                // 偏移量就是前面级别的总和
                val offset = prefixSum(lvl)         // 动态索引 prefixSum
                codes(i) := codeStartReg(lvl) + offset
            }
            state := sDone
        }

        is(sDone) {
            when(!io.start) { 
                state := sIdle 
            }
        }
    }

    when(io.flush) { 
        state := sIdle 
    }
}