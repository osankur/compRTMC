module main;
    reg clk; reg sched0; reg sched1;
    reg startA, startB, tick;
    wire _rt_startA, _rt_startB, _rt_tick;
    wire error;
    reg[5:0] i;

    sched p(clk, sched0, sched1, startA, startB, tick, error, _rt_startA, _rt_startB, _rt_tick);
    initial begin
        $dumpfile("test.vcd");
        $dumpvars(0, main);
        clk = 0;
        sched0 = 0;
        sched1 = 0;
        startA = 0;
        startB = 0;
        tick = 0;
        toggle_clk;

        startA <= 1;
        sched0 <= 1;
        tick <= 1;
        toggle_clk;

        startA <= 0;
        sched0 <=0;
        toggle_clk;
        toggle_clk;
        toggle_clk;

        startB <= 1;
        sched0 <= 1;
        toggle_clk;

        startB <= 0;
        sched0 <= 0;
        startA <= 1;
        sched1 <= 1;
        toggle_clk;

        startA <= 0;
        sched1 <= 0;
        toggle_clk;
        toggle_clk;
        toggle_clk;
        toggle_clk;
        toggle_clk;

    end
    task toggle_clk;
      begin
        #10 clk = ~clk;
        #10 clk = ~clk;
      end
    endtask    
    task schedule0;
    begin
        sched0 <= 1;
        sched1 <= 0;
    end
    endtask
    task schedule1;
    begin
        sched0 <= 0;
        sched1 <= 1;
    end
    endtask
    task cleartasks;
    begin
        sched0 <= 0;
        sched1 <= 0;
    end
    endtask

endmodule
