error id: file://<WORKSPACE>/src/main/scala/Fundamental_IC.scala:`<none>`.
file://<WORKSPACE>/src/main/scala/Fundamental_IC.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -chisel3/PrintWriter.
	 -chisel3/PrintWriter#
	 -chisel3/PrintWriter().
	 -java/io/PrintWriter.
	 -java/io/PrintWriter#
	 -java/io/PrintWriter().
	 -PrintWriter.
	 -PrintWriter#
	 -PrintWriter().
	 -scala/Predef.PrintWriter.
	 -scala/Predef.PrintWriter#
	 -scala/Predef.PrintWriter().
offset: 205
uri: file://<WORKSPACE>/src/main/scala/Fundamental_IC.scala
text:
```scala
package Fundamental_IC
import chisel3._
import chisel3.stage.ChiselGeneratorAnnotation
import chisel3.util.{Cat, ShiftRegister, is, log2Ceil, log2Floor}
import circt.stage.ChiselStage

import java.io.Print@@Writer
import scala.collection.mutable
import scala.language.postfixOps

class wire_connection(bw:Int) extends Module{
  val io=IO(new Bundle {
    val in_a = Input(UInt(bw.W))
    val out_b = Output(UInt(bw.W))
  })
  val w1 = Wire(UInt(bw.W))
  val w2 = Wire(Vec(2, UInt(bw.W)))
  w1 := 7.U(bw.W) & io.in_a
  w2(0) := 3.U(bw.W) | io.in_a
  w2(1) := 5.U(bw.W) & w1
  io.out_b := w2(0) & w2(1)
}

class adder(bw:Int) extends Module{
  val io=IO(new Bundle {
    val in_a = Input(UInt(bw.W))
    val in_b = Input(UInt(bw.W))
    val out_s = Output(UInt((bw+1).W))
  })
  io.out_s := io.in_a + io.in_b
}

class parallel_adder(n: Int, bw: Int) extends Module {
  require(bw == 16 || bw == 32 || bw == 64 || bw == 128)
  val io = IO(new Bundle {
    val in_a = Input(Vec(n, UInt(bw.W)))
    val in_b = Input(Vec(n, UInt(bw.W)))
    val out_s = Output(Vec(n, UInt(bw.W)))
  })
  //val adders = Vector.fill(n)(Module(new adder(bw)).io)
  //adders.zipWithIndex.map(x=>x._1.in_a := io.in_a(x._2))
  //adders.zipWithIndex.map(x=>x._1.in_b := io.in_b(x._2))
  //adders.zipWithIndex.map(x=>io.out_s(x._2) := x._1.out_s)
  val register_layer = RegInit(VecInit.fill(n)(0.U(bw.W)))
  val adder_layer = for (i <-0 until n) yield {
    val adder = Module(new adder(bw)).io
    adder.in_a := io.in_a(i)
    adder.in_b := io.in_b(i)
    register_layer(i) := adder.out_s
    io.out_s(i) := register_layer(i)
  }
}

class register_file(n: Int, bw: Int) extends Module {
  val io = IO(new Bundle {
    val in_d = Input(Vec(n, UInt(bw.W)))
    val out_q = Output(Vec(n, UInt(bw.W)))
  })
  val register_file = RegInit(VecInit.fill(n)(0.U(bw.W)))
  val register_layer = for (i <-0 until n) yield {
    register_file(i) := io.in_d(i)
    io.out_q(i) := register_file(i)
  }
}

class register(bw: Int) extends Module {
  val io = IO(new Bundle {
    val in_d = Input(UInt(bw.W))
    val out_q = Output(UInt(bw.W))
  })
  val register = RegInit(0.U(bw.W))
    register := io.in_d
    io.out_q := register
}

class counter(bw:Int) extends Module{
  val io=IO(new Bundle {
    val in_en = Input(UInt(1.W))
    val out_cnt = Output(UInt(bw.W))
  })
  val cnt = RegInit(0.U(bw.W))
  val nxt_cnt = Wire(UInt(bw.W))
  nxt_cnt := Mux(io.in_en===1.U, Mux(cnt===9.U, 0.U, cnt+1.U), cnt)
  cnt := nxt_cnt
  io.out_cnt := cnt
}

class mux(bw:Int) extends Module {
  val io = IO(new Bundle {
    val in_sel = Input(UInt(1.W))
    val in_a   = Input(UInt(bw.W))
    val in_b   = Input(UInt(bw.W))
    val out_c   = Output(UInt(bw.W))
  })
  when (io.in_sel === 1.U) {
    io.out_c := io.in_a
  }.otherwise {
    io.out_c := io.in_b
  }
}

//if-else cannot be used for constructing hardware
class mux_ifelse(bw:Int) extends Module {
  val io = IO(new Bundle {
    val in_sel = Input(UInt(1.W))
    val in_a   = Input(UInt(bw.W))
    val in_b   = Input(UInt(bw.W))
    val out_c   = Output(UInt(bw.W))
  })
  if (io.in_sel==1.U) {
    io.out_c := io.in_a
  } else {
    io.out_c := io.in_b
  }
}

class syn_ram(bw: Int, depth: Int) extends Module {
  val io = IO {
    new Bundle() {
      val ena = Input(Bool())
      val enb = Input(Bool())
      val wea = Input(UInt((bw/8).W))
      val addra = Input(UInt((log2Ceil(depth).W)))
      val addrb = Input(UInt((log2Ceil(depth).W)))
      val dina = Input(UInt(bw.W))
      val doutb = Output(UInt(bw.W))
    }
  }
  val mem = RegInit(VecInit.fill(depth)(VecInit.fill(bw / 8)(0.U(8.W))))
    when(io.ena) {
      for (i <- 0 until bw / 8) {
        when(io.wea(i)) {
          mem(io.addra)(i) := io.dina(8 * (i + 1) - 1, 8 * i)
        }
      }
    }

  val data_out = RegInit(0.U(bw.W))
    when(io.enb) {
      data_out := Cat(mem(io.addrb).reverse)
    }
  io.doutb := data_out
}

class asyn_ram(bw: Int, depth: Int) extends Module {
  val io = IO(new Bundle {
    val clka = Input(Clock()) // Write clock
    val clkb = Input(Clock()) // Read clock
    val ena = Input(Bool()) // Enable write
    val enb = Input(Bool()) // Enable read
    val wea = Input(Vec(bw / 8, Bool())) // Write enable mask (one bit per byte)
    val addra = Input(UInt(log2Ceil(depth).W)) // Write address
    val addrb = Input(UInt(log2Ceil(depth).W)) // Read address
    val dina = Input(UInt(bw.W)) // Write data
    val doutb = Output(UInt(bw.W)) // Read data
  })

  // Memory Array (depth x bw bits wide)
  val mem = SyncReadMem(depth, UInt(bw.W)) // Memory for storing `bw`-bit wide words

  // Write logic on clka with byte-level write enable mask
  withClock(io.clka) {
    val data_in = WireDefault(0.U(bw.W))
    when(io.ena) {
      // Byte-wise write masking
      for (i <- 0 until bw / 8) {
        when(io.wea(i)) { // Only write the byte if the corresponding bit in wea is set
          data_in := io.dina(8 * (i + 1) - 1, 8 * i)
        }
      }
      mem.write(io.addra, data_in) // Write back the modified word
    }
  }

  // Read logic on clkb
  withClock(io.clkb) {
    val data_out = Wire(UInt(bw.W))
    when(io.enb) {
      data_out := mem.read(io.addrb) // Read from memory on clkb
    }.otherwise {
      data_out := 0.U // Output zero when not enabled
    }
    io.doutb := data_out
  }
}

class shift_register(depth: Int,bw: Int) extends Module{
  val io = IO(new Bundle() {
    val in = Input(UInt(bw.W))
    val out = Output(UInt(bw.W))
  })
  val reg = RegInit(VecInit.fill(depth)(0.U(bw.W)))
    reg(0) := io.in
    for(i <- 1 until depth){
      reg(i) := reg(i-1)
    }
  io.out := reg(depth - 1)
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.