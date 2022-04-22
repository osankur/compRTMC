# CompRTMC
Compositional model checking algorithms for timed automata

## Usage

## TODO 
- Add new benchmarks
  - Mutex (from TChecker database)
    
- Trace abstraction refinement algorithm on deterministic timed automata
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