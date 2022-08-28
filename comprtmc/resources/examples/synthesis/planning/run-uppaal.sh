#!/bin/bash
MEMLIM=8000000
TIMELIM=1800
ulimit -m ${MEMLIM} -v ${MEMLIM} -t ${TIMELIM}
benchs=(
planning_genbuf_a.xml
planning_genbuf_b.xml
planning_genbuf_c.xml
planning_stateless_d.xml
planning_stateless_e.xml
planning_stateless_f.xml
)
for b in ${benchs[@]}; do
	echo $b	2>&1 | tee -a log_uppaal
	time verifytga $b planning.q 2>&1 | tee -a log_uppaal
done
