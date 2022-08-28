#!/bin/bash
MEMLIM=8000000
TIMELIM=1800
ulimit -m ${MEMLIM} -v ${MEMLIM} -t ${TIMELIM}
for b in *.tsmv; do
	echo $b	| tee -a log_nuxmv
	time nuXmv-time.sh $b 2>&1 | tee -a log_nuxmv
done
