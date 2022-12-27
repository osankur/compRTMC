#!/bin/bash
MEMLIM=8000000
TIMELIM=1800
ulimit -m ${MEMLIM} -v ${MEMLIM} -t ${TIMELIM}
benchs=
(
sts-2.xml
sts-3.xml
)
for b in ${benchs[@]}; do
	echo $b	2>&1 | tee -a log_uppaal
	time verifyta $b | tee -a log_uppaal
done
