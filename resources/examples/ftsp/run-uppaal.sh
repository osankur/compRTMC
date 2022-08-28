#!/bin/bash
MEMLIM=8000000
TIMELIM=1800
ulimit -m ${MEMLIM} -v ${MEMLIM} -t ${TIMELIM}
benchs=(
ftsp-2-abs.xml
ftsp-3-abs.xml
ftsp-4-abs.xml
)
for b in ${benchs[@]}; do
	echo $b	| tee -a log_uppaal
	time verifyta $b 2>&1 | tee -a log_uppaal
done
