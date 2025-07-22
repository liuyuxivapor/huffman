package huffman.encoder

import chisel3._
import chisel3.util._

/**
 * MIN4_2: 从 4 个输入并行选出最小 mn0 和次小 mn1，余下两个通过 rem 输出。
 */
class MIN4_2(val wtWidth: Int) extends Module {
  val io = IO(new Bundle {
    val Din = Input(Vec(4, UInt(wtWidth.W)))
    val mn0 = Output(UInt(wtWidth.W))
    val mn1 = Output(UInt(wtWidth.W))
    val rem = Output(Vec(2, UInt(wtWidth.W)))
  })

  val a = io.Din(0); val b = io.Din(1)
  val c = io.Din(2); val d = io.Din(3)
  val m0 = Mux(a < b, a, b)
  val o0 = Mux(a < b, b, a)
  val m1 = Mux(c < d, c, d)
  val o1 = Mux(c < d, d, c)
  val mn0_comb = Mux(m0 < m1, m0, m1)
  val t = Mux(m0 < m1, m1, m0)
  val m2 = Mux(t < o0, t, o0)
  val mn1_comb = Mux(m2 < o1, m2, o1)
  val max_o = Mux(o0 > o1, o0, o1)
  val min_o = Mux(o0 > o1, o1, o0)
  val rem0 = max_o
  val rem1 = Mux(t > min_o, t, min_o)

  val mn0_r = RegNext(mn0_comb)
  val mn1_r = RegNext(mn1_comb)
  val rem_r = RegNext(VecInit(rem0, rem1))

  io.mn0 := mn0_r
  io.mn1 := mn1_r
  io.rem := rem_r
}

/**
 * MIN6_2: 从 6 个输入并行选出最小 mn0、次小 mn1，并输出剩余 4 个 rem。
 * 结构：stageA0 和 stageA1 各自选出 2，小值用 stageB 汇总。
 */
class MIN6_2(val wtWidth: Int) extends Module {
  val io = IO(new Bundle {
    val Din = Input(Vec(6, UInt(wtWidth.W)))
    val mn0 = Output(UInt(wtWidth.W))
    val mn1 = Output(UInt(wtWidth.W))
    val rem = Output(Vec(4, UInt(wtWidth.W)))
  })

  val stageA0 = Module(new MIN4_2(wtWidth))
  val stageA1 = Module(new MIN4_2(wtWidth))
  stageA0.io.Din := io.Din.slice(0, 4)
  stageA1.io.Din := io.Din.slice(2, 6)

  val stageB = Module(new MIN4_2(wtWidth))
  stageB.io.Din := VecInit(
    stageA0.io.mn0, stageA0.io.mn1,
    stageA1.io.mn0, stageA1.io.mn1
  )

  val mn0_r = RegNext(stageB.io.mn0)
  val mn1_r = RegNext(stageB.io.mn1)
  val remA = VecInit(
    stageA0.io.rem(0), stageA0.io.rem(1),
    stageA1.io.rem(0), stageA1.io.rem(1)
  )
  val rem_r = RegNext(remA)

  io.mn0 := mn0_r
  io.mn1 := mn1_r
  io.rem := rem_r
}

/**
 * SymbolSort: 并行分选网络
 * 每周期：取 6 字符 -> MIN6_2 -> 输出两个最小，余下 4 写回队列
 * 重复 depth/2 周期完成 descending 排序。
 */
class SymbolSort(val depth: Int, val wtWidth: Int) extends Module {
  require(depth >= 2, "depth must be >=2")
  val io = IO(new Bundle {
    val freq_in    = Flipped(Decoupled(Vec(depth, UInt(wtWidth.W))))
    val start      = Input(Bool())
    val flush      = Input(Bool())
    val sorted_out = Output(Vec(depth, UInt(wtWidth.W)))
    val done       = Output(Bool())
  })

  // States: Idle -> Load -> Sort -> Done
  val sIdle :: sLoad :: sSort :: sDone :: Nil = Enum(4)
  val state = RegInit(sIdle)
  val iterCnt = RegInit(0.U(log2Ceil((depth*2)+1).W))

  // Data register
  val dataReg = Reg(Vec(depth, UInt(wtWidth.W)))

  io.sorted_out := dataReg
  io.done := (state === sDone)

  // Control ready/valid for loading
  io.freq_in.ready := (state === sLoad)

  // Flush resets
  when(io.flush) {
    state := sIdle
    iterCnt := 0.U
  }

  switch(state) {
    is(sIdle) {
      when(io.start) {
        state := sLoad
      }
    }
    is(sLoad) {
      when(io.freq_in.valid) {
        dataReg := io.freq_in.bits
        iterCnt := 0.U
        state := sSort
      }
    }
    is(sSort) {
      val phase = ~iterCnt(0)
      for(i <- 0 until depth-1) {
        when((i.U(log2Ceil(depth).W) % 2.U) === phase) {
          val a = dataReg(i)
          val b = dataReg(i+1)
          // descending: swap if a < b
          when(a < b) {
            dataReg(i) := b
            dataReg(i+1) := a
          }
        }
      }
      iterCnt := iterCnt + 1.U
      when(iterCnt === (depth*2 - 1).U) {
        state := sDone
      }
    }
    is(sDone) {
      // one-cycle done then back to idle
      state := sIdle
    }
  }
}
