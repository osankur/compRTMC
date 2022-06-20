# CompRTMC
Compositional model checking and reactive synthesis algorithms for timed automata

## Dependencies
The tool depends on several command line programs, most of which are shipped together:
- `NuSMV` as an FSM model checker
- `nuXmv` as an FSM model checker (not included in the archive due to its licence)
- The TChecker timed automaton model checker executables: `tck-reach`, `tck-tar`, `tck-convert` (branch `tar`)
- `abssynthe` circuit synthesis tool from AIG specifications (branch `env_win_region`)
  - Modified to produce the controlled system as output (-o) both when the circuit is realizable (system controlled by the controller),
    and when it is not realizable (system controlled by the environment strategy).
- `aigtosmv` for translating aig models to SMV (modified to produce parsable names)
- `yosys`, `berkeley-abc`, `aigtoaig` for translating Verilog programs to ascii AIG
  
## Usage
Currently two safety verification algorithms are implemented.

- `learning`: a DFA learning algorithm is run to learn the timed automaton model
applies compositional verification.

- `tar`: trace abstraction refinement algorithm. This case requries the timed automaton
to be deterministic in the sense that at each location there is at most one transition with
given event. TChecker is used to obtain an interpolant automaton, and the automatalib library
is used to compute unions of DFAs and their minimizations.

- `synth`: synthesize.

## Modeling
- Explain the use of `_rt_events`. Typically one uses DEFINEs as predicates. 
- These predicates must be pairwise disjoint.
- A single error define or variable must be used to mark the error, and the invariant must be exactly `INVARSPEC !error`.
- Avoid defining _rt_events as inputs.
- TODO provide basic examples

## Future Work
- Faster learning options:
  - Use statistical simulations to generate (pos,neg) while `covering' the ta statistically
  - Then use offline learning to make a DFA hypothesis, check inclusion and continue learning if not passed
  - If passed, model check, etc.
- Add support for PReach with Murphi FSM format

- To test TAR, add a benchmark with _very_ easy FSM, and several clocks
In fact, when FSM queries are slow, the algorithm does not converge quickly.
My hypothesis: the graph structure of programs are so simple in general that
the automata approach produces very quickly counterexample traces. Here however,
reactive systems are so nondeterministic in their structures that the FSM verification is hard.

Fischer?
Job-shop?
gps-mc
train-gate
check out leader_election in tchecker distribution