#!/bin/bash
MEMLIM=8G
TIMELIM=1800
ulimit -t ${TIMELIM}
benchs=(
broadcast_amba.smv broadcast_amba_a.ta
broadcast_amba.smv broadcast_amba_b.ta
broadcast_amba.smv broadcast_amba_c.ta
broadcast_amba.smv broadcast_amba_d.ta
)
for i in $(seq 0 2 $((${#benchs[@]} - 1)))
do
     fsm=${benchs[$i]}
     ta=${benchs[$i+1]}
     echo $fsm $ta | tee -a log_comp
     ../../../run.sh verify $fsm $ta 2>&1 | tee -a log_comp
done
