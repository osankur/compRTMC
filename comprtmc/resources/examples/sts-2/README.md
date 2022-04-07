This file contains the sts-2 model from the tchecker benchmarks database. The model was modified
in order to keep a single timed component called rtconstraints, while the other components are untimed.

- sts-2.ta : The timed automaton model in which the process rtconstraints is the unique timed one
- sts-2-fsm.ta : The FSM part of the timed automaton model (in which the timed process rtconstraints is removed).
- sts-2-fsm-p.ta : Same as `sts-2-fsm.ta` but single process computed by `tck-syntax -p`
- sts-2-ta.ta : Separate timed component

- sts-2-fsm.smv : The FSM model obtained from `sts-2-fsm-p.ta` using `tck-convert`.
  The sync labels of the model was manually added using the script `extract-sync-labels.scala`
  which reads the edge information printed as comments at the end of the smv file.
  The error state was also added manually, using the to_err event (which is not sync'ed)

- sts-2.tsmv : nuXmv timed automaton model
