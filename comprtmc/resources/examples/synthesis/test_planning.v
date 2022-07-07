module main;
    reg tb_robot;
    reg tb_robot_up, tb_robot_down, tb_robot_left, tb_robot_right;
    reg tb_obs1_up, tb_obs1_down, tb_obs1_left, tb_obs1_right;
    reg tb_obs2_up, tb_obs2_down, tb_obs2_left, tb_obs2_right;
    reg tb_end_init = 0;
    reg clk;
    wire _rt_robot, _rt_obs1, _rt_obs2;
    wire error;
    reg[5:0] i;
    planning p(clk, tb_end_init, tb_robot, tb_obs1_up, tb_obs1_down, tb_obs1_left, tb_obs1_right,
               tb_obs1_up, tb_obs1_down, tb_obs1_left, tb_obs1_right, 
               tb_robot_up, tb_robot_down, tb_robot_left, tb_robot_right, 
               error,
               _rt_robot, _rt_obs1, _rt_obs2);
    initial begin
        $dumpfile("test.vcd");
        $dumpvars(0, main);
        clk = 0;
        tb_obs1_up <= 0;
        tb_obs1_down <= 0;
        tb_obs1_left <= 0;
        tb_obs1_right <= 0;

        tb_obs2_up <= 0;
        tb_obs2_down <= 0;
        tb_obs2_left <= 0;
        tb_obs2_right <= 0;

        tb_robot_up <= 0;
        tb_robot_down <= 0;
        tb_robot_left <= 0;
        tb_robot_right <= 0;
        tb_end_init <= 0;

        // Initialize obstacles
        toggle_clk;
        tb_end_init <= 1;
        toggle_clk;

        tb_obs1_right <= 1;
        tb_obs2_right <= 1;
        toggle_clk;
        toggle_clk;
        tb_obs1_right <= 0;
        toggle_clk;
        toggle_clk;

        tb_end_init <= 1;
        toggle_clk;

        tb_robot=1;
        for(i = 0; i < 5; i=i+1) begin
          tb_robot_up <= 1;
          toggle_clk;
        end;
        for(i = 0; i < 3; i=i+1) begin
          tb_robot_up <= 0;
          tb_robot_right <=1;
          toggle_clk;
        end;
        for(i = 0; i < 2; i=i+1) begin
          tb_robot_right <= 0;
          tb_robot_down <= 1;
          toggle_clk;
        end;
        for(i = 0; i < 2; i=i+1) begin
          tb_obs1_right = 1;
          tb_robot_down <= 0;
          tb_robot_right <=1 ;
          toggle_clk;
        end;
        for(i = 0; i < 2; i=i+1) begin
          tb_robot_right <= 0;
          tb_robot_up <= 1;
          toggle_clk;
        end;
        toggle_clk;
        // for(i = 0; i < 10; i=i+1) begin
        //   toggle_clk;
        //   tb_robot=1;
        //   tb_robot_up=1;
        // end
    end
    task toggle_clk;
      begin
        #10 clk = ~clk;
        #10 clk = ~clk;
      end
    endtask    

endmodule
