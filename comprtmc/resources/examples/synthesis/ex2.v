module bench( input clk, input  i, input controllable_i, output error, output _rt_get);
  reg [2:0] counter;
  // wire response;
  assign _rt_get =  i;
  // assign response =  ;
  assign error = reg_error;
  reg reg_error;
  initial
  begin
    counter = 2'b0;
    reg_error = 0;
  end
  always @(posedge clk)
  begin
    if(response) begin
        $display("\tresponse");
        counter = counter + 1;
    end
    reg_error = i & !(counter <= 2 && i && controllable_i);
  end
endmodule