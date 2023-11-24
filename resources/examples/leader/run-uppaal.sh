#!/bin/bash
MEMLIM=8000000
TIMELIM=1800
ulimit -m ${MEMLIM} -v ${MEMLIM} -t ${TIMELIM}
benchs=(leader_stateless_a.xml leader_stateless_b.xml leader_stateless_c.xml leader_stateless_d.xml leader_stay_a.xml leader_stay_b.xml leader_stay_c.xml leader_stay_d.xml)
for b in ${benchs[@]}; do
	echo $b	| tee -a log_uppaal
	time verifyta $b 2>&1 | tee -a log_uppaal 
done
