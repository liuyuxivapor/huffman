error id: file://<WORKSPACE>/src/main/scala/huffman/encoder/tree_build.scala:`<none>`.
file://<WORKSPACE>/src/main/scala/huffman/encoder/tree_build.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -chisel3/Output.
	 -chisel3/Output#
	 -chisel3/Output().
	 -chisel3/util/Output.
	 -chisel3/util/Output#
	 -chisel3/util/Output().
	 -Output.
	 -Output#
	 -Output().
	 -scala/Predef.Output.
	 -scala/Predef.Output#
	 -scala/Predef.Output().
offset: 686
uri: file://<WORKSPACE>/src/main/scala/huffman/encoder/tree_build.scala
text:
```scala
package huffman_encoder

import chisel3._
import chisel3.util._

class tree_build(val depth: Int, val idxWidth: Int, val wtWidth: Int) extends Module {
    // Maximum number of nodes in Huffman tree (2*depth-1)
    val maxNodes = 2*depth-1
    val io = IO(new Bundle {
        // Sorted input weights with valid/ready handshake
        val sorted_in = Flipped(Decoupled(Vec(depth, UInt(wtWidth.W))))
        // Index of the root node in the Huffman tree
        val root_idx  = Output(UInt(idxWidth.W))
        // Start signal to initiate tree construction
        val start     = Input(Bool())
        // Done signal indicating tree construction completion
        val done      = Outp@@ut(Bool())
    })

    // Storage arrays for Huffman tree nodes
    // Weight values of each node, initialized to maximum value
    val weight   = RegInit(VecInit(Seq.fill(maxNodes)(UIntMax(wtWidth.W))))
    // Depth of each node in the tree
    val depthArr = RegInit(VecInit(Seq.fill(maxNodes)(0.U(log2Ceil(depth).W))))
    // Valid bit indicating if a node is active
    val valid    = RegInit(VecInit(Seq.fill(maxNodes)(false.B)))
    // Left child index of each node
    val left     = Reg(Vec(maxNodes, UInt(idxWidth.W)))
    // Right child index of each node
    val right    = Reg(Vec(maxNodes, UInt(idxWidth.W)))

    // State machine for Huffman tree construction
    val sIdle :: sLoad :: sMerge :: sDone :: Nil = Enum(4)
    val state = RegInit(sIdle)
    // Counter for total number of nodes created
    val nodeCnt = RegInit(0.U(log2Ceil(maxNodes+1).W))
    // Counter for merge operations performed
    val mergeCount = RegInit(0.U(log2Ceil(depth).W))

    // Find indices of the two minimum values in the array with valid mask
    def findTwoMinIndices(vals: Seq[UInt], mask: Seq[Bool]): (UInt, UInt) = {
        // Mask values with invalid entries set to maximum value
        val masked = vals.zip(mask).map{ case (v, m) => Mux(m, v, UIntMax(wtWidth.W)) }
        
        // Find the first minimum value and its index
        val min1Val = RegInit(UIntMax(wtWidth.W))
        val min1Idx = RegInit(0.U(idxWidth.W))
        for(i <- 0 until maxNodes) {
            when(masked(i) < min1Val) {
                min1Val := masked(i)
                min1Idx := i.U
            }
        }
        
        // Find the second minimum value and its index (excluding the first minimum)
        val min2Val = RegInit(UIntMax(wtWidth.W))
        val min2Idx = RegInit(0.U(idxWidth.W))
        for(i <- 0 until maxNodes) {
            when(masked(i) < min2Val && i.U =/= min1Idx) {
                min2Val := masked(i)
                min2Idx := i.U
            }
        }
        
        (min1Idx, min2Idx)
    }

    // Default output values
    io.done := false.B
    io.root_idx := 0.U
    io.sorted_in.ready := false.B

    switch(state) {
        is(sIdle) {
            // Wait for start signal to begin tree construction
            when(io.start) {
                // Reset all node states
                for(i <- 0 until maxNodes) {
                    valid(i) := false.B
                }
                nodeCnt := 0.U
                mergeCount := 0.U
                state := sLoad
            }
        }

        is(sLoad) {
            // Accept sorted input weights
            io.sorted_in.ready := true.B
            when(io.sorted_in.fire()) {
                // Initialize leaf nodes with input weights
                for(i <- 0 until depth) {
                    weight(i) := io.sorted_in.bits(i)
                    depthArr(i) := 0.U  // Leaf nodes have depth 0
                    valid(i) := true.B  // Mark leaf nodes as valid
                }
                nodeCnt := depth.U  // Set initial node count to number of leaves
                state := sMerge     // Transition to merge state
            }
        }

        is(sMerge) {
            // Continue merging until all internal nodes are created
            when(mergeCount < (depth-1).U) {
                // Find indices of two smallest valid nodes
                val (i1, i2) = findTwoMinIndices(weight, valid)
                val w1 = weight(i1)
                val w2 = weight(i2)
                
                // Create parent node with combined weight
                val parentIdx = nodeCnt
                weight(parentIdx) := w1 + w2
                
                // Determine parent node depth based on children's depth
                val d1 = depthArr(i1)
                val d2 = depthArr(i2)
                depthArr(parentIdx) := Mux(d1 >= d2, d1 + 1.U, d2 + 1.U)
                
                // Set left and right children for parent node
                left(parentIdx) := i1
                right(parentIdx) := i2
                
                // Invalidate children nodes and validate parent node
                valid(i1) := false.B
                valid(i2) := false.B
                valid(parentIdx) := true.B
                
                // Update counters
                nodeCnt := nodeCnt + 1.U
                mergeCount := mergeCount + 1.U
            } .otherwise {
                // Merging complete, set root index and transition to done state
                io.root_idx := nodeCnt - 1.U
                state := sDone
            }
        }

        is(sDone) {
            // Indicate completion and hold root index
            io.done := true.B
            io.root_idx := nodeCnt - 1.U
            // Return to idle state when start signal is de-asserted
            when(!io.start) {
                state := sIdle
            }
        }
    }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.