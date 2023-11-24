#!/bin/bash
MEMLIM=8000000
TIMELIM=1800
ulimit -m ${MEMLIM} -v ${MEMLIM} -t ${TIMELIM}
benchs=(broadcast_amba_a.tsmv
broadcast_amba_b.tsmv
broadcast_amba_c.tsmv
broadcast_amba_d.tsmv)
for b in ${benchs[@]}; do
	echo $b	| tee log_nuxm 2>&1
	time nuXmv-time.sh $b | tee log_nuxmv 2>&1
done
