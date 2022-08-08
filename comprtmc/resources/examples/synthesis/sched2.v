`define nb_tasks 3

/*
We want to execute nb_tasks tasks within each cycle. We have the freedom to execute either task0 or task1.
Each task has an internal state p0, p1 and the execution time can be short or long (_rt_sched_short or _rt_sched_long).

This example does not seem adapted for our language game setting. In fact, since Controller's actions directly influence 
the word that is being read in the TA, it can make sure the word is not accepted and win.
*/
module sched(input clk, input controllable_sched0, input controllable_sched1, 
                        input end_task,
                        input end_of_cycle,
                        output error,
                        output _rt_sched_short, output _rt_sched_long, output _rt_end_task,
                        output _rt_end_of_cycle
                        );
    reg[0:3] counter;
    reg m1_occupied;
    reg notfirst;
    reg reg_error;
    reg definite_win;
    wire pout0;
    wire pout1;
    reg update;
    process p0(clk, update, 2'b 00, pout0);
    process p1(clk, update, 2'b 10, pout1);
    assign error = reg_error;
    assign _rt_sched_short = notfirst && ~m1_occupied && ~error && (controllable_sched0 && ~pout0 || controllable_sched1 && ~pout1); 
    assign _rt_sched_long = notfirst  && ~_rt_sched_short && ~m1_occupied && ~error && (controllable_sched0 && pout0 || controllable_sched1 && pout1); 
    assign _rt_end_of_cycle = notfirst && ~_rt_sched_short && ~_rt_sched_long && end_of_cycle;
    assign _rt_end_task = notfirst &&~_rt_end_of_cycle && ~_rt_sched_short && ~_rt_sched_long && m1_occupied && ~error && end_task;

    initial begin
        notfirst = 0;
        counter = 0;
        m1_occupied = 0;
        update = 0;
        reg_error = 0;
        definite_win = 0;
    end
    always @(posedge clk) begin
        if (notfirst) begin
            update <= 0;
            if(_rt_end_task)begin
                update <= 1;
                m1_occupied <= 0;
                if (counter < `nb_tasks)
                    counter <= counter + 1;
            end
            if (_rt_sched_short || _rt_sched_long )begin
                m1_occupied <= 1;
            end
            if ( _rt_end_of_cycle )
                counter <= 0;
            // Go to error if:
            // - end of cycle is reached, and less than nb_tasks was executed
            // - or, sched command was issued by the controller when machine is occupied.
            reg_error <= reg_error || (
                _rt_end_of_cycle && counter < `nb_tasks ||  (controllable_sched0 && m1_occupied || controllable_sched1 && m1_occupied)
                );
        end
        notfirst <= 1;
    end
endmodule
module process(input clk, input update, input[0:1] bit, output outstate);
    reg[4:0] state;
    initial state =0;
    assign outstate = state[bit];
    always @(posedge clk)begin
        if (update) begin
            if (state < 4'b 1111) 
                state <= state + 1;
            else 
                state <= 0;
        end
    end
endmodule
