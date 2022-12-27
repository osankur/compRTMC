# CompRTMC
Compositional model checking and reactive synthesis algorithms for timed automata.
This is a prototype tool used for the experiments of the following paper:

  Ocan Sankur. Timed Automata Verification and Synthesis via Finite Automata Learning. TACAS 2023.

This is licensed under GNU GPL 3.0.

## Dependencies and Installation
The tool depends on several command line programs, most of which are shipped together:
- `NuSMV` as an FSM model checker (LGPL v2.1)

  https://nusmv.fbk.eu/index.html

- `nuXmv` as an FSM model checker (proprietary; not included due to license).
  This can be downloaded and used for academic and research purposes:

  https://nuxmv.fbk.eu/
  
- The TChecker timed automaton model checker (MIT license) executables: `tck-reach`, `tck-tar`, `tck-convert` from the following particular fork (the default `tacas23` branch): 
  
  https://github.com/osankur/tchecker/
  
  
- `abssynthe` circuit synthesis tool from AIG specifications (GNU GPL 3.0); the following branch `tacas23`:
  
    https://github.com/osankur/abssynthe/tree/tacas23

- `aigtoaig` and `aigtosmv` for translating aig models (modified to produce parsable names)
  Sources are included. Please see here for the original and full library:
  
  https://github.com/arminbiere/aiger

- `yosys`, `berkeley-abc` for translating Verilog programs to AIG.
  These can be installed as packages of the same name e.g. under Ubuntun 22.04.
  

## Usage
Currently two safety verification algorithms are implemented.

- `learning`: a DFA learning algorithm is run to learn the timed automaton model
applies compositional verification.

- `synth`: controller synthesis algrithm.

- `tar`: trace abstraction refinement algorithm. This case requries the timed automaton
to be deterministic in the sense that at each location there is at most one transition with
given event. TChecker is used to obtain an interpolant automaton, and the automatalib library
is used to compute unions of DFAs and their minimizations.

## Modeling
- In verification problems, the FSM is given in the SMV format. The alphabet is defined
  with DEFINEs that are prefixed with `_rt_`. For instance, `_rt_event` is true iff the next transition is to be
  synchronized with a TA edge labeled by `event`. 
- These predicates must be pairwise disjoint: at each instant, at most one label must be true. This is not checked by the program,
  so it is the user's responsibility.
- A single error define or variable must be used to mark the error, and the invariant must be exactly `INVARSPEC !error`.