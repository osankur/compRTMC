# Benchmark Format
The FSM part is a Verilog module in which the inputs are uncontrollable unless they are prefixed with "controllable_".
There is a distinguished output named `error` which is true iff the system is at the error state.
All other outputs are synchronization labels with the timed automaton and are to be prefixed with `_rt_`.

The module must have the distinguished `input clk` which defines the synchronous steps of the module;
that is, a synchronous step happens on the rising edge of `clk`. This input should not be used elsewhere.

As an example, consider the file `ex1.v`. Input `i` is uncontrollable, and `controllable_i` is controllable. There is a single synchronization label `_rt_get`. The accompanying timed automaton file `ex1.ta` determines the untimed language on the singleton alphabet `{_rt_get}`.
Here, to avoid the error state, whenever `i` is true, `controllable_i` must be set to 1 as well. However, this is only possible if `counter <= 3`, that is,
at most three times. The timed automaton model also restricts the language to at most three `_rt_get` events.
Thus, the system is controllable.
On the other hand, in `ex2.v`, the controller is only allowed to respond twice, so the system is uncontrollable.

The real-time semantics is the following. 

At each synchronous step:
1. the adversary chooses a valuation for uncontrollable inputs
2. the controller instantaneously responds with a valuation for the controllable inputs (depending on the state and the uncontrollable inputs)
3. the output labels are determined by the current state and current valuations of the inputs and outputs. 

We intuitively assume that the controller responds instantaneously to the environment. The real-time events are in fact used to measure the time elapsed
between particular synchronous steps. In particular, if the environment picks an input valuation that the timed automaton rejects, then safety is achieved
(error can no longer be set to 1).

Each `_rt_sigmal` symbol must only depend on uncontrollable inputs, and must be pairwise disjoint.

In fact, we will consider overapproximations of the timed automaton, which means that in the original game, the adversary has more possibilities, but not the controller. Thus, Controller winning in the abstraction implies that they win in the original game.

# How to translate from Verilog to AIG
The user only needs to provide `.v` and `.ta` files but it might be useful to know how the tool translates Verilog to AIG to run reactive synthesis tools.
The translation might give non-blocking warnings so make sure the FSM .v file is tested independently before proceeding to the TA synthesis.

 1) Convert to blif with yosys
    echo "read_verilog ex1.v; write_blif ex1.blif" | yosys
 2) Convert to aig with abc
    berkeley-abc -c "read_blif a.blig; strash; refactor; rewrite; dfraig; write_aiger -s a.aig"
 3) Convert aig to aag
    aigtoaig a.aig a.aag

These operations are implemented in the script `make-aag.sh`.