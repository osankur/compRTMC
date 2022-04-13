This folder contains instances of the STS model from the tchecker benchmarks database: https://github.com/ticktac-project/benchmarks
The script sts.sh is modified so that it produces a network of TAs with only a single timed component called rtconstraints,
while the other components are untimed. The behaviors are however identical to the original model.

- `sts-X.ta` : The timed automaton model output by sts.sh in which the process rtconstraints is the unique timed one. The specification is that
  locations with label `error` must not be reached.
- `sts-X-fsm.ta` : The FSM part of the timed automaton model obtained manually from `sts-X.ta`. Here, the timed process rtconstraints is removed,
  and the synchronization labels are marked.
- `sts-X-ta.ta` : The timed component of `sts-X.ta`
  
The model can be verified by providing `sts-X-fsm.ta` as the FSM file, and `sts-X-ta.ta` as the TA file using TChecker as both FSM and TA model checkers.

There are also the following files
- `sts-X-fsm-p.ta` : Same as `sts-2-fsm.ta` but as a single process computed by `tck-syntax -p`
- `sts-2-fsm.smv` : The FSM model obtained from `sts-2-fsm-p.ta` using `tck-convert`.
  The sync labels of the model was manually added using the script `extract-sync-labels.scala`
  which reads the edge information printed as comments at the end of the smv file.
  The error state was also added manually, using the `to_err` event (which is not in sync alphabet)

- `sts-2.tsmv` : nuXmv timed automaton model obtained using `tck-convert` from the single process model
  given by `tck-convert` applied to `sts-X.ta`.

Current performance is:
- `sts-2-fsm.ta` and `sts-2-ta.ta`: 2m (and ~30s with covreach)
- `sts-2-fsm.smv` and `sts-2-ta.ta`: 7s
- `sts-3-fsm.ta` and `sts-3-ta.ta`: ????
- `sts-2.xml` : 20s (Uppaal)
- `sts-3.xml` : ???? (Uppaal)
- `sts-2.tsmv` : > 10m, probably times out (?)