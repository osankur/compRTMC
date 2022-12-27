#!/bin/bash
MEMLIM=8G
TIMELIM=1800
ulimit -t ${TIMELIM}
benchs=(
sched_bitcounter64.v scheda.ta
sched_bitcounter64.v schedb.ta
sched_bitcounter64.v schedc.ta
sched_genbuf2f3yunrealy.v schedd.ta
sched_genbuf2f3yunrealy.v schede.ta
sched_genbuf2f3yunrealy.v schedf.ta
)
for i in $(seq 0 2 $((${#benchs[@]} - 1)))
do
     fsm=${benchs[$i]}
     ta=${benchs[$i+1]}
     echo $fsm $ta | tee -a log_comp
     ../../../../run.sh synthesize $fsm $ta 2>&1 | tee -a log_comp
done
