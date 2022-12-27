/*
Two sporadic tasks arrive each with signal startA, and startB.
The controller is supposed to schedule these using two machines. Each machine has an internal state,
and the scheduling `time' depends on this. Some states requires 2 ticks, others 3 ticks.
Each tick represents an external call with nondet. duration. At each tick, the internal states of the machines change.
The controller loses (error=1) if all machines are occupied upon the arrival of a new task.

Internal states of the two machines are modeled by a single genbuf model.
*/
`define nb1 1
`define nb2 2
module sched(input clk, input controllable_sched0, input controllable_sched1, 
                        input startA, input startB,
                        input tick,
                        output error,
                        output _rt_startA, output _rt_startB, 
                        output _rt_tick,
        input i_StoB_REQ0,
        input controllable_BtoS_ACK0,
        input i_StoB_REQ1,
        input controllable_BtoS_ACK1,
        input i_StoB_REQ2,
        input controllable_BtoS_ACK2,
        input i_RtoB_ACK0,
        input controllable_BtoR_REQ0,
        input i_RtoB_ACK1,
        input controllable_BtoR_REQ1,
        input i_FULL,
        input i_nEMPTY,
        input controllable_ENQ,
        input controllable_DEQ,
        input controllable_SLC0, input controllable_SLC1);
    reg[2:0] m0_occupied; // 0: idle, 1: taskA, 2: taskB
    reg[2:0] m0_counter;
    reg[2:0] m1_occupied;
    reg[2:0] m1_counter;
    reg notfirst;
    reg reg_error;

    wire pout0;
    wire pout1;
    // reg update0;
    // reg update1;

    wire dummyerr;
    genbuf_3_new_4 p(clk,
        i_StoB_REQ0,
        controllable_BtoS_ACK0,
        i_StoB_REQ1,
        controllable_BtoS_ACK1,
        i_StoB_REQ2,
        controllable_BtoS_ACK2,
        i_RtoB_ACK0,
        controllable_BtoR_REQ0,
        i_RtoB_ACK1,
        controllable_BtoR_REQ1,
        i_FULL,
        i_nEMPTY,
        controllable_ENQ,
        controllable_DEQ,
        controllable_SLC0,
        controllable_SLC1, 
        pout0, pout1, dummyerr);

    // genbuf_3_new_4 p(
    //     clk,
    //     startA, //i_StoB_REQ0,
    //     controllable_sched0, //controllable_BtoS_ACK0,
    //     tick, // i_RtoB_ACK0,
    //     controllable_sched1, // controllable_BtoR_REQ0,
    //     startB, // i_RtoB_ACK1,
    //     1'b1, //controllable_BtoR_REQ1,
    //     ~tick, // i_FULL,
    //     ~tick, //i_nEMPTY,
    //     tick,
    //     tick,
    //     tick,
    //     startA,
    //     startB,
    //     startA,
    //     startB,
    //     ~tick,
    //     pout0, pout1,
    //     dummyerr
    // );

    assign error = reg_error;

    assign _rt_startA = notfirst && ~error && startA;
    assign _rt_startB = notfirst && ~error && ~_rt_startA && startB;
    assign _rt_tick = notfirst &&~_rt_startA && ~_rt_startB && tick;
    // assign _rt_endA = notfirst && ~error && ~_rt_startA && ~_rt_startB && endA;
    // assign _rt_endB = notfirst && ~error && ~_rt_startA && ~_rt_startB && !_rt_endA && endB;
    // assign _rt_tick = notfirst &&~_rt_startA && ~_rt_startB && !_rt_endA && !_rt_endB && tick;

    initial begin
        notfirst = 0;
        m0_counter = 0;
        m0_occupied = 0;
        m1_counter = 0;
        m1_occupied = 0;
        // update0 = 0;
        // update1 = 0;
        reg_error = 0;
    end
    always @(posedge clk) begin
        if (notfirst) begin
            // update0 <= 0;
            // update1 <= 0;
            if (_rt_startA ) begin
                if (controllable_sched0) begin
                    m0_occupied <= 1;
                    m0_counter <= 0;
                end else if (controllable_sched1) begin

                    m1_occupied <= 1;
                    m1_counter <= 0;
                end
            end
            if (_rt_startB ) begin
                if (controllable_sched0) begin
                    m0_occupied <= 2;
                    m0_counter <= 0;
                end else if (controllable_sched1) begin
                    m1_occupied <= 2;
                    m1_counter <= 0;
                end
            end

            if( _rt_tick )begin
                // if (m0_occupied>0)
                //     update0 <=  1;
                // if (m1_occupied>0)
                //     update1 <=  1;

                // m0 is occupied by A or B
                if (m0_occupied > 0 && (pout0 && m0_counter < `nb1 || ~pout0 && m0_counter < `nb2)) begin
                // not yet finished
                    m0_counter <= m0_counter + 1;
                end else if (m0_occupied > 0 && (pout0 && m0_counter >= `nb1 || ~pout0 && m0_counter >= `nb2)) begin
                // finished
                    m0_counter <= 0;
                    m0_occupied <= 0;
                end

                // m1 is occupied by A or B
                if (m1_occupied > 0 && (pout1 && m1_counter < `nb1 || ~pout1 && m1_counter < `nb2)) begin
                // not yet finished
                    m1_counter <= m1_counter + 1;
                end else if (m1_occupied > 0 && (pout1 && m1_counter >= `nb1 || ~pout1 && m1_counter >= `nb2)) begin
                // finished
                    m1_counter <= 0;
                    m1_occupied <= 0;
                end
            end
            reg_error <= reg_error || (
                (controllable_sched0 && m0_occupied>0 || controllable_sched1 && m1_occupied>0)
                || _rt_startA && ~controllable_sched0 && ~controllable_sched1
                || _rt_startB && ~controllable_sched0 && ~controllable_sched1
            );
        end
        notfirst <= 1;
    end
endmodule
// module process(input clk, input update, input[0:1] bit, output outstate);
//     reg[4:0] state;
//     initial state =0;
//     assign outstate = state[bit];
//     always @(posedge clk)begin
//         if (update) begin
//             if (state < 4'b 1111) 
//                 state <= state + 1;
//             else 
//                 state <= 0;
//         end
//     end
// endmodule

module genbuf_3_new_4(
        i_clk,
        i_StoB_REQ0,
        controllable_BtoS_ACK0,
        i_StoB_REQ1,
        controllable_BtoS_ACK1,
        i_StoB_REQ2,
        controllable_BtoS_ACK2,
        i_RtoB_ACK0,
        controllable_BtoR_REQ0,
        i_RtoB_ACK1,
        controllable_BtoR_REQ1,
        i_FULL,
        i_nEMPTY,
        controllable_ENQ,
        controllable_DEQ,
        controllable_SLC0,
        controllable_SLC1, 
        pout0, pout1,       o_err
    );

input i_clk;
input i_StoB_REQ0;
input controllable_BtoS_ACK0;
input i_StoB_REQ1;
input controllable_BtoS_ACK1;
input i_StoB_REQ2;
input controllable_BtoS_ACK2;
input i_RtoB_ACK0;
input controllable_BtoR_REQ0;
input i_RtoB_ACK1;
input controllable_BtoR_REQ1;
input i_FULL;
input i_nEMPTY;
input controllable_ENQ;
input controllable_DEQ;
input controllable_SLC0;
input controllable_SLC1;
output o_err;
output pout0;
output pout1;

reg reg_i_StoB_REQ0;
reg reg_controllable_BtoS_ACK0;
reg reg_i_StoB_REQ1;
reg reg_controllable_BtoS_ACK1;
reg reg_i_StoB_REQ2;
reg reg_controllable_BtoS_ACK2;
reg reg_i_RtoB_ACK0;
reg reg_controllable_BtoR_REQ0;
reg reg_i_RtoB_ACK1;
reg reg_controllable_BtoR_REQ1;
reg reg_i_FULL;
reg reg_i_nEMPTY;
reg reg_controllable_ENQ;
reg reg_controllable_DEQ;
reg reg_controllable_SLC0;
reg reg_controllable_SLC1;
reg reg_stateG7_0;
reg reg_nstateG7_1;
reg reg_stateG12;
reg env_safe_err_happened;
reg env_fair0done;
reg env_fair1done;
reg sys_fair0done;
reg sys_fair1done;
reg sys_fair2done;
reg sys_fair3done;
reg [2:0] fair_cnt;

//assign pout0 = reg_stateG7_0;
assign pout0 = ~env_safe_err & ~env_safe_err_happened & (sys_safe_err | fair_err);
assign pout1 = reg_nstateG7_1 || reg_stateG12;


wire env_safe_err0;
wire env_safe_err1;
wire env_safe_err2;
wire env_safe_err3;
wire env_safe_err4;
wire env_safe_err5;
wire env_safe_err6;
wire env_safe_err7;
wire env_safe_err8;
wire env_safe_err9;
wire env_safe_err10;
wire env_safe_err11;
wire env_safe_err12;
wire env_safe_err;

wire sys_safe_err0;
wire sys_safe_err1;
wire sys_safe_err2;
wire sys_safe_err3;
wire sys_safe_err4;
wire sys_safe_err5;
wire sys_safe_err6;
wire sys_safe_err7;
wire sys_safe_err8;
wire sys_safe_err9;
wire sys_safe_err10;
wire sys_safe_err11;
wire sys_safe_err12;
wire sys_safe_err13;
wire sys_safe_err14;
wire sys_safe_err15;
wire sys_safe_err16;
wire sys_safe_err17;
wire sys_safe_err18;
wire sys_safe_err19;
wire sys_safe_err20;
wire sys_safe_err21;
wire sys_safe_err22;
wire sys_safe_err23;
wire sys_safe_err24;
wire sys_safe_err25;
wire sys_safe_err26;
wire sys_safe_err27;
wire sys_safe_err28;
wire sys_safe_err29;
wire sys_safe_err30;
wire sys_safe_err31;
wire sys_safe_err;

wire env_fair0;
wire env_fair1;

wire sys_fair0;
wire sys_fair1;
wire sys_fair2;
wire sys_fair3;
wire all_env_fair_fulfilled;
wire all_sys_fair_fulfilled;
wire fair_err;
wire o_err;


// =============================================================
//                        ENV_TRANSITION:
// =============================================================
// G((StoB_REQ0=1 * BtoS_ACK0=0) -> X(StoB_REQ0=1));	#A1
assign env_safe_err0 = ~((~(reg_i_StoB_REQ0 & ~reg_controllable_BtoS_ACK0)) | i_StoB_REQ0);

// G(BtoS_ACK0=1 -> X(StoB_REQ0=0));	#A1
assign env_safe_err1 = ~((~(reg_controllable_BtoS_ACK0)) | ~i_StoB_REQ0);

// G((StoB_REQ1=1 * BtoS_ACK1=0) -> X(StoB_REQ1=1));	#A1
assign env_safe_err2 = ~((~(reg_i_StoB_REQ1 & ~reg_controllable_BtoS_ACK1)) | i_StoB_REQ1);

// G(BtoS_ACK1=1 -> X(StoB_REQ1=0));	#A1
assign env_safe_err3 = ~((~(reg_controllable_BtoS_ACK1)) | ~i_StoB_REQ1);

// G((StoB_REQ2=1 * BtoS_ACK2=0) -> X(StoB_REQ2=1));	#A1
assign env_safe_err4 = ~((~(reg_i_StoB_REQ2 & ~reg_controllable_BtoS_ACK2)) | i_StoB_REQ2);

// G(BtoS_ACK2=1 -> X(StoB_REQ2=0));	#A1
assign env_safe_err5 = ~((~(reg_controllable_BtoS_ACK2)) | ~i_StoB_REQ2);

// G(BtoR_REQ0=0 -> X(RtoB_ACK0=0));	#A3
assign env_safe_err6 = ~(reg_controllable_BtoR_REQ0 | ~i_RtoB_ACK0);

// G((BtoR_REQ0=1 * RtoB_ACK0=1) -> X(RtoB_ACK0=1));	#A4
assign env_safe_err7 = ~((~(reg_controllable_BtoR_REQ0 & reg_i_RtoB_ACK0)) | i_RtoB_ACK0);

// G(BtoR_REQ1=0 -> X(RtoB_ACK1=0));	#A3
assign env_safe_err8 = ~(reg_controllable_BtoR_REQ1 | ~i_RtoB_ACK1);

// G((BtoR_REQ1=1 * RtoB_ACK1=1) -> X(RtoB_ACK1=1));	#A4
assign env_safe_err9 = ~((~(reg_controllable_BtoR_REQ1 & reg_i_RtoB_ACK1)) | i_RtoB_ACK1);

// G((ENQ=1 * DEQ=0) -> X(EMPTY=0));	#A4
assign env_safe_err10 = ~(~(reg_controllable_ENQ & ~reg_controllable_DEQ) | i_nEMPTY);

// G((DEQ=1 * ENQ=0) -> X(FULL=0));	#A4
assign env_safe_err11 = ~(~(reg_controllable_DEQ & ~reg_controllable_ENQ) | ~i_FULL);

// G((ENQ=1 <-> DEQ=1) -> ((FULL=1 <-> X(FULL=1)) *
//                         (EMPTY=1 <-> X(EMPTY=1))));	#A4
assign env_safe_err12 = ~(~(reg_controllable_ENQ ^~ reg_controllable_DEQ) | ((reg_i_FULL ^~ i_FULL) &  (reg_i_nEMPTY ^~ i_nEMPTY)) );

// collecting together the safety error bits:
assign env_safe_err = env_safe_err0 |
                      env_safe_err1 |
                      env_safe_err2 |
                      env_safe_err3 |
                      env_safe_err4 |
                      env_safe_err5 |
                      env_safe_err6 |
                      env_safe_err7 |
                      env_safe_err8 |
                      env_safe_err9 |
                      env_safe_err10 |
                      env_safe_err11 |
                      env_safe_err12;

// =============================================================
//                        SYS_TRANSITION:
// =============================================================
// G((StoB_REQ0=0 * X(StoB_REQ0=1)) -> X(BtoS_ACK0=0));	#G2
assign sys_safe_err0 = ~((~(~reg_i_StoB_REQ0 & i_StoB_REQ0 )) | ~controllable_BtoS_ACK0);

// G((BtoS_ACK0=0 * StoB_REQ0=0) -> X(BtoS_ACK0=0));	#G2
assign sys_safe_err1 = ~((~(~reg_controllable_BtoS_ACK0 & ~reg_i_StoB_REQ0 )) | ~controllable_BtoS_ACK0);

// G((BtoS_ACK0=1 * StoB_REQ0=1) -> X(BtoS_ACK0=1));	#G4
assign sys_safe_err2 = ~((~(reg_controllable_BtoS_ACK0 & reg_i_StoB_REQ0 )) | controllable_BtoS_ACK0);

// G((BtoS_ACK0=0) + (BtoS_ACK1=0));	#G5
assign sys_safe_err3 = ~(~controllable_BtoS_ACK0 | ~controllable_BtoS_ACK1);

// G((BtoS_ACK0=0) + (BtoS_ACK2=0));	#G5
assign sys_safe_err4 = ~(~controllable_BtoS_ACK0 | ~controllable_BtoS_ACK2);

// G((StoB_REQ1=0 * X(StoB_REQ1=1)) -> X(BtoS_ACK1=0));	#G2
assign sys_safe_err5 = ~((~(~reg_i_StoB_REQ1 & i_StoB_REQ1 )) | ~controllable_BtoS_ACK1);

// G((BtoS_ACK1=0 * StoB_REQ1=0) -> X(BtoS_ACK1=0));	#G2
assign sys_safe_err6 = ~((~(~reg_controllable_BtoS_ACK1 & ~reg_i_StoB_REQ1 )) | ~controllable_BtoS_ACK1);

// G((BtoS_ACK1=1 * StoB_REQ1=1) -> X(BtoS_ACK1=1));	#G4
assign sys_safe_err7 = ~((~(reg_controllable_BtoS_ACK1 & reg_i_StoB_REQ1 )) | controllable_BtoS_ACK1);

// G((BtoS_ACK1=0) + (BtoS_ACK2=0));	#G5
assign sys_safe_err8 = ~(~controllable_BtoS_ACK1 | ~controllable_BtoS_ACK2);

// G((StoB_REQ2=0 * X(StoB_REQ2=1)) -> X(BtoS_ACK2=0));	#G2
assign sys_safe_err9 = ~((~(~reg_i_StoB_REQ2 & i_StoB_REQ2 )) | ~controllable_BtoS_ACK2);

// G((BtoS_ACK2=0 * StoB_REQ2=0) -> X(BtoS_ACK2=0));	#G2
assign sys_safe_err10 = ~((~(~reg_controllable_BtoS_ACK2 & ~reg_i_StoB_REQ2 )) | ~controllable_BtoS_ACK2);

// G((BtoS_ACK2=1 * StoB_REQ2=1) -> X(BtoS_ACK2=1));	#G4
assign sys_safe_err11 = ~((~(reg_controllable_BtoS_ACK2 & reg_i_StoB_REQ2 )) | controllable_BtoS_ACK2);

// G((BtoR_REQ0=1 * RtoB_ACK0=0) -> X(BtoR_REQ0=1));	#G6
assign sys_safe_err12 = ~((~(reg_controllable_BtoR_REQ0 & ~reg_i_RtoB_ACK0)) | controllable_BtoR_REQ0);

// G((BtoR_REQ0=0) + (BtoR_REQ1=0));	#G7
assign sys_safe_err13 = ~(~controllable_BtoR_REQ0 | ~controllable_BtoR_REQ1);

// G(RtoB_ACK0=1 -> X(BtoR_REQ0=0));	#G8
assign sys_safe_err14 = ~(~reg_i_RtoB_ACK0 | ~controllable_BtoR_REQ0);

// G((BtoR_REQ1=1 * RtoB_ACK1=0) -> X(BtoR_REQ1=1));	#G6
assign sys_safe_err15 = ~((~(reg_controllable_BtoR_REQ1 & ~reg_i_RtoB_ACK1)) | controllable_BtoR_REQ1);

// G(RtoB_ACK1=1 -> X(BtoR_REQ1=0));	#G8
assign sys_safe_err16 = ~(~reg_i_RtoB_ACK1 | ~controllable_BtoR_REQ1);

// G((BtoR_REQ0=1 * BtoR_REQ1=1) -> FALSE);	#G7
assign sys_safe_err17 = reg_controllable_BtoR_REQ0 & reg_controllable_BtoR_REQ1;

// G((stateG7_1=0 * stateG7_0=1 * BtoR_REQ0=1) -> FALSE);	#G7
assign sys_safe_err18 = reg_nstateG7_1 & reg_stateG7_0 & reg_controllable_BtoR_REQ0;

// G((stateG7_1=1 * stateG7_0=1 * BtoR_REQ1=1) -> FALSE);	#G7
assign sys_safe_err19 = ~reg_nstateG7_1 & reg_stateG7_0 & reg_controllable_BtoR_REQ1;

// G((BtoS_ACK0=0 * X(BtoS_ACK0=1)) -> X(ENQ=1));	#G9
assign sys_safe_err20 = ~(~((~reg_controllable_BtoS_ACK0 & controllable_BtoS_ACK0)) | controllable_ENQ);

// G((BtoS_ACK0=0 * X(BtoS_ACK0=1))  -> X(SLC0=0 * SLC1=0));	#G9
assign sys_safe_err21 = ~(~((~reg_controllable_BtoS_ACK0 & controllable_BtoS_ACK0)) | (~controllable_SLC0 & ~controllable_SLC1));

// G((BtoS_ACK1=0 * X(BtoS_ACK1=1)) -> X(ENQ=1));	#G9
assign sys_safe_err22 = ~(~((~reg_controllable_BtoS_ACK1 & controllable_BtoS_ACK1)) | controllable_ENQ);

// G((BtoS_ACK1=0 * X(BtoS_ACK1=1)) <-> X(SLC0=1 * SLC1=0));	#G9
assign sys_safe_err23 = ~(((~reg_controllable_BtoS_ACK1 & controllable_BtoS_ACK1)) ^~ (controllable_SLC0 & ~controllable_SLC1));

// G((BtoS_ACK2=0 * X(BtoS_ACK2=1)) -> X(ENQ=1));	#G9
assign sys_safe_err24 = ~(~((~reg_controllable_BtoS_ACK2 & controllable_BtoS_ACK2)) | controllable_ENQ);

// G((BtoS_ACK2=0 * X(BtoS_ACK2=1)) <-> X(SLC0=0 * SLC1=1));	#G9
assign sys_safe_err25 = ~(((~reg_controllable_BtoS_ACK2 & controllable_BtoS_ACK2)) ^~ (~controllable_SLC0 & controllable_SLC1));

// G(((BtoS_ACK0=1 + X(BtoS_ACK0=0)) * (BtoS_ACK1=1 + X(BtoS_ACK1=0)) * (BtoS_ACK2=1 + X(BtoS_ACK2=0))) -> X(ENQ=0));	#G9
assign sys_safe_err26 = ~(~((reg_controllable_BtoS_ACK0 | ~controllable_BtoS_ACK0) & (reg_controllable_BtoS_ACK1 | ~controllable_BtoS_ACK1) & (reg_controllable_BtoS_ACK2 | ~controllable_BtoS_ACK2)) | ~controllable_ENQ);

// G((RtoB_ACK0=1 * X(RtoB_ACK0=0)) -> X(DEQ=1));	#G10
assign sys_safe_err27 = ~(~(reg_i_RtoB_ACK0 & ~i_RtoB_ACK0) | controllable_DEQ);

// G((RtoB_ACK1=1 * X(RtoB_ACK1=0)) -> X(DEQ=1));	#G10
assign sys_safe_err28 = ~(~(reg_i_RtoB_ACK1 & ~i_RtoB_ACK1) | controllable_DEQ);

// G(((RtoB_ACK0=0 + X(RtoB_ACK0=1)) * (RtoB_ACK1=0 + X(RtoB_ACK1=1))) -> X(DEQ=0));	#G10
assign sys_safe_err29 = ~(~((~reg_i_RtoB_ACK0 | i_RtoB_ACK0) & (~reg_i_RtoB_ACK1 | i_RtoB_ACK1)) | ~controllable_DEQ);

// G((FULL=1 * DEQ=0) -> ENQ=0);	#G11
assign sys_safe_err30 = ~(~(i_FULL & ~controllable_DEQ) | ~controllable_ENQ);

// G(EMPTY=1 -> DEQ=0);	#G11
assign sys_safe_err31 = ~( i_nEMPTY | ~controllable_DEQ);

// collecting together the safety error bits:
assign sys_safe_err = sys_safe_err0 |
                      sys_safe_err1 |
                      sys_safe_err2 |
                      sys_safe_err3 |
                      sys_safe_err4 |
                      sys_safe_err5 |
                      sys_safe_err6 |
                      sys_safe_err7 |
                      sys_safe_err8 |
                      sys_safe_err9 |
                      sys_safe_err10 |
                      sys_safe_err11 |
                      sys_safe_err12 |
                      sys_safe_err13 |
                      sys_safe_err14 |
                      sys_safe_err15 |
                      sys_safe_err16 |
                      sys_safe_err17 |
                      sys_safe_err18 |
                      sys_safe_err19 |
                      sys_safe_err20 |
                      sys_safe_err21 |
                      sys_safe_err22 |
                      sys_safe_err23 |
                      sys_safe_err24 |
                      sys_safe_err25 |
                      sys_safe_err26 |
                      sys_safe_err27 |
                      sys_safe_err28 |
                      sys_safe_err29 |
                      sys_safe_err30 |
                      sys_safe_err31;

// =============================================================
//                          ENV_FAIRNESS:
// =============================================================
// G(F(BtoR_REQ0=1 <-> RtoB_ACK0=1));	#A2
assign env_fair0 = controllable_BtoR_REQ0 ^~ i_RtoB_ACK0;

// G(F(BtoR_REQ1=1 <-> RtoB_ACK1=1));	#A2
assign env_fair1 = controllable_BtoR_REQ1 ^~ i_RtoB_ACK1;

assign all_env_fair_fulfilled = (env_fair0done | env_fair0) &
                                (env_fair1done | env_fair1);

// =============================================================
//                          SYS_FAIRNESS:
// =============================================================
// G(F(StoB_REQ0=1 <-> BtoS_ACK0=1));	#G1+G2
assign sys_fair0 = i_StoB_REQ0 ^~ controllable_BtoS_ACK0;

// G(F(StoB_REQ1=1 <-> BtoS_ACK1=1));	#G1+G2
assign sys_fair1 = i_StoB_REQ1 ^~ controllable_BtoS_ACK1;

// G(F(StoB_REQ2=1 <-> BtoS_ACK2=1));	#G1+G2
assign sys_fair2 = i_StoB_REQ2 ^~ controllable_BtoS_ACK2;

// G(F(stateG12=0));	#G12
assign sys_fair3 = ~reg_stateG12;

assign all_sys_fair_fulfilled = (sys_fair0done | sys_fair0) &
                                (sys_fair1done | sys_fair1) &
                                (sys_fair2done | sys_fair2) &
                                (sys_fair3done | sys_fair3);
assign fair_err = (fair_cnt >= 3'b100);

// computing the error output bit:
assign o_err = ~env_safe_err & ~env_safe_err_happened & (sys_safe_err | fair_err);
initial
 begin
  reg_i_StoB_REQ0 = 0;
  reg_controllable_BtoS_ACK0 = 0;
  reg_i_StoB_REQ1 = 0;
  reg_controllable_BtoS_ACK1 = 0;
  reg_i_StoB_REQ2 = 0;
  reg_controllable_BtoS_ACK2 = 0;
  reg_i_RtoB_ACK0 = 0;
  reg_controllable_BtoR_REQ0 = 0;
  reg_i_RtoB_ACK1 = 0;
  reg_controllable_BtoR_REQ1 = 0;
  reg_i_FULL = 0;
  reg_i_nEMPTY = 0;
  reg_controllable_ENQ = 0;
  reg_controllable_DEQ = 0;
  reg_controllable_SLC0 = 0;
  reg_controllable_SLC1 = 0;
  reg_stateG7_0 = 0;
  reg_nstateG7_1 = 0;
  reg_stateG12 = 0;
  env_safe_err_happened = 0;
  env_fair0done = 0;
  env_fair1done = 0;
  sys_fair0done = 0;
  sys_fair1done = 0;
  sys_fair2done = 0;
  sys_fair3done = 0;
  fair_cnt = 0;
 end


always @(posedge i_clk)
 begin
   // We remember if an environment error occurred:
   env_safe_err_happened = env_safe_err_happened | env_safe_err;

   // Updating the fairness counters: 
   if(all_sys_fair_fulfilled)
    begin
      env_fair0done = 0;
      env_fair1done = 0;
      sys_fair0done = 0;
      sys_fair1done = 0;
      sys_fair2done = 0;
      sys_fair3done = 0;
      fair_cnt = 0;
    end
   else
    begin
      sys_fair0done = sys_fair0done | sys_fair0;
      sys_fair1done = sys_fair1done | sys_fair1;
      sys_fair2done = sys_fair2done | sys_fair2;
      sys_fair3done = sys_fair3done | sys_fair3;
      if(all_env_fair_fulfilled)
       begin
         env_fair0done = 0;
         env_fair1done = 0;
         fair_cnt = fair_cnt + 1;
       end
      else
       begin
         env_fair0done = env_fair0done | env_fair0;
         env_fair1done = env_fair1done | env_fair1;
       end
    end

   // Updating the automata: 
   // Automaton G7: 
   if(reg_nstateG7_1 & ~reg_controllable_BtoR_REQ0 & reg_controllable_BtoR_REQ1)
    begin
      reg_nstateG7_1 = 1'b0;
      reg_stateG7_0 = 1'b0;
    end
   else if(~reg_nstateG7_1 & reg_controllable_BtoR_REQ0 & ~reg_controllable_BtoR_REQ1)
    begin
      reg_nstateG7_1 = 1'b1;
      reg_stateG7_0 = 1'b0;
    end
   else if(reg_nstateG7_1 & ~reg_controllable_BtoR_REQ0 & ~reg_controllable_BtoR_REQ1)
    begin
      reg_nstateG7_1 = 1'b1;
      reg_stateG7_0 = 1'b1;
    end
   else if(~reg_nstateG7_1 & ~reg_controllable_BtoR_REQ0 & ~reg_controllable_BtoR_REQ1)
    begin
      reg_nstateG7_1 = 1'b0;
      reg_stateG7_0 = 1'b1;
    end

   // Automaton G12: 
   if(~reg_stateG12 & reg_i_nEMPTY & ~reg_controllable_DEQ)
      reg_stateG12 = 1'b1;
   else if(reg_stateG12 & reg_controllable_DEQ)
      reg_stateG12 = 1'b0;

   // Latching the previous input:
   reg_i_StoB_REQ0 =  i_StoB_REQ0;
   reg_controllable_BtoS_ACK0 =  controllable_BtoS_ACK0;
   reg_i_StoB_REQ1 =  i_StoB_REQ1;
   reg_controllable_BtoS_ACK1 =  controllable_BtoS_ACK1;
   reg_i_StoB_REQ2 =  i_StoB_REQ2;
   reg_controllable_BtoS_ACK2 =  controllable_BtoS_ACK2;
   reg_i_RtoB_ACK0 =  i_RtoB_ACK0;
   reg_controllable_BtoR_REQ0 =  controllable_BtoR_REQ0;
   reg_i_RtoB_ACK1 =  i_RtoB_ACK1;
   reg_controllable_BtoR_REQ1 =  controllable_BtoR_REQ1;
   reg_i_FULL =  i_FULL;
   reg_i_nEMPTY =  i_nEMPTY;
   reg_controllable_ENQ =  controllable_ENQ;
   reg_controllable_DEQ =  controllable_DEQ;
   reg_controllable_SLC0 =  controllable_SLC0;
   reg_controllable_SLC1 =  controllable_SLC1;

 end
endmodule
