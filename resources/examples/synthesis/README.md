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

1. Convert to blif with yosys
    echo "read_verilog ex1.v; write_blif ex1.blif" | yosys
2. Convert to aig with abc
    berkeley-abc -c "read_blif a.blig; strash; refactor; rewrite; dfraig; write_aiger -s a.aig"
3. Convert aig to aag
    aigtoaig a.aig a.aag

These operations are implemented in the script `make-aag.sh`.

# Description of the benchmarks
## Scheduling Problems
In these benchmarks, there are two sporadic tasks whose start are indicated by the events `startA` and `startB`.
The controller must schedule these using two machines. Each machine has an internal state,
and the scheduling duration depends on the internal state. Some states require executing two external tasks, some others require executing three.
The external task has a nondeterministic duration and is modeled by the event `tick`.
The internal states of the machines change each time they finalize a task.
The controller loses (error=1) if all machines are busy upon the arrival of a new task, or if it schedules a task on a busy machine.

The timed model model gives interarrival times of the tasks, and the duration of a tick event.

### Verilog-TChecker Files
- `scheduling/sched_genbuf2f3yunrealy.v`
  The combined internal states of both machines are modeled by the AIG circuit genbuf2f3yunrealy.aag (from the synthesis scompetition benchmarks).
  Here, Environment chooses uncontrollable inputs of the genbuf model, and the controller chooses its controllable inputs.
- `scheduling/sched_counter64.v`
  The internal state of each machine is an 6-bit counter.

The timed automata model are: `sched*.ta`

### Uppaal TIGA Files
- `scheduling/sched_genbuf2f3yunrealy_*.xml`, `scheduling/sched_counter64_*.xml` are the corresponding Uppaal files.
- The query file is `sched.q`

## Real-time planning
One controlled robot, and an adversarial obstacle move in a 3k x 3k grid with two walls (at [k,k]x[0,2k], and at [2k,2k]x[k,3k]).
They can both move to an adjacent cell on an event corresponding to their moves. The controlled robot moves upon the event `robot`, while the adversarial obstacle moevs upon the event `obs`.

Controller's objective is to avoid collision with the obstacle.
This is the stateles version.

In the genbuf version, both robots have internal states: both of them have a glitch depending on their internal states, during which they stay idle for one step.

### Verilog-TChecker Files
- Verilog: `planning_genbuf.v`, `planning_stateless.v`
- Timed automata; `planning_*.ta`

### Uppaal TIGA Files
- `planning_stateless_*.xml`
- `planning_genbuf_*.xml`