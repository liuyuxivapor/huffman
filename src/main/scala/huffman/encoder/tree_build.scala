package huffman.encoder

import chisel3._
import chisel3.util._

/**
  * HuffmanTreeBuilder:
  * 1. Load sorted frequency array of size N into nodes[0..N-1]
  * 2. Initialize valid mask for leaf nodes
  * 3. Iteratively merge two smallest valid nodes to form a new internal node:
  *    - nodes[k].weight = w_i + w_j
  *    - nodes[k].left = i, nodes[k].right = j
  *    - mark i,j invalid, mark k valid
  * 4. Continue until only one valid node remains (root)
  * Outputs root index and arrays of left/right children and weights
  */
class HuffmanTreeBuilder(val numLeaves: Int, val wtWidth: Int) extends Module {
    val maxNodes = 2 * numLeaves - 1
    val idxWidth = log2Ceil(maxNodes)
    val depthWidth = log2Ceil(numLeaves + 1)

    val io = IO(new Bundle {
        val start     = Input(Bool())
        val freqs     = Input(Vec(numLeaves, UInt(wtWidth.W)))  // sorted ascending
        val done      = Output(Bool())
        val root      = Output(UInt(idxWidth.W))
        // Expose tree structure
        val depths    = Output(Vec(numLeaves, UInt(depthWidth.W)))
        val weights   = Output(Vec(maxNodes, UInt(wtWidth.W)))
        val lefts     = Output(Vec(maxNodes, UInt(idxWidth.W)))
        val rights    = Output(Vec(maxNodes, UInt(idxWidth.W)))
    })

    // Node storage
    val weight = Reg(Vec(maxNodes, UInt(wtWidth.W)))
    val left   = Reg(Vec(maxNodes, UInt(idxWidth.W)))
    val right  = Reg(Vec(maxNodes, UInt(idxWidth.W)))
    val valid  = RegInit(VecInit(Seq.fill(maxNodes)(false.B)))
    val depthArr = RegInit(VecInit(Seq.fill(maxNodes)(0.U(depthWidth.W))))

    // State machine
    val sIdle :: sInit :: sMerge :: sDone :: Nil = Enum(4)
    val state = RegInit(sIdle)
    val nodeCnt = RegInit(0.U(idxWidth.W)) // next free index

    // Helpers: find two smallest valid nodes
    def findTwoMin(): (UInt, UInt) = {
        val maxVal = (1.U << wtWidth) - 1.U
        
        val maskedWeights = VecInit((0 until maxNodes).map(i => 
            Mux(valid(i), weight(i), maxVal)
        ))
        
        val min1Val = maskedWeights.reduce((a, b) => Mux(a <= b, a, b))
        val min1Matches = VecInit((0 until maxNodes).map(i => 
            maskedWeights(i) === min1Val && valid(i)
        ))
        val min1Idx = PriorityEncoder(min1Matches)
        
        // 排除第一个最小值后找第二小值
        val maskedWeights2 = VecInit((0 until maxNodes).map(i => 
            Mux(i.U === min1Idx || !valid(i), maxVal, weight(i))
        ))
        
        val min2Val = maskedWeights2.reduce((a, b) => Mux(a <= b, a, b))
        val min2Matches = VecInit((0 until maxNodes).map(i => 
            maskedWeights2(i) === min2Val && valid(i)
        ))
        val min2Idx = PriorityEncoder(min2Matches)
        
        (min1Idx, min2Idx)
    }


    // Default outputs
    io.done := false.B
    io.root := 0.U
    io.weights := weight
    io.lefts := left
    io.rights := right
    io.depths := depthArr.take(numLeaves)

    switch(state) {
        is(sIdle) {
            when(io.start) {
                state := sInit 
            }
        }

        is(sInit) {
        // load leaves
            for(i <- 0 until numLeaves) {
                weight(i) := io.freqs(i)
                left(i) := 0.U
                right(i) := 0.U
                valid(i) := true.B
                depthArr(i) := 0.U
            }
            // clear internal nodes
            for(i <- numLeaves until maxNodes) {
                weight(i) := 0.U
                left(i) := 0.U
                right(i) := 0.U
                valid(i) := false.B
                depthArr(i) := 0.U
            }
            nodeCnt := numLeaves.U
            state := sMerge
        }

        is(sMerge) {
            // merge until root
            when(nodeCnt < maxNodes.U) {
                val (i1, i2) = findTwoMin()
                val wsum = weight(i1) + weight(i2)
                // create new node
                weight(nodeCnt) := wsum
                left(nodeCnt) := i1
                right(nodeCnt) := i2
                depthArr(nodeCnt) := Mux(depthArr(i1) >= depthArr(i2), depthArr(i1), depthArr(i2)) + 1.U
                // update valid
                valid(i1) := false.B
                valid(i2) := false.B
                valid(nodeCnt) := true.B
                nodeCnt := nodeCnt + 1.U
            }.otherwise {
                // finished
                io.root := nodeCnt - 1.U
                io.done := true.B
                state := sDone
            }
        }

        is(sDone) {
            when(!io.start) {
                state := sIdle 
            }
        }
    }
}