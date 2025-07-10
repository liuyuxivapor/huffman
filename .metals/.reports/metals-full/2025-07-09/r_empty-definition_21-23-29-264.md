error id: file://<WORKSPACE>/src/main/scala/huffman/encoder/mem_wrapper.scala:`<none>`.
file://<WORKSPACE>/src/main/scala/huffman/encoder/mem_wrapper.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -chisel3/code_mem.
	 -chisel3/util/code_mem.
	 -code_mem.
	 -scala/Predef.code_mem.
offset: 2447
uri: file://<WORKSPACE>/src/main/scala/huffman/encoder/mem_wrapper.scala
text:
```scala
package huffman_encoder

import chisel3._
import chisel3.util._

/**
 * Memory Wrapper for Huffman Code Tables
 * Manages storage and retrieval of Huffman codes and lengths
 */
class MemWrapper(val depth: Int, val maxCodeLen: Int, val addrWidth: Int) extends Module {
    val io = IO(new Bundle {
        // Write interface (from tree builder)
        val write_en = Input(Bool())
        val write_addr = Input(UInt(addrWidth.W))
        val write_code = Input(UInt(maxCodeLen.W))
        val write_length = Input(UInt(log2Ceil(maxCodeLen+1).W))
        
        // Read interface (to encoder)
        val read_addr = Input(UInt(addrWidth.W))
        val read_code = Output(UInt(maxCodeLen.W))
        val read_length = Output(UInt(log2Ceil(maxCodeLen+1).W))
        val read_valid = Output(Bool())
        
        // Bulk read interface (for encoder initialization)
        val bulk_read_en = Input(Bool())
        val code_table = Output(Vec(depth, UInt(maxCodeLen.W)))
        val length_table = Output(Vec(depth, UInt(log2Ceil(maxCodeLen+1).W)))
        val table_ready = Output(Bool())
    })

    // Memory arrays for codes and lengths
    val code_mem = SyncReadMem(depth, UInt(maxCodeLen.W))
    val length_mem = SyncReadMem(depth, UInt(log2Ceil(maxCodeLen+1).W))
    
    // Valid bits to track which entries have been written
    val valid_bits = RegInit(VecInit(Seq.fill(depth)(false.B)))
    
    // Write logic
    when(io.write_en) {
        code_mem.write(io.write_addr, io.write_code)
        length_mem.write(io.write_addr, io.write_length)
        valid_bits(io.write_addr) := true.B
    }
    
    // Single read logic
    val read_code_reg = RegNext(code_mem.read(io.read_addr))
    val read_length_reg = RegNext(length_mem.read(io.read_addr))
    val read_valid_reg = RegNext(valid_bits(io.read_addr))
    
    io.read_code := read_code_reg
    io.read_length := read_length_reg
    io.read_valid := read_valid_reg
    
    // Bulk read logic for encoder table initialization
    val bulk_read_state = RegInit(0.U(2.W))
    val read_counter = RegInit(0.U(log2Ceil(depth).W))
    val code_table_reg = Reg(Vec(depth, UInt(maxCodeLen.W)))
    val length_table_reg = Reg(Vec(depth, UInt(log2Ceil(maxCodeLen+1).W)))
    
    when(io.bulk_read_en && bulk_read_state === 0.U) {
        bulk_read_state := 1.U
        read_counter := 0.U
    }
    
    when(bulk_read_state === 1.U) {
        code_table_reg(read_counter) := code_me@@m.read(read_counter)
        length_table_reg(read_counter) := length_mem.read(read_counter)
        read_counter := read_counter + 1.U
        
        when(read_counter === (depth-1).U) {
            bulk_read_state := 2.U
        }
    }
    
    io.code_table := code_table_reg
    io.length_table := length_table_reg
    io.table_ready := (bulk_read_state === 2.U) && valid_bits.reduce(_ && _)
    
    // Reset bulk read when disabled
    when(!io.bulk_read_en) {
        bulk_read_state := 0.U
    }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.