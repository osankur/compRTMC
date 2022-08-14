`define k 2
`define excl_exp (_rt_robot + _rt_obs1  <= 1)

module planning(input clk, 
                           input move_robot, 
                           input move_obs1_up, input move_obs1_down,
                           input move_obs1_left, input move_obs1_right,
                           input controllable_up, input controllable_down, input controllable_left, input controllable_right,
                           output error,
                           output _rt_obs1, output _rt_robot,
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
  reg excl;
  reg notfirst;
  reg [3:0] robot_x;
  reg [3:0] robot_y;
  reg [3:0] obs1_x;
  reg [3:0] obs1_y;

  wire maintenance;
  wire shock;
  wire dummyerr;
  wire p1;
  counter c0(clk, _rt_obs1, 3'b010, maintenance);
  //counter c1(clk, _rt_obs1, 3'b111, shock);
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
        shock, dummyerr);
      

  wire coll1;
  wire cur_excl;
  assign cur_excl = `excl_exp;
  //assign oexcl = excl & `excl_exp;
  assign coll1 = (robot_x == obs1_x & robot_y == obs1_y);
  reg reg_error;
  // No moves are to be done on the very first step, and once the error is up
  assign _rt_robot = notfirst & !error & move_robot;
  assign _rt_obs1 = notfirst & !error & (move_obs1_up | move_obs1_down | move_obs1_left | move_obs1_right);
  assign error = reg_error;
  initial
  begin
    notfirst = 0;
    reg_error = 0;
    robot_x = 0;
    robot_y = 0;
    obs1_x = 0;
    obs1_y = 0;
    excl = 0;
  end
  always @(posedge clk)
  begin
    notfirst <= 1;
    if (!notfirst) begin
      obs1_x = 2;
      excl = 1;
    end else begin
      excl <= excl & `excl_exp;
      reg_error <= reg_error | excl & `excl_exp & coll1;
      // There is a 0-width thin wall between x=k-1 and x=k+2, and 0<=y<=2*k-1
      // There is a another one between x=2k-1 and x=2k, and 2k<=y2=3k-1
      if (!maintenance && move_obs1_up) begin
        if (obs1_y < 3*`k-1)
          obs1_y <= obs1_y +1;
      end
      else if (!maintenance && move_obs1_down) begin
        if (obs1_y > 0)
          obs1_y <= obs1_y -1;
      end
      if (!maintenance && move_obs1_left) begin
        if (obs1_x > 0)
          obs1_x <= obs1_x -1;
      end
      else if (!maintenance && move_obs1_right) begin
        if (obs1_x < 3*`k-1)
          obs1_x <= obs1_x +1;
      end
      if (_rt_robot && !shock) begin
        if (controllable_down & robot_y > 0) 
          robot_y <= robot_y - 1;
        else if (controllable_up & robot_y < 3*`k-1)
          robot_y <= robot_y + 1;
        else if (controllable_left & robot_x > 0 )
          robot_x <= robot_x - 1;
        else if (controllable_right & robot_x < 3*`k-1 )
          robot_x <= robot_x + 1;
      end
    end
  end
endmodule


module counter(input clk, input update, input[0:2] bit, output outstate);
    reg[10:0] state;
    initial state =0;
    assign outstate = state[bit];
    always @(posedge clk)begin
        if (update) begin
            if (state < 32)
                state <= state + 1;
            else 
                state <= 0;
        end
    end
endmodule


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
        pout0,  o_err
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
assign pout0 = (~env_safe_err & ~env_safe_err_happened & (sys_safe_err | fair_err));
// assign pout0 = ~reg_stateG7_0;
// assign pout1 = reg_nstateG7_1 || reg_stateG12;


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
