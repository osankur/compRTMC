set -e
./run.sh verify resources/examples/ftsp/ftsp-abs-2-fsm.smv resources/examples/ftsp/ftsp-abs-2-ta.ta
sleep 2
./run.sh verify resources/examples/rt-broadcast/broadcast_amba.smv resources/examples/rt-broadcast/broadcast_amba_a.ta
sleep 2
./run.sh verify resources/examples/prio_scheduling/prio_sched_2a.smv resources/examples/prio_scheduling/prio_sched_2.ta
sleep 2
./run.sh synthesize resources/examples/synthesis/planning/planning_stateless.v resources/examples/synthesis/planning/planning_a.ta
sleep 2
./run.sh synthesize resources/examples/synthesis/scheduling/sched_bitcounter64.v resources/examples/synthesis/scheduling/scheda.ta


