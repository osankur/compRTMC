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
module bench( input  i, input controllable_i, output error, output _rt_get);
  reg [2:0] counter;
  wire response;
  // All rt events must be preconditioned with clk
  assign _rt_get =  i;
  assign response =  counter < 3 && i && controllable_i;
  assign error = reg_error;
  reg reg_error;
  initial
  begin
    counter = 2'b0;
    reg_error = 0;
  end
  always #1
  begin
    if(response) begin
        $display("\tresponse");
        counter = counter + 1;
    end
    reg_error = i & !response;
  end
endmodule
// For conversion to aag
// 1) Convert to blif with yosys
//    echo "read_verilog ex1.v; write_blif ex1.blif" | yosys
// 2) Convert to aig with abc
//    berkeley-abc -c "read_blif a.blig; strash; refactor; rewrite; dfraig; write_aiger -s a.aig"
// 3) Convert aig to aag
//    aigtoaig a.aig a.aag
//
// berkeley-abc cannot parse this verilog file; only yosys can
// yosys cannot synthesize