package huffman_encoder

import chisel3._
import chisel3.util._

/**
  * tree_adjust implements the 3-step depth limiting algorithm:
  * L: maximum allowed depth
  * numLeaves: number of leaves (symbols)
  * Steps:
  * 1) Compute K = (N(L+1)+...+N(maxD))/2, where N(d) is node count at level d
  * 2) Migrate nodes from levels 1..L-1 down to level L until emptySlots(L) >= K
  * 3) Move K nodes to level L; then apply Observation?3 to eliminate any remaining holes
  */

class tree_adjust(val numLeaves: Int, val maxDepth: Int) extends Module {
    val lvlWidth = log2Ceil(numLeaves*2)
    val io = IO(new Bundle {
        val hist_in   = Input(Vec(maxDepth*2+1, UInt(lvlWidth.W))) // N(d) for d=0..maxD
        val start     = Input(Bool())
        val hist_out  = Output(Vec(maxDepth*2+1, UInt(lvlWidth.W))) // adjusted N(d)
        val done      = Output(Bool())
    })

    // Registers
    val hist      = RegInit(VecInit(Seq.fill(maxDepth*2+1)(0.U(lvlWidth.W))))
    val emptySlots= Wire(UInt(lvlWidth.W))
    val K         = Wire(UInt(lvlWidth.W))
    val step      = RegInit(0.U(3.W))     // 0=Step1, 1=Step2, 2=Step3.1, 3=Step3.2, 4=Done
    val currentD  = RegInit(1.U(log2Ceil(maxDepth+1).W))
    val nodesMoved= RegInit(0.U(lvlWidth.W))
    val kMoved    = RegInit(0.U(lvlWidth.W))
    val holes     = Reg(Vec(maxDepth*2+1, UInt(lvlWidth.W))) // 每层的空洞数

    // 辅助函数：计算特定深度的空槽数
    def emptySlotsAtDepth(d: UInt): UInt = {
        (1.U << d) - hist(d)
    }

    // 辅助函数：计算特定深度的空洞数
    def computeHoles(d: UInt): UInt = {
        val maxNodes = 1.U << d
        Mux(d === 0.U, 0.U, maxNodes - hist(d) - holes(d + 1.U))
    }

    // 主逻辑
    emptySlots := emptySlotsAtDepth(maxDepth.U)
    
    // 步骤1：计算K值
    val sumHigh = hist.zipWithIndex.map{ case (v,d) => 
        Mux(d.U > maxDepth.U, v, 0.U) 
    }.reduce(_+_)
    K := sumHigh >> 1.U

    // 初始化空洞数
    for(d <- 0 until maxDepth) {
        holes(d) := 0.U
    }

    // 默认输出
    io.hist_out := hist
    io.done := (step === 4.U)

    switch(step) {
        is(0.U) { // 步骤1：加载直方图并计算K
            when(io.start) {
                for(d <- 0 until hist.length) hist(d) := io.hist_in(d)
                step := 1.U
            }
        }
        
        is(1.U) { // 步骤2：从深度1到L-1迁移节点到L
            when(emptySlots < K) {
                when(currentD < maxDepth.U) {
                    // 尝试从当前深度迁移节点
                    when(hist(currentD) > 0.U) {
                        // 迁移一个节点
                        hist(currentD)         := hist(currentD) - 1.U
                        hist(maxDepth)         := hist(maxDepth) + 1.U
                        // 更新父节点深度的空槽数
                        when(currentD > 0.U) {
                            hist(currentD - 1.U) := hist(currentD - 1.U) + 2.U
                        }
                    }
                    // 移动到下一个深度
                    currentD := currentD + 1.U
                }.otherwise {
                    // 完成一轮迁移，重置currentD
                    currentD := 1.U
                }
            }.otherwise {
                // 空槽数足够，进入步骤3
                step := 2.U
                currentD := (maxDepth-1).U
                kMoved := 0.U
            }
        }
        
        is(2.U) { // 步骤3.1：移动K个节点到L层
            when(kMoved < K) {
                // 找到最深的非空层级（从L-1开始向下）
                var found = false
                for(d <- (maxDepth-1) to 0 by -1) {
                    if(!found && d > 0) {
                        when(currentD === d.U && hist(d) > 0.U) {
                            // 迁移节点
                            hist(d)            := hist(d) - 1.U
                            hist(maxDepth)     := hist(maxDepth) + 1.U
                            hist(d - 1)        := hist(d - 1) + 2.U
                            kMoved             := kMoved + 1.U
                            currentD           := (maxDepth-1).U
                            found              = true
                        }
                    }
                }
                
                // 如果没有找到可迁移的节点，或者已经检查完所有层级
                when(!found || currentD === 0.U) {
                    step := 3.U // 没有更多节点可迁移，进入步骤3.2
                    currentD := 0.U // 从深度0开始处理空洞
                }.otherwise {
                    currentD := currentD - 1.U // 继续检查下一个深度
                }
            }.otherwise {
                // K个节点已迁移，进入步骤3.2消除空洞
                step := 3.U
                currentD := 0.U // 从深度0开始处理空洞
            }
        }
        
        is(3.U) { // 步骤3.2：应用Observation 3消除剩余空洞
            when(currentD < maxDepth.U) {
                // 计算当前深度的空洞数
                holes(currentD) := computeHoles(currentD)
                
                // 处理空洞：尝试从更深层迁移节点填补空洞
                when(holes(currentD) > 0.U && currentD < (maxDepth-1).U) {
                    // 查找更深层的节点
                    var found = false
                    for(d <- (maxDepth-1) to (currentD.toInt+1) by -1) {
                        if(!found) {
                            when(hist(d) > 0.U) {
                                // 迁移节点以填补空洞
                                hist(d)            := hist(d) - 1.U
                                hist(currentD)     := hist(currentD) + 1.U
                                hist(d - 1)        := hist(d - 1) + 2.U
                                holes(currentD)    := holes(currentD) - 1.U
                                found              = true
                            }
                        }
                    }
                }
                
                currentD := currentD + 1.U
            }.otherwise {
                // 所有深度处理完毕，完成调整
                step := 4.U
            }
        }
        
        is(4.U) { // 完成状态
            when(!io.start) {
                step := 0.U // 等待下一次启动
            }
        }
    }
}    