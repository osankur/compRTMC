#!/bin/bash
MEMLIM=8000000
TIMELIM=1800
ulimit -m ${MEMLIM} -v ${MEMLIM} -t ${TIMELIM}
for b in *.xml; do
	echo $b	| tee -a log_uppaal
	time verifyta $b 2>&1 | tee -a log_uppaal
done
