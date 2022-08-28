#!/bin/bash
MEMLIM=8000000
TIMELIM=1800
ulimit -m ${MEMLIM} -v ${MEMLIM} -t ${TIMELIM}
benchs=(broadcast_amba_a.xml
broadcast_amba_b.xml
broadcast_amba_c.xml
broadcast_amba_d.xml)
for b in ${benchs[@]}; do
	echo $b	| tee -a log_uppaal
	time verifyta $b  2>&1| tee -a log_uppaal
done
