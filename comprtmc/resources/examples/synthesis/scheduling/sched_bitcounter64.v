/*
Two sporadic tasks arrive each with signal startA, and startB.
The controller is supposed to schedule these using two machines. Each machine has an internal state,
and the scheduling `time' depends on this. Some states requires 2 ticks, others 3 ticks.
Each tick represents an external call with nondet. duration. At each tick, the internal states of the machines change.
The controller loses (error=1) if all machines are occupied upon the arrival of a new task.

Internal states of the two machines are modeled by a counter.
*/
`define nb1 1
`define nb2 2
module sched(input clk, input controllable_sched0, input controllable_sched1, 
                        input startA, input startB,
                        input tick,
                        output error,
                        output _rt_startA, output _rt_startB, 
                        output _rt_tick);
    reg[2:0] m0_occupied; // 0: idle, 1: taskA, 2: taskB
    reg[2:0] m0_counter;
    reg[2:0] m1_occupied;
    reg[2:0] m1_counter;
    reg notfirst;
    reg reg_error;

    wire pout0;
    wire pout1;
    reg update0;
    reg update1;

    counter p0(clk, update0, 2'b00, pout0);
    counter p1(clk, update1, 2'b11, pout1);

    assign error = reg_error;

    assign _rt_startA = notfirst && ~error && startA;
    assign _rt_startB = notfirst && ~error && ~_rt_startA && startB;
    assign _rt_tick = notfirst &&~_rt_startA && ~_rt_startB && tick;

    initial begin
        notfirst = 0;
        m0_counter = 0;
        m0_occupied = 0;
        m1_counter = 0;
        m1_occupied = 0;
        update0 = 0;
        update1 = 0;
        reg_error = 0;
    end
    always @(posedge clk) begin
        if (notfirst) begin
            update0 <= 0;
            update1 <= 0;
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
                if (m0_occupied>0)
                    update0 <=  1;
                if (m1_occupied>0)
                    update1 <=  1;

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

module counter(input clk, input update, input[0:1] bit, output outstate);
    reg[9:0] state;
    initial state =0;
    assign outstate = state[bit];
    always @(posedge clk)begin
        if (update) begin
            if (state < 64) 
                state <= state + 1;
            else 
                state <= 0;
        end
    end
endmodule
