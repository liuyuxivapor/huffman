import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import chiseltest.WriteVcdAnnotation
import chiseltest.VerilatorBackendAnnotation
import chisel3.stage.ChiselGeneratorAnnotation
import circt.stage.{ChiselStage, FirtoolOption}
import Fundamental_IC._

object main extends App {
    (new ChiselStage).execute(
      Array("--target", "systemverilog", "--target-dir", "verification/dut"),
      Seq(ChiselGeneratorAnnotation(() => new adder(32)),
      FirtoolOption("--disable-all-randomization"),
      FirtoolOption("-strip-debug-info")
      )
    )
}
