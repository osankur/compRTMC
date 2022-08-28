#!/bin/bash
MEMLIM=8000000
TIMELIM=1800
ulimit -m ${MEMLIM} -v ${MEMLIM} -t ${TIMELIM}
benchs=(leader_n3_stateless_a.tsmv leader_n3_stateless_b.tsmv leader_n3_stateless_c.tsmv leader_n3_stateless_d.tsmv leader_n3_stay_a.tsmv leader_n3_stay_b.tsmv leader_n3_stay_c.tsmv leader_n3_stay_d.tsmv)
for b in ${benchs[@]}; do
	echo $b 2>&1 >> log_nuxmv
	time nuXmv-time.sh $b 2>&1 >> log_nuxmv 
done
