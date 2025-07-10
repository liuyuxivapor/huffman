error id: file://<WORKSPACE>/src/test/scala/GenVerilog/GetVerilog.scala:`<none>`.
file://<WORKSPACE>/src/test/scala/GenVerilog/GetVerilog.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -chisel3/ChiselStage.
	 -chisel3/ChiselStage#
	 -chisel3/ChiselStage().
	 -chiseltest/ChiselStage.
	 -chiseltest/ChiselStage#
	 -chiseltest/ChiselStage().
	 -circt/stage/ChiselStage.
	 -circt/stage/ChiselStage#
	 -circt/stage/ChiselStage().
	 -Fundamental_IC.ChiselStage.
	 -Fundamental_IC.ChiselStage#
	 -Fundamental_IC.ChiselStage().
	 -ChiselStage.
	 -ChiselStage#
	 -ChiselStage().
	 -scala/Predef.ChiselStage.
	 -scala/Predef.ChiselStage#
	 -scala/Predef.ChiselStage().
offset: 237
uri: file://<WORKSPACE>/src/test/scala/GenVerilog/GetVerilog.scala
text:
```scala
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import chiseltest.WriteVcdAnnotation
import chiseltest.VerilatorBackendAnnotation
import chisel3.stage.ChiselGeneratorAnnotation
import circt.stage.{ChiselSta@@ge, FirtoolOption}
import Fundamental_IC._

object main extends App {
    (new ChiselStage).execute(
      Array("--target", "systemverilog", "--target-dir", "verification/dut"),
      Seq(ChiselGeneratorAnnotation(() => new parallel_adder(4,32)),
      FirtoolOption("--disable-all-randomization"),
      FirtoolOption("-strip-debug-info")
      )
    )
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.