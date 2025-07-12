package huffman.encoder

import chisel3._
import chisel3.util._

/**
  * tree_adjust implements the 3-step depth limiting algorithm:
  * L: maximum allowed depth
  * numLeaves: number of leaves (symbols)
  * Steps:
  * 1) Compute K = (N(L+1)+...+N(maxD))/2, where N(d) is node count at level d
  * 2) Migrate nodes from levels 1..L-1 down to level L until emptySlots(L) >= K
  * 3) Move K nodes to level L; then apply Observation 3 to eliminate any remaining holes
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
    val holes     = Reg(Vec(maxDepth*2+1, UInt(lvlWidth.W)))

    /** Calculate empty slots at specified depth */
    def emptySlotsAtDepth(d: UInt): UInt = {
        (1.U << d) - hist(d)
    }

    /** Calculate number of holes at specified depth */
    def computeHoles(d: UInt): UInt = {
        val maxNodes = 1.U << d
        Mux(d === 0.U, 0.U, maxNodes - hist(d) - holes(d + 1.U))
    }

    // Main logic signals
    emptySlots := emptySlotsAtDepth(maxDepth.U)
    
    // Step 1: Compute K value
    val sumHigh = hist.zipWithIndex.map{ case (v,d) => 
        Mux(d.U > maxDepth.U, v, 0.U) 
    }.reduce(_+_)
    K := sumHigh >> 1.U

    // Initialize holes array
    for(d <- 0 until maxDepth) {
        holes(d) := 0.U
    }

    // Default outputs
    io.hist_out := hist
    io.done := (step === 4.U)

    switch(step) {
        is(0.U) { // Step 1: Load histogram and initialize for K calculation
            when(io.start) {
                for(d <- 0 until hist.length) hist(d) := io.hist_in(d)
                step := 1.U
            }
        }
        
        is(1.U) { // Step 2: Migrate nodes from levels 1 to L-1 down to level L
            when(emptySlots < K) {
                when(currentD < maxDepth.U) {
                    // Attempt to migrate node from current depth
                    when(hist(currentD) > 0.U) {
                        // Migrate one node
                        hist(currentD)         := hist(currentD) - 1.U
                        hist(maxDepth)         := hist(maxDepth) + 1.U
                        // Update empty slots at parent depth
                        when(currentD > 0.U) {
                            hist(currentD - 1.U) := hist(currentD - 1.U) + 2.U
                        }
                    }
                    // Move to next depth level
                    currentD := currentD + 1.U
                }.otherwise {
                    // Complete one migration pass, reset currentD
                    currentD := 1.U
                }
            }.otherwise {
                // Sufficient empty slots available, proceed to Step 3
                step := 2.U
                currentD := (maxDepth-1).U
                kMoved := 0.U
            }
        }
        
        is(2.U) { // Step 3.1: Move exactly K nodes to level L
            when(kMoved < K) {
                // 使用寄存器代替var变量
                val searchComplete = RegInit(false.B)
                val nodeFound = RegInit(false.B)
                
                when(!searchComplete) {
                    when(currentD > 0.U && hist(currentD) > 0.U) {
                        // 执行节点迁移
                        hist(currentD) := hist(currentD) - 1.U
                        hist(maxDepth) := hist(maxDepth) + 1.U
                        hist(currentD - 1.U) := hist(currentD - 1.U) + 2.U
                        kMoved := kMoved + 1.U
                        currentD := (maxDepth-1).U
                        nodeFound := true.B
                    }.elsewhen(currentD === 0.U) {
                        searchComplete := true.B
                    }.otherwise {
                        currentD := currentD - 1.U
                    }
                }
                
                when(searchComplete || nodeFound) {
                    when(kMoved >= K) {
                        step := 3.U
                        currentD := 0.U
                    }.otherwise {
                        searchComplete := false.B
                        nodeFound := false.B
                        currentD := (maxDepth-1).U
                    }
                }
            }.otherwise {
                step := 3.U
                currentD := 0.U
            }
        }

        is(3.U) { // Step 3.2: Apply Observation 3 to eliminate remaining holes
            when(currentD < maxDepth.U) {
                holes(currentD) := computeHoles(currentD)
                
                // 使用状态寄存器处理搜索，避免循环
                val fillComplete = RegInit(false.B)
                val searchDepth = RegInit((maxDepth-1).U)
                
                when(holes(currentD) > 0.U && currentD < (maxDepth-1).U && !fillComplete) {
                    when(searchDepth > currentD && hist(searchDepth) > 0.U) {
                        // 迁移节点填补空洞
                        hist(searchDepth) := hist(searchDepth) - 1.U
                        hist(currentD) := hist(currentD) + 1.U
                        hist(searchDepth - 1.U) := hist(searchDepth - 1.U) + 2.U
                        holes(currentD) := holes(currentD) - 1.U
                        fillComplete := true.B
                    }.elsewhen(searchDepth <= currentD) {
                        fillComplete := true.B
                    }.otherwise {
                        searchDepth := searchDepth - 1.U
                    }
                }.otherwise {
                    fillComplete := true.B
                }
                
                when(fillComplete) {
                    currentD := currentD + 1.U
                    fillComplete := false.B
                    searchDepth := (maxDepth-1).U
                }
            }.otherwise {
                step := 4.U
            }
        }
        
        is(4.U) { // Completion state
            when(!io.start) {
                step := 0.U // Wait for next start signal
            }
        }
    }
}