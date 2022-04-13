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