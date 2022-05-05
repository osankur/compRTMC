# CompRTMC
Compositional model checking algorithms for timed automata

## Usage
Currently two safety verification algorithms are implemented.

- `learning`: a DFA learning algorithm is run to learn the timed automaton model
to apply compositional verification.

- `tar`: trace abstraction refinement algorithm. This case requries the timed automaton
to be deterministic in the sense that at each location there is at most one transition with
given event. TChecker is used to obtain an interpolant automaton, and the automatalib library
is used to compute unions of DFAs and their minimizations.

- `synth`: synthesize.
All events in the TA must be synchronizing events. Otherwise complementing is complicated.
The _rt_events must all be variables. In fact, DEFINES are lost when translating to/from AIG.

## Modeling
- Explain the use of `_rt_events`. Typically one uses DEFINEs as predicates. 
- These predicates must be pairwise disjoint.
- A single err define or variable must be used to mark the error, and the invariant must be exactly `INVARSPEC !err`.
- Avoid defining _rt_events as inputs.
- TODO provide a basic example

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