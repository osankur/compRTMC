// module main;
//     reg tb_cont_reset;
//     reg tb_i;
//     wire _rt_get;
//     wire error;
//     reg[5:0] counter;
//     bench _rtmc_(tb_i, tb_cont_reset, error, _rt_get);
//     initial begin
//         $dumpfile("test.vcd");
//         $dumpvars(0, main);
//         counter = 0;
//         tb_i = 1;
//         tb_cont_reset = 1;
//     end
//     always #1 begin
//     if (counter >= 10)
//     begin
//         $finish;
//     end
//     else begin
//         $display("tick");
//         counter = counter+1;
//         tb_i = 1;
//         tb_cont_reset = 1;
//     end
//     end
// endmodule
module bench( input clk, input  i, input controllable_i, output error, output _rt_get);
  reg [2:0] counter;
  assign _rt_get =  i;
  assign error = reg_error;
  reg reg_error;
  initial
  begin
    counter = 2'b0;
    reg_error = 0;
  end
  always @(posedge clk)
  begin
    reg_error <= reg_error | counter >= 3 | (i && !controllable_i);
    if(counter < 3 && i && controllable_i) begin
        $display("\tresponse");
        counter <= counter + 1;
    end
  end
endmodule