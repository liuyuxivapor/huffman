package huffman.decoder

import chisel3._
import chisel3.util._

class ParrallelHuffmanDecoder(val symWidth: Int, val wtWidth: Int, val depth: Int, val maxCodeLen: Int) extends Module {
    val io = IO(new Bundle{

    })
}