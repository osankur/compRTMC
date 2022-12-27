#!/bin/bash
MEMLIM=8000000
TIMELIM=1800
ulimit -m ${MEMLIM} -v ${MEMLIM} -t ${TIMELIM}
benchs=(
sched_bitcounter64_d.xml
sched_bitcounter64_e.xml
sched_bitcounter64_f.xml
sched_genbuf2f3yunrealy_a.xml
sched_genbuf2f3yunrealy_b.xml
sched_genbuf2f3yunrealy_c.xml
)
for b in ${benchs[@]}; do
	echo $b	2>&1 | tee -a log_uppaal
	time verifytga $b sched.q 2>&1 | tee -a log_uppaal
done
