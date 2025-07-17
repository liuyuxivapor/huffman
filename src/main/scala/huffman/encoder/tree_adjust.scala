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
            // 找出第一个 d in [1, maxDepth-1] 且 hist(d)>0 的层级
            val validMask = VecInit((1 until maxDepth).map(d => hist(d) > 0.U))
            val canMigrate = validMask.asUInt.orR
            val validMaskOH = validMask.asUInt
            val selOH = PriorityEncoderOH(validMaskOH)
            val sel = OHToUInt(selOH) + 1.U

            when(migrated < K && canMigrate) {
                // 在 sel 处迁移
                hist(sel)        := hist(sel) - 1.U
                hist(maxDepth)   := hist(maxDepth) + 1.U
                hist(sel - 1.U)  := hist(sel - 1.U) + 2.U
                migrated         := migrated + 1.U
            }

            // 迁移结束或无可迁移节点时进入 FillHoles
            when(migrated >= K || !canMigrate) {
                holesLeft := emptyL
                state     := sFillHoles
            }
        }

        // Observation 3，填补 holesLeft 个空洞
        is(sFillHoles) {
            // 从最深层往上找节点来“填洞”
            val validMask2 = VecInit(((maxDepth+1) to maxD).reverse.map(d => hist(d) > 0.U))
            val canFill    = validMask2.asUInt.orR
            val sel2       = PriorityEncoder(validMask2) // sel2=0->层=maxD, sel2=1->maxD-1, etc.
            val level      = (maxD.U - sel2)

            when(holesLeft > 0.U && canFill) {
                hist(level)      := hist(level) - 1.U
                hist(maxDepth)   := hist(maxDepth) + 1.U
                hist(level - 1.U):= hist(level - 1.U) + 2.U
                holesLeft        := holesLeft - 1.U
            }.otherwise {
                // 洞填完后，准备 Assign
                assignPtr := 0.U
                for(d <- 0 to maxD) cnts(d) := hist(d)
                state := sAssign
            }
        }

        is(sAssign) {
            when(assignPtr < numLeaves.U) {
                val mask = VecInit(cnts.map(_ > 0.U))
                val sel  = PriorityEncoder(mask)

                outDepth(assignPtr) := sel
                cnts(sel)           := cnts(sel) - 1.U
                assignPtr           := assignPtr + 1.U
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
