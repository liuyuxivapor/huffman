package huffman.encoder

import chisel3._
import chisel3.util._

class tree_adjust(val numLeaves: Int, val maxDepth: Int) extends Module {
    val maxD = maxDepth * 2
    val depthWidth = log2Ceil(maxD + 1)
    val io = IO(new Bundle {
        val depths_in  = Input(Vec(numLeaves, UInt(depthWidth.W)))
        val start      = Input(Bool())
        val depths_out = Output(Vec(numLeaves, UInt(depthWidth.W)))
        val done       = Output(Bool())
    })

    // Registers and counters
    val hist      = RegInit(VecInit(Seq.fill(maxD+1)(0.U(depthWidth.W))))
    val migrated  = RegInit(0.U(depthWidth.W))
    val K         = Reg(UInt(depthWidth.W))
    val holesLeft = Reg(UInt(depthWidth.W))
    val outDepth  = Reg(Vec(numLeaves, UInt(depthWidth.W)))
    val assignPtr = RegInit(0.U(depthWidth.W))

    val sIdle :: sBuildHist :: sMigrate :: sFillHoles :: sAssign :: sDone :: Nil = Enum(6)
    val state     = RegInit(sIdle)

    // Default
    io.depths_out := outDepth
    io.done := (state === sDone)

    val emptyL = (1.U << maxDepth) - hist(maxDepth)
    val cnts = Reg(Vec(maxD+1, UInt(depthWidth.W)))

    switch(state) {
        is(sIdle) {
            when(io.start) {
                state := sBuildHist
            }
        }

        is(sBuildHist) {
            for (d <- 0 to maxD) hist(d) := 0.U
            for (i <- 0 until numLeaves) {
                val d = io.depths_in(i)
                hist(d) := hist(d) + 1.U
            }
            val sumHigh = (0 to maxD).map { d =>
                Mux(d.U > maxDepth.U, hist(d), 0.U)
            }.reduce(_ + _)

            K := sumHigh >> 1
            migrated := 0.U
            state := sMigrate
        }

        is(sMigrate) {
            when(migrated < K) {
                // 标志是否本轮找到可迁移
                val found = WireInit(false.B)
                // 顺序遍历 d=1..L-1
                for (d <- 1 until maxDepth) {
                    when(!found && hist(d) > 0.U) {
                        // 迁移一个节点到 L
                        hist(d) := hist(d) - 1.U
                        hist(maxDepth) := hist(maxDepth) + 1.U
                        // 同时在 d-1 处产生两个“子空位”
                        hist(d-1) := hist(d-1) + 2.U
                        migrated := migrated + 1.U
                        found := true.B
                    }
                }
                // 迁移后检查空位
                when(emptyL >= K) {
                    // 第 L 级已有足够空位
                    holesLeft := emptyL
                    state := sFillHoles
                } .elsewhen(!found) {
                    // 如果找不到可迁移节点（所有 d 层都没节点），直接进入下一步
                    holesLeft := emptyL
                    state := sFillHoles
                }
            }.otherwise {
                // 已经迁移 K 次
                holesLeft := emptyL
                state := sFillHoles
            }
        }

        // Observation 3，填补 holesLeft 个空洞
        is(sFillHoles) {
            when(holesLeft > 0.U) {
                // 顺序从最深层向上找可迁移节点
                val found2 = WireInit(false.B)

                for (d <- (maxDepth+1 to maxD).reverse) {
                    when(!found2 && hist(d) > 0.U) {
                        // 将该节点迁移到 L，填洞
                        hist(d) := hist(d) - 1.U
                        hist(maxDepth) := hist(maxDepth) + 1.U
                        hist(d-1) := hist(d-1) + 2.U
                        holesLeft := holesLeft - 1.U
                        found2 := true.B
                    }
                }

                // 如果一轮下来未找到可迁移节点，也认为洞补完，直接进入 Assign
                when(!found2) {
                    state := sAssign
                    assignPtr := 0.U
                    for(d <- 0 to maxD) cnts(d) := hist(d)
                }
            } .otherwise {
                // 洞已全部填完
                state := sAssign
                assignPtr := 0.U
                for(d <- 0 to maxD) cnts(d) := hist(d)
            }
        }

        is(sAssign) {
            when(assignPtr < numLeaves.U) {
                val sel = WireInit(0.U(depthWidth.W))
                for(d <- 0 to maxD) {
                    when(cnts(d) > 0.U && sel === 0.U) { 
                        sel := d.U 
                    }
                }
                outDepth(assignPtr) := sel
                cnts(sel) := cnts(sel) - 1.U
                assignPtr := assignPtr + 1.U
            } .otherwise {
                state := sDone
            }
        }

        is(sDone) {
            when(!io.start) {
                state := sIdle
            }
        }
    }
}
