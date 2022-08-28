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
	echo $b	| tee log_uppaal 2>&1
	time verifyta $b | tee log_uppaal 2>&1
done
