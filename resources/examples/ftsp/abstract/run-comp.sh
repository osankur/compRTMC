#!/bin/bash
MEMLIM=8G
TIMELIM=1800
ulimit -t ${TIMELIM}
benchs=(
ftsp-abs-2-fsm.smv ftsp-abs-2-ta.ta
ftsp-abs-3-fsm.smv ftsp-abs-3-ta.ta
ftsp-abs-4-fsm.smv ftsp-abs-4-ta.ta
)
for i in $(seq 0 2 $((${#benchs[@]} - 1)))
do
     fsm=${benchs[$i]}
     ta=${benchs[$i+1]}
     echo $fsm $ta | tee -a log_comp
     ../../../run.sh verify $fsm $ta 2>&1 | tee -a log_comp
done
