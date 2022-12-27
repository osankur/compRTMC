#!/bin/bash
MEMLIM=8G
TIMELIM=1800
ulimit -t ${TIMELIM}
benchs=(
leader_n3_stateless.smv leader_n3_a-ta.ta
leader_n3_stateless.smv leader_n3_b-ta.ta
leader_n3_stateless.smv leader_n3_c-ta.ta
leader_n3_stateless.smv leader_n3_d-ta.ta
leader_stay_n3.smv leader_n3_a-ta.ta
leader_stay_n3.smv leader_n3_b-ta.ta
leader_stay_n3.smv leader_n3_c-ta.ta
leader_stay_n3.smv leader_n3_d-ta.ta
)
for i in $(seq 0 2 $((${#benchs[@]} - 1)))
do
     fsm=${benchs[$i]}
     ta=${benchs[$i+1]}
     echo $fsm $ta | tee -a log_comp
     time ../../../run.sh verify $fsm $ta 2>&1 | tee -a log_comp
done
