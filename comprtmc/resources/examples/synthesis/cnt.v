module bench(input clk, input  stay, input controllable_reset, output error, output _rt_check, output _rt_event);
  reg [4:0] counter;

  assign error = (counter == 5'b11111) ? 1 : 0;
  assign _rt_check = counter == 5'b00001;
  assign _rt_event = !_rt_check;
  initial
  begin
    counter = 5'b0;
  end
  always #1
  begin
    if(stay) begin
      counter = counter;
     $display("stay");
    end
    else if(counter == 5'b01111 && controllable_reset) begin
      counter = 0;
      $display("counter = 0");
     end
    else begin
      counter = counter + 1;
      $display("inc");
     end
   end
endmodule

