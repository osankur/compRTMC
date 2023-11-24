#!/bin/bash
MEMLIM=8G
TIMELIM=1800
ulimit -t ${TIMELIM}
benchs=(
sts-2-fsm.smv
sts-2-ta.ta
sts-3-fsm.smv
sts-3-ta.ta
)
for i in $(seq 0 2 $((${#benchs[@]} - 1)))
do
     fsm=${benchs[$i]}
     ta=${benchs[$i+1]}
     echo $fsm $ta | tee -a log_comp
     time ../../../run.sh verify $fsm $ta 2>&1 | tee -a log_comp
done
