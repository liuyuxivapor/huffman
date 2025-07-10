package huffman_encoder

import chisel3._
import chisel3.util._

/**
  * tree_adjust implements the 3-step depth limiting algorithm:
  * L: maximum allowed depth
  * numLeaves: number of leaves (symbols)
  * Steps:
  * 1) Compute K = (N(L+1)+...+N(maxD))/2, where N(d) is node count at level d
  * 2) Migrate nodes from levels 1..L-1 down to level L until emptySlots(L) >= K
  * 3) Move K nodes to level L; then apply Observation?3 to eliminate any remaining holes
  */

class tree_adjust(val numLeaves: Int, val maxDepth: Int) extends Module {
    val lvlWidth = log2Ceil(numLeaves*2)
    val io = IO(new Bundle {
        val hist_in   = Input(Vec(maxDepth*2+1, UInt(lvlWidth.W))) // N(d) for d=0..maxD
        val start     = Input(Bool())
        val hist_out  = Output(Vec(maxDepth*2+1, UInt(lvlWidth.W))) // adjusted N(d)
        val done      = Output(Bool())
    })

    // Registers
    val hist      = RegInit(VecInit(Seq.fill(maxDepth*2+1)(0.U(lvlWidth.W))))
    val emptySlots= Wire(UInt(lvlWidth.W))
    val K         = Wire(UInt(lvlWidth.W))
    val step      = RegInit(0.U(3.W))     // 0=Step1, 1=Step2, 2=Step3.1, 3=Step3.2, 4=Done
    val currentD  = RegInit(1.U(log2Ceil(maxDepth+1).W))
    val nodesMoved= RegInit(0.U(lvlWidth.W))
    val kMoved    = RegInit(0.U(lvlWidth.W))
    val holes     = Reg(Vec(maxDepth*2+1, UInt(lvlWidth.W))) // ÿ��Ŀն���

    // ���������������ض���ȵĿղ���
    def emptySlotsAtDepth(d: UInt): UInt = {
        (1.U << d) - hist(d)
    }

    // ���������������ض���ȵĿն���
    def computeHoles(d: UInt): UInt = {
        val maxNodes = 1.U << d
        Mux(d === 0.U, 0.U, maxNodes - hist(d) - holes(d + 1.U))
    }

    // ���߼�
    emptySlots := emptySlotsAtDepth(maxDepth.U)
    
    // ����1������Kֵ
    val sumHigh = hist.zipWithIndex.map{ case (v,d) => 
        Mux(d.U > maxDepth.U, v, 0.U) 
    }.reduce(_+_)
    K := sumHigh >> 1.U

    // ��ʼ���ն���
    for(d <- 0 until maxDepth) {
        holes(d) := 0.U
    }

    // Ĭ�����
    io.hist_out := hist
    io.done := (step === 4.U)

    switch(step) {
        is(0.U) { // ����1������ֱ��ͼ������K
            when(io.start) {
                for(d <- 0 until hist.length) hist(d) := io.hist_in(d)
                step := 1.U
            }
        }
        
        is(1.U) { // ����2�������1��L-1Ǩ�ƽڵ㵽L
            when(emptySlots < K) {
                when(currentD < maxDepth.U) {
                    // ���Դӵ�ǰ���Ǩ�ƽڵ�
                    when(hist(currentD) > 0.U) {
                        // Ǩ��һ���ڵ�
                        hist(currentD)         := hist(currentD) - 1.U
                        hist(maxDepth)         := hist(maxDepth) + 1.U
                        // ���¸��ڵ���ȵĿղ���
                        when(currentD > 0.U) {
                            hist(currentD - 1.U) := hist(currentD - 1.U) + 2.U
                        }
                    }
                    // �ƶ�����һ�����
                    currentD := currentD + 1.U
                }.otherwise {
                    // ���һ��Ǩ�ƣ�����currentD
                    currentD := 1.U
                }
            }.otherwise {
                // �ղ����㹻�����벽��3
                step := 2.U
                currentD := (maxDepth-1).U
                kMoved := 0.U
            }
        }
        
        is(2.U) { // ����3.1���ƶ�K���ڵ㵽L��
            when(kMoved < K) {
                // �ҵ�����ķǿղ㼶����L-1��ʼ���£�
                var found = false
                for(d <- (maxDepth-1) to 0 by -1) {
                    if(!found && d > 0) {
                        when(currentD === d.U && hist(d) > 0.U) {
                            // Ǩ�ƽڵ�
                            hist(d)            := hist(d) - 1.U
                            hist(maxDepth)     := hist(maxDepth) + 1.U
                            hist(d - 1)        := hist(d - 1) + 2.U
                            kMoved             := kMoved + 1.U
                            currentD           := (maxDepth-1).U
                            found              = true
                        }
                    }
                }
                
                // ���û���ҵ���Ǩ�ƵĽڵ㣬�����Ѿ���������в㼶
                when(!found || currentD === 0.U) {
                    step := 3.U // û�и���ڵ��Ǩ�ƣ����벽��3.2
                    currentD := 0.U // �����0��ʼ����ն�
                }.otherwise {
                    currentD := currentD - 1.U // ���������һ�����
                }
            }.otherwise {
                // K���ڵ���Ǩ�ƣ����벽��3.2�����ն�
                step := 3.U
                currentD := 0.U // �����0��ʼ����ն�
            }
        }
        
        is(3.U) { // ����3.2��Ӧ��Observation 3����ʣ��ն�
            when(currentD < maxDepth.U) {
                // ���㵱ǰ��ȵĿն���
                holes(currentD) := computeHoles(currentD)
                
                // ����ն������ԴӸ����Ǩ�ƽڵ���ն�
                when(holes(currentD) > 0.U && currentD < (maxDepth-1).U) {
                    // ���Ҹ����Ľڵ�
                    var found = false
                    for(d <- (maxDepth-1) to (currentD.toInt+1) by -1) {
                        if(!found) {
                            when(hist(d) > 0.U) {
                                // Ǩ�ƽڵ�����ն�
                                hist(d)            := hist(d) - 1.U
                                hist(currentD)     := hist(currentD) + 1.U
                                hist(d - 1)        := hist(d - 1) + 2.U
                                holes(currentD)    := holes(currentD) - 1.U
                                found              = true
                            }
                        }
                    }
                }
                
                currentD := currentD + 1.U
            }.otherwise {
                // ������ȴ�����ϣ���ɵ���
                step := 4.U
            }
        }
        
        is(4.U) { // ���״̬
            when(!io.start) {
                step := 0.U // �ȴ���һ������
            }
        }
    }
}    