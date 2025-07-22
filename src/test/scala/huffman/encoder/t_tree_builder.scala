package huffman.encoder

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
// import chiseltest.VerilatorBackendAnnotation
import huffman.encoder._

class EntropyAwareTreeBuilderTester extends AnyFlatSpec with ChiselScalatestTester {
    behavior of "EntropyAwareTreeBuilder"

    it should "complete with zero output when all frequencies are below threshold" in {
        val depth = 4
        val wtWidth = 8
        test(new EntropyAwareTreeBuilder(depth, wtWidth)) { c =>
            // Set config: encoding_mode = 0, tree_depth_limit = 2, min_freq_threshold = 1
            c.io.entropy_config.encoding_mode.poke(0.U)
            c.io.entropy_config.tree_depth_limit.poke(2.U)
            c.io.entropy_config.min_freq_threshold.poke(1.U)

            // Input all zeros (below threshold)
            for (i <- 0 until depth) {
                c.io.freq_in(i).poke(0.U)
            }

            // Start
            c.io.flush.poke(false.B)
            c.io.start.poke(true.B)
            c.clock.step()
            c.io.start.poke(false.B)

            // Wait for done
            var cycles = 0
            while (!c.io.done.peek().litToBoolean) {
                c.clock.step()
                cycles += 1
            }
            assert(c.io.done.peek().litToBoolean, "Builder did not assert done")

            // Outputs should be zeros
            for (i <- 0 until depth) {
                assert(c.io.code_out(i).peek().litValue == 0, s"code_out($i) != 0")
                assert(c.io.length_out(i).peek().litValue == 0, s"length_out($i) != 0")
            }
        }
    }

    it should "respond to flush and restart properly" in {
        val depth = 4
        val wtWidth = 8
        test(new EntropyAwareTreeBuilder(depth, wtWidth)) { c =>
            // Set config
            c.io.entropy_config.encoding_mode.poke(1.U)
            c.io.entropy_config.tree_depth_limit.poke(3.U)
            c.io.entropy_config.min_freq_threshold.poke(0.U)

            // Provide a set of frequencies
            val inputs = Seq(5, 1, 3, 2)
            for (i <- 0 until depth) {
                c.io.freq_in(i).poke(inputs(i).U)
            }

            // Start first session
            c.io.flush.poke(false.B)
            c.io.start.poke(true.B)
            c.clock.step()
            c.io.start.poke(false.B)

            // Immediately flush mid-processing
            c.clock.step(2)
            c.io.flush.poke(true.B)
            c.clock.step()
            c.io.flush.poke(false.B)

            // Provide new input (all zeros)
            for (i <- 0 until depth) {
                c.io.freq_in(i).poke(0.U)
            }
            // Restart
            c.io.start.poke(true.B)
            c.clock.step()
            c.io.start.poke(false.B)

            // Wait for done
            var cycles = 0
            while (!c.io.done.peek().litToBoolean && cycles < 20) {
                c.clock.step()
                cycles += 1
            }
            assert(c.io.done.peek().litToBoolean, "Builder did not assert done after flush and restart")
        }
    }
}
