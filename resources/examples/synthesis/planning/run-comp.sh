#!/bin/bash
MEMLIM=8G
TIMELIM=1800
ulimit -t ${TIMELIM}
benchs=(
planning_stateless.v planning_a.ta
planning_stateless.v planning_b.ta
planning_stateless.v planning_c.ta
planning_genbuf.v planning_a.ta
planning_genbuf.v planning_b.ta
planning_genbuf.v planning_c.ta
)
for i in $(seq 0 2 $((${#benchs[@]} - 1)))
do
     fsm=${benchs[$i]}
     ta=${benchs[$i+1]}
     echo $fsm $ta | tee -a log_comp
     time ../../../../run.sh synthesize $fsm $ta 2>&1 | tee -a log_comp
done
