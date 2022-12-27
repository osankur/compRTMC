#!/bin/bash
MEMLIM=8G
TIMELIM=1800
ulimit -t ${TIMELIM}
benchs=(
prio_sched_2a.smv prio_sched_2.ta
prio_sched_2b.smv prio_sched_2.ta
prio_sched_3.smv prio_sched_3c.ta
prio_sched_3.smv prio_sched_3d.ta
prio_sched_3.smv prio_sched_3e.ta
)
for i in $(seq 0 2 $((${#benchs[@]} - 1)))
do
     fsm=${benchs[$i]}
     ta=${benchs[$i+1]}
     echo $fsm $ta | tee -a log_comp
     ../../../run.sh verify $fsm $ta 2>&1 | tee -a log_comp
done
