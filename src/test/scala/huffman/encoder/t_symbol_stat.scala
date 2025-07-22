package huffman.encoder

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
// import chiseltest.VerilatorBackendAnnotation
import huffman.encoder._

class SymbolStatTester extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "SymbolStat"

  it should "correctly count symbol frequencies and output them" in {
    test(new SymbolStat(sym_width = 4, wtWidth = 8, depth = 16))
    //   .withAnnotations(Seq(VerilatorBackendAnnotation)) 
      { c =>

        def resetAndStart(): Unit = {
          c.io.flush.poke(true.B)
          c.clock.step()
          c.io.flush.poke(false.B)
          c.io.start.poke(true.B)
          c.clock.step()
          c.io.start.poke(false.B)
        }

        // 模拟输入：输入序列为 3, 3, 5, 5, 5, 15
        val inputs = Seq(3, 3, 5, 5, 5, 15)

        resetAndStart()
        c.io.data_in.valid.poke(true.B)

        for (value <- inputs) {
            c.io.data_in.bits.poke(value.U)
            while (!c.io.data_in.ready.peek().litToBoolean) {
            c.clock.step()
            }
            c.clock.step()
        }

        c.io.data_in.valid.poke(false.B)
        c.io.freq_out.ready.poke(true.B)

        // 仅等待 1 周期，让状态从 sCount → sOutput 并 fire 一次
        c.clock.step()

        // 立即检查
        assert(c.io.freq_out.valid.peek().litToBoolean)
        val out = c.io.freq_out.bits.peek()
        assert(out(3).litValue == 2)
        assert(out(5).litValue == 3)
        assert(out(15).litValue == 1)
        // 其它位置应为 0
        for (i <- 0 until 16) {
            if (!Set(3, 5, 15).contains(i)) {
                assert(out(i).litValue == 0)
            }
        }
        // done 也会在同一周期被拉高
        assert(c.io.done.peek().litToBoolean)
      }
  }

  it should "reset internal state when flush is asserted" in {
    test(new SymbolStat(sym_width = 4, wtWidth = 8, depth = 16))
    //   .withAnnotations(Seq(VerilatorBackendAnnotation)) 
      { c =>
        // 模拟写入一次
        c.io.flush.poke(false.B)
        c.io.start.poke(true.B)
        c.clock.step()
        c.io.start.poke(false.B)
        c.io.data_in.valid.poke(true.B)
        c.io.data_in.bits.poke(1.U)
        while (!c.io.data_in.ready.peek().litToBoolean) {
          c.clock.step()
        }
        c.clock.step()

        // flush 触发清空
        c.io.flush.poke(true.B)
        c.clock.step()
        c.io.flush.poke(false.B)
        c.clock.step()

        // 再次输出频率，应该为 0
        c.io.start.poke(true.B)
        c.clock.step()
        c.io.start.poke(false.B)
        c.io.data_in.valid.poke(false.B)
        c.clock.step(5)

        assert(c.io.freq_out.valid.peek().litToBoolean)
        val out = c.io.freq_out.bits.peek()
        for (i <- 0 until 16) {
          assert(out(i).litValue == 0)
        }
      }
  }
}
