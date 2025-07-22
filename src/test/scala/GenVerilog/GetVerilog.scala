import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import chiseltest.VerilatorBackendAnnotation
import chisel3.stage.ChiselGeneratorAnnotation
import circt.stage.{ChiselStage, FirtoolOption}
import huffman.encoder._

object main extends App {
    (new ChiselStage).execute(
      Array("--target", "verilog", "--target-dir", "verilog/dut"),
      Seq(ChiselGeneratorAnnotation(() => new EntropyAwareHuffmanSystem(256, 32, 8, 16)),
      FirtoolOption("--disable-all-randomization"),
      FirtoolOption("-strip-debug-info")
      )
    )
}
