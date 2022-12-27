#!/bin/bash
MEMLIM=8000000
TIMELIM=1800
ulimit -m ${MEMLIM} -v ${MEMLIM} -t ${TIMELIM}
benchs=(
sts-2.tsmv
sts-3.tsmv
)
for b in ${benchs[@]}; do
	echo $b 2>&1 >> log_nuxmv
	time nuXmv-time.sh $b 2>&1 >> log_nuxmv 
done
