package huffman.encoder

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class SymbolSortTester extends AnyFlatSpec with ChiselScalatestTester {
    behavior of "SymbolSort"

    it should "correctly sort the frequency vector in descending order" in {
        val depth = 16
        val wtWidth = 8
        test(new SymbolSort(depth, wtWidth)) { c =>
            // Prepare input data: unsorted frequencies
            val inputs = Seq(3,1,7,0,5,2,6,4,9,8,15,12,10,11,14,13)
            val expected = inputs.sorted(Ordering[Int].reverse)

            // Initialize and start
            c.io.flush.poke(false.B)
            c.io.start.poke(true.B)
            c.clock.step()
            c.io.start.poke(false.B)

            // Provide freq_in
            c.io.freq_in.valid.poke(true.B)
            for (i <- 0 until depth) {
                c.io.freq_in.bits(i).poke(inputs(i).U)
            }
            // wait for ready
            while (!c.io.freq_in.ready.peek().litToBoolean) {
                c.clock.step()
            }
            c.clock.step()
            c.io.freq_in.valid.poke(false.B)

            // Wait until done signal is high
            // Worst-case cycles: 2 * depth
            var cycles = 0
            while (!c.io.done.peek().litToBoolean && cycles < 2 * depth + 2) {
                c.clock.step()
                cycles += 1
            }
            assert(c.io.done.peek().litToBoolean, "SymbolSort did not assert done in expected time")

            // Check sorted_out
            for (i <- 0 until depth) {
                val v = c.io.sorted_out(i).peek().litValue.toInt
                assert(v == expected(i), s"sorted_out($i) = $v, expected ${expected(i)}")
            }
        }
    }

    it should "reset internal state when flush is asserted" in {
        val depth = 16
        val wtWidth = 8
        test(new SymbolSort(depth, wtWidth)) { c =>
            // Start a sort job
            val inputs1 = Seq(3,1,7,0,5,2,6,4,9,8,15,12,10,11,14,13)
            c.io.start.poke(true.B)
            c.clock.step()
            c.io.start.poke(false.B)
            c.io.freq_in.valid.poke(true.B)
            for (i <- 0 until depth) c.io.freq_in.bits(i).poke(inputs1(i).U)
            while (!c.io.freq_in.ready.peek().litToBoolean) { c.clock.step() }
            c.clock.step()
            
            // Issue flush mid-sorting
            c.io.flush.poke(true.B)
            c.clock.step()
            c.io.flush.poke(false.B)

            // Start new sort with known data: all zeros
            c.io.start.poke(true.B)
            c.clock.step()
            c.io.start.poke(false.B)
            c.io.freq_in.valid.poke(true.B)
            val inputs2 = Seq.fill(depth)(0)
            for (i <- 0 until depth) c.io.freq_in.bits(i).poke(0.U)
            while (!c.io.freq_in.ready.peek().litToBoolean) { c.clock.step() }
            c.clock.step()
            c.io.freq_in.valid.poke(false.B)

            // Wait for completion
            var cycles = 0
            while (!c.io.done.peek().litToBoolean && cycles < 2 * depth + 2) {
                c.clock.step()
                cycles += 1
            }
            assert(c.io.done.peek().litToBoolean)

            // After sorting zeros, sorted_out should all be zero
            for (i <- 0 until depth) {
                assert(c.io.sorted_out(i).peek().litValue == 0)
            }
        }
    }
}
