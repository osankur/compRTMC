`define k 2
`define robot_target_x 5
`define robot_target_y 5
`define non_excl_exp (_rt_robot + _rt_obs1 + _rt_obs2 >= 2)

/* At the very first cycle, notfirst=0, and we only initialize the positions of the obstacles.
 * Then, starts an initialization phase where the obstacles move freely around within their designated areas.
 * When the uncontrollable end_init signal arrives, the robot can start moving in order to reach its target.
 **/
module planning(input clk, input end_init,
                           input move_robot, 
                           input move_obs1_up, input move_obs1_down,
                           input move_obs1_left, input move_obs1_right,
                           input move_obs2_up, input move_obs2_down,
                           input move_obs2_left, input move_obs2_right,
                           input controllable_up, input controllable_down, input controllable_left, input controllable_right,
                           output error,
                           output _rt_robot, output _rt_obs1, output _rt_obs2);
  // Whether several uncontrollable inputs have been set to 1
  reg non_excl;
  reg initialized;
  reg notfirst;
  reg [2:0] counter;
  reg [3:0] robot_x;
  reg [3:0] robot_y;
  reg [3:0] obs1_x;
  reg [3:0] obs1_y;
  reg [3:0] obs2_x;
  reg [3:0] obs2_y;

  reg reg_error;
  reg goal_reached;
  assign _rt_robot = move_robot;
  assign _rt_obs1 = move_obs1_up | move_obs1_down | move_obs1_left | move_obs1_right;
  assign _rt_obs2 = move_obs2_up | move_obs2_down | move_obs2_left | move_obs2_right;
  assign error = reg_error;
  initial
  begin
    initialized = 0;
    notfirst = 0;
    goal_reached = 0;
    non_excl = 0;
    reg_error = 0;
    robot_x = 0;
    robot_y = 0;
    obs1_x = 0; //obs1_x = 2;
    obs1_y = 0;
    obs2_x = 0; // obs2_x = 4;
    obs2_y = 0;

  end
  always @(posedge clk)
  begin
    initialized <= initialized | end_init;
    notfirst <= 1;
    if (!notfirst) begin
      obs1_x = 2;
      obs2_x = 4;
    end
    else begin
      // Obs1 shall remain between within [`k,2`k-1] x [0,2`k-1]
      if (move_obs1_up) begin
        if (obs1_y < 2*`k-1)
          obs1_y <= obs1_y +1;
      end
      if (move_obs1_down) begin
        if (obs1_y > 0)
          obs1_y <= obs1_y -1;
      end
      if (move_obs1_left) begin
        if (obs1_x > `k+1)
          obs1_x <= obs1_x -1;
      end
      if (move_obs1_right) begin
        if (obs1_x < 2*`k-1)
          obs1_x <= obs1_x +1;
      end

      // Obs2 shall remain between within [2`k,3`k-1] x [0,2`k-1]
      if (move_obs2_up) begin
        if (obs2_y < 2*`k-2)
          obs2_y <= obs2_y +1;
      end
      if (move_obs2_down) begin
        if (obs2_y > 0)
          obs2_y <= obs2_y -1;
      end
      if (move_obs2_left) begin
        if (obs2_x > 2*`k+1)
          obs2_x <= obs2_x -1;
      end
      if (move_obs2_right) begin
        if (obs2_x < 3*`k-2)
          obs2_x <= obs2_x + 1;
      end
    end
    if (initialized) begin
      non_excl <= non_excl | `non_excl_exp;
      if(non_excl) begin
          $display("\nnonexcl!");
      end
      reg_error = reg_error | ~`non_excl_exp & !goal_reached &
                              (
                                (robot_x == obs1_x & robot_y == obs1_y)
                                & (robot_x == obs2_x & robot_y == obs2_y)
                              );
      // There is a 0-width thin wall between x=k-1 and x=k+2, and 0<=y<=2*k-1
      // There is a another one between x=2k-1 and x=2k, and 2k<=y2=3k-1
      if (move_robot) begin
        if (controllable_down & robot_y > 0) 
          robot_y <= robot_y - 1;
        else if (controllable_up & robot_y < 3*`k-1)
          robot_y <= robot_y + 1;
        else if (controllable_left & robot_x > 0 & (robot_x != `k | robot_y >= 2*`k) & (robot_x != 2*`k | robot_y < 2*`k))
          robot_x <= robot_x - 1;
        else if (controllable_right & robot_x < 3*`k-1 & (robot_x != `k-1 | robot_y >= 2*`k) & (robot_x != 2*`k-1 | robot_y < 2*`k))
          robot_x <= robot_x + 1;
      end
      goal_reached <= goal_reached | robot_x == `robot_target_x && robot_y == `robot_target_y;
    end
  end
endmodule