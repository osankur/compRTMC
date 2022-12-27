# CompRTMC
Compositional model checking and reactive synthesis algorithms for timed automata.
This is a prototype tool used for the experiments of the following paper:

  Ocan Sankur. Timed Automata Verification and Synthesis via Finite Automata Learning. TACAS 2023.

This software is licensed under GNU GPL 3.0.

## Dependencies and Installation

### Manual Installation
The tool depends on several command line programs. We provide the binaries for most of them:

- `NuSMV` as an FSM model checker (LGPL v2.1)

  https://nusmv.fbk.eu/index.html
  
- The TChecker timed automaton model checker (MIT license) executables: `tck-reach`, `tck-tar`, `tck-convert` from the following particular fork (the default `tacas23` branch): 
  
  https://github.com/osankur/tchecker/
  
  
- `abssynthe` circuit synthesis tool from AIG specifications (GNU GPL 3.0); the following branch `tacas23`:
  
    https://github.com/osankur/abssynthe/tree/tacas23

- `aigtoaig` and `aigtosmv` for translating aig models (modified to produce parsable names):
  https://github.com/osankur/aiger

  The original and full library is here:
  
  https://github.com/arminbiere/aiger

  Some scripts in the present repository contain copies of parts of this library.

- [*] `yosys`, `berkeley-abc` for translating Verilog programs to AIG.
  These can be installed as packages of the same name e.g. under Ubuntun 22.04.
  
- [+] `nuXmv`: either as an FSM model checker or asa timed automata model checker for comparing performances.
  This software is proprietary, and can be downloaded and used for academic and research purposes:

  https://nuxmv.fbk.eu/

- [+] The proprietary software Uppaal and Uppaal-TIGA, `verifyta` and `verifytga` programs in order to reproduce the experiments from the paper:

  https://www.uppaal.org

Binaries for all these programs are included in this repository except for [*] which are free software and can be installed easily on Linux;
and [+] which are proprietary software and are only used for comparing the present algorithm to those model checkers.

Concretely, the only installation step consists in installing yosys and berkeley-abc. Note that these are ony used for the synthesis algorithm.
The verification algorithm works out of the box.

To compile, run 

    sbt assembly

## Usage
Currently two safety verification algorithms are implemented.

- `learning`: a DFA learning algorithm is run to learn the timed automaton model
applies compositional verification.

- `synth`: controller synthesis algrithm.

A script is provided to run examples. To run the verification algorithm, try

    ./run.sh verify resources/examples/ftsp/ftsp-abs-2-fsm.smv resources/examples/ftsp/ftsp-abs-2-ta.ta 

For the synthesis algorithm, try:

    ./run.sh synthesize resources/examples/synthesis/scheduling/sched_bitcounter64.v resources/examples/synthesis/scheduling/scheda.ta

## Modeling
- In verification problems, the FSM is given in the SMV format. The alphabet is defined
  with DEFINEs that are prefixed with `_rt_`. For instance, `_rt_event` is true iff the next transition is to be
  synchronized with a TA edge labeled by `event`. 
- These predicates must be pairwise disjoint: at each instant, at most one label must be true. This is not checked by the program,
  so it is the user's responsibility.
- A single error define or variable must be used to mark the error, and the invariant must be exactly `INVARSPEC !error`.

Check out the resources/examples directory.