`define k 3
`define excl_exp (_rt_robot + _rt_obs1  <= 1)

module planning(input clk, 
                           input move_robot, 
                           input move_obs1_up, input move_obs1_down,
                           input move_obs1_left, input move_obs1_right,
                           input controllable_up, input controllable_down, input controllable_left, input controllable_right,
                           output error,
                           output _rt_obs1, output _rt_robot);
  reg excl;
  reg notfirst;
  reg [3:0] robot_x;
  reg [3:0] robot_y;
  reg [3:0] obs1_x;
  reg [3:0] obs1_y;

  wire maintenance =0;
  wire shock = 0;
  // counter c0(clk, _rt_obs1, 3'b010, maintenance);
  // counter c1(clk, _rt_obs1, 3'b100, shock);

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
        // else if (controllable_left & robot_x > 0 & (robot_x != `k | robot_y >= 2*`k) & (robot_x != 2*`k | robot_y < 2*`k))
        //   robot_x <= robot_x - 1;
        // else if (controllable_right & robot_x < 3*`k-1 & (robot_x != `k-1 | robot_y >= 2*`k) & (robot_x != 2*`k-1 | robot_y < 2*`k))
        //   robot_x <= robot_x + 1;
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
            if (state < 128)
                state <= state + 1;
            else 
                state <= 0;
        end
    end
endmodule
