#!/bin/bash

# This file is a part of the TickTac benchmarks project.
#
# See files AUTHORS and LICENSE for copyright details.

# Check parameters

CYCLE=1
MUTEX_DELAY=2

function usage() {
    echo "Usage: $0 N";
    echo "       $0 N CYCLE MUTEX_DELAY";
    echo "       N number of processes";
    echo "       CYCLE time of a CYCLE in a node"
    echo "       MUTEX_DELAY delay of a mutex control"
}

if [ $# -eq 1 ]; then
    N=$1
elif [ $# -eq 3 ]; then
    N=$1
    CYCLE=$2
    MUTEX_DELAY=$3
else
    usage
    exit 1
fi

# Model

echo "#clock:size:name
#int:size:min:max:init:name
#process:name
#event:name
#location:process:name{attributes}
#edge:process:source:target:event:{attributes}
#sync:events
#   where
#   attributes is a colon-separated list of key:value
#   events is a colon-separated list of process@event
"

echo "# Inspired from the model introduced in Section 3.3.3 in
# Henning Dierks; 'Time; Abstraction and Heuristics';
# Habilitation Thesis; Department of Computer Science;
# University of Oldenburg; 2005 (http://www.avacs.org/Publikationen/Open/dierks05.pdf)
"

echo "
# Mutual exclusion can be verified by checking that there cannot be two 
# processes A in location Go at the same time.
"

echo "system:sts_${N}_${CYCLE}_$MUTEX_DELAY
"

# Events

echo "event:tau
event:go
event:err"
for pid in `seq 1 $N`; do
echo "event:set_drive$pid
event:set_not_drive$pid
event:poll_EC$pid
event:poll_not_EC$pid
event:poll_EC_drive$pid
event:poll_not_EC_drive$pid
event:poll_EC_not_drive$pid
event:poll_not_EC_not_drive$pid"
done

echo "# _rt_alphabet"
echo "event:set_drive
event:cpoll
event:tau_reset_z
event:tau_yleq$MUTEX_DELAY
event:tau_ygt$MUTEX_DELAY
event:tau_reset_yz
"
for pid in `seq 1 $N`; do
echo "event:poll$pid
event:tau_reset_z$pid"
done

# Processes

echo "process:rtconstraints
clock:1:x
clock:1:y
clock:1:z"

for pid in `seq 1 $N`; do
    echo "clock:1:z$pid"
done

echo "location:rtconstraints:init{initial::invariant: z < 1 "
for pid in `seq 1 $N`; do
    echo -n "&& z$pid < $CYCLE"
done
echo "}"
echo "edge:rtconstraints:init:init:go{provided:x==0}
edge:rtconstraints:init:init:set_drive{do:x=0;y=0;z=0}
edge:rtconstraints:init:init:cpoll{provided:x>0 && z>0}
edge:rtconstraints:init:init:tau_reset_yz{do:y=0;z=0}
edge:rtconstraints:init:init:tau_yleq2{provided:y<=2}
edge:rtconstraints:init:init:tau_ygt2{provided:y>2}
edge:rtconstraints:init:init:tau_reset_z{do:z=0}"
for pid in `seq 1 $N`; do
echo "edge:rtconstraints:init:init:poll$pid{provided:x>0 && z$pid > 0}"
echo "edge:rtconstraints:init:init:tau_reset_z$pid{do:z$pid=0}"
done


echo "
# Process InOut
process:InOut"
for pid in `seq 1 $N`; do
    echo "int:1:0:1:0:EC$pid
int:1:0:1:0:drive$pid"
done
echo "location:InOut:init{initial:}
location:InOut:L"
echo "edge:InOut:init:L:go"
for pid in `seq 1 $N`; do
    echo "edge:InOut:L:L:set_drive$pid{do:drive$pid=1}
edge:InOut:L:L:set_not_drive$pid{do:drive$pid=0}
edge:InOut:L:L:tau{do:EC$pid=1}
edge:InOut:L:L:tau{do:EC$pid=0}
edge:InOut:L:L:poll_EC$pid{provided:EC$pid==1}
edge:InOut:L:L:poll_not_EC$pid{provided:EC$pid==0}
edge:InOut:L:L:poll_EC_drive$pid{provided:EC$pid==1&&drive$pid==1}
edge:InOut:L:L:poll_not_EC_drive$pid{provided:EC$pid==0&&drive$pid==1}
edge:InOut:L:L:poll_EC_not_drive$pid{provided:EC$pid==1&&drive$pid==0}
edge:InOut:L:L:poll_not_EC_not_drive$pid{provided:EC$pid==0&&drive$pid==0}"
done

for pid in `seq 1 $N`; do
    echo "
# Process A$pid
process:A$pid
# clock:1:y$pid
#clock:1:z$pid
int:1:0:1:0:polled_drive$pid
int:1:0:1:0:polled_EC_A$pid
int:1:0:3:0:pc$pid
location:A$pid:init{initial:}
location:A$pid:Stop
location:A$pid:Wait
location:A$pid:Go
location:A$pid:Err{labels:err$pid}
edge:A$pid:init:Stop:go
edge:A$pid:Stop:Stop:poll_EC_drive$pid{provided:pc$pid==0: do:pc$pid=1;polled_EC_A$pid=1;polled_drive$pid=1}
edge:A$pid:Stop:Stop:poll_not_EC_drive$pid{provided:pc$pid==0: do:pc$pid=1;polled_EC_A$pid=0;polled_drive$pid=1}
edge:A$pid:Stop:Stop:poll_EC_not_drive$pid{provided:pc$pid==0: do:pc$pid=1;polled_EC_A$pid=1;polled_drive$pid=0}
edge:A$pid:Stop:Stop:poll_not_EC_not_drive$pid{provided:pc$pid==0: do:pc$pid=1;polled_EC_A$pid=0;polled_drive$pid=0}
edge:A$pid:Stop:Stop:tau{provided:pc$pid==1 : do:pc$pid=3}
edge:A$pid:Stop:Stop:tau_reset_z$pid{provided:pc$pid==3&&polled_EC_A$pid==0 : do:pc$pid=0;}
edge:A$pid:Stop:Wait:tau_reset_z$pid{provided:pc$pid==3&&polled_EC_A$pid==1 : do:pc$pid=0;}
edge:A$pid:Wait:Wait:poll_EC_drive$pid{provided:pc$pid==0: do:pc$pid=1;polled_EC_A$pid=1;polled_drive$pid=1}
edge:A$pid:Wait:Wait:poll_EC_not_drive$pid{provided:pc$pid==0: do:pc$pid=1;polled_EC_A$pid=1;polled_drive$pid=0}
edge:A$pid:Wait:Wait:poll_not_EC_drive$pid{provided:pc$pid==0: do:pc$pid=1;polled_EC_A$pid=0;polled_drive$pid=1}
edge:A$pid:Wait:Wait:poll_not_EC_not_drive$pid{provided:pc$pid==0: do:pc$pid=1;polled_EC_A$pid=0;polled_drive$pid=0}
edge:A$pid:Wait:Wait:tau{provided:pc$pid==1 : do:pc$pid=3}
edge:A$pid:Wait:Wait:tau_reset_z$pid{provided:pc$pid==3&&polled_EC_A$pid==0 : do:pc$pid=0}
edge:A$pid:Wait:Wait:tau_reset_z$pid{provided:pc$pid==3&&polled_drive$pid==0 : do:pc$pid=0}
edge:A$pid:Wait:Go:tau_reset_z$pid{provided:pc$pid==3&&polled_EC_A$pid==1&&polled_drive$pid==1 : do:pc$pid=0;}
edge:A$pid:Go:Go:poll_EC_drive$pid{provided:pc$pid==0: do:pc$pid=1;polled_EC_A$pid=1;polled_drive$pid=1}
edge:A$pid:Go:Go:poll_EC_not_drive$pid{provided:pc$pid==0: do:pc$pid=1;polled_EC_A$pid=1;polled_drive$pid=0}
edge:A$pid:Go:Go:poll_not_EC_drive$pid{provided:pc$pid==0: do:pc$pid=1;polled_EC_A$pid=0;polled_drive$pid=1}
edge:A$pid:Go:Go:poll_not_EC_not_drive$pid{provided:pc$pid==0: do:pc$pid=1;polled_EC_A$pid=0;polled_drive$pid=0}
edge:A$pid:Go:Go:tau{provided:pc$pid==1 : do:pc$pid=3}
edge:A$pid:Go:Go:tau_reset_z$pid{provided:pc$pid==3&&polled_drive$pid==1 : do:pc$pid=0;}
edge:A$pid:Go:Stop:tau_reset_z$pid{provided:pc$pid==3&&polled_drive$pid==0 : do:pc$pid=0;}
edge:A$pid:Go:Err:err"
done

echo "
# Process Ctrl
process:Ctrl
#clock:1:y
#clock:1:z
int:1:0:3:0:pc"
for pid in `seq 1 $N`; do
    echo "int:1:0:1:0:polled_EC$pid"
done
echo "location:Ctrl:init{initial:}"
for pid in `seq 1 $N`; do
echo "location:Ctrl:Ready$pid
location:Ctrl:Drive$pid
location:Ctrl:Fin$pid"
done
echo "edge:Ctrl:init:Ready$pid:go"
for pid in `seq 1 $N`; do
    echo "edge:Ctrl:Ready$pid:Ready$pid:poll_EC$pid{provided:pc==0: do:pc=1;polled_EC$pid=1}
edge:Ctrl:Ready$pid:Ready$pid:poll_not_EC$pid{provided:pc==0: do:pc=1;polled_EC$pid=0}
edge:Ctrl:Ready$pid:Ready$pid:poll_EC$((($pid%N)+1)){provided:pc==0: do:pc=1;polled_EC$((($pid%N)+1))=1}
edge:Ctrl:Ready$pid:Ready$pid:poll_not_EC$((($pid%N)+1)){provided:pc==0: do:pc=1;polled_EC$((($pid%N)+1))=0}
edge:Ctrl:Ready$pid:Ready$pid:tau_reset_z{provided:pc==3&&polled_EC$pid==0 : do:pc=0}
edge:Ctrl:Ready$pid:Ready$pid:tau{provided:pc==1 : do:pc=3}
edge:Ctrl:Ready$pid:Ready$((($pid%N)+1)):tau_reset_z{provided:pc==3&&polled_EC$((($pid%N)+1))==1&&polled_EC$pid==0 : do:pc=0}
edge:Ctrl:Ready$pid:Drive$pid:set_drive$pid{provided:pc==3&&polled_EC$pid==1 : do:pc=0}
edge:Ctrl:Drive$pid:Drive$pid:poll_EC$pid{provided:pc==0: do:pc=1;polled_EC$pid=1}
edge:Ctrl:Drive$pid:Drive$pid:poll_not_EC$pid{provided:pc==0: do:pc=1;polled_EC$pid=0}
edge:Ctrl:Drive$pid:Drive$pid:tau{provided:pc==1 : do:pc=3}
edge:Ctrl:Drive$pid:Drive$pid:tau_reset_z{provided:pc==3&&polled_EC$pid==1 : do:pc=0}
edge:Ctrl:Drive$pid:Fin$pid:set_not_drive$pid{provided:pc==3&&polled_EC$pid==0 : do:pc=0}
edge:Ctrl:Fin$pid:Fin$pid:poll_EC$pid{provided:pc==0: do:pc=1;polled_EC$pid=1}
edge:Ctrl:Fin$pid:Fin$pid:poll_not_EC$pid{provided:pc==0: do:pc=1;polled_EC$pid=0}
edge:Ctrl:Fin$pid:Fin$pid:tau_yleq$MUTEX_DELAY{provided:pc==1 : do:pc=2}
edge:Ctrl:Fin$pid:Fin$pid:tau_reset_z{provided:pc==2 : do:pc=0}
edge:Ctrl:Fin$pid:Fin$pid:tau_ygt$MUTEX_DELAY{provided:pc==1 : do:pc=3}
edge:Ctrl:Fin$pid:Fin$pid:tau_reset_z{provided:pc==3&&polled_EC$pid==0 : do:pc=0}
edge:Ctrl:Fin$pid:Ready$((($pid%N)+1)):tau_reset_yz{provided:pc==3&&polled_EC$pid==1 : do:pc=0}"
done

echo "
"

# Synchros
for pid in `seq 1 $N`; do
    echo "sync:Ctrl@poll_EC$pid:InOut@poll_EC$pid:rtconstraints@cpoll
sync:Ctrl@poll_not_EC$pid:InOut@poll_not_EC$pid:rtconstraints@cpoll
sync:Ctrl@set_drive$pid:InOut@set_drive$pid:rtconstraints@set_drive
sync:Ctrl@set_not_drive$pid:InOut@set_not_drive$pid:rtconstraints@set_drive
sync:A$pid@poll_EC_drive$pid:InOut@poll_EC_drive$pid:rtconstraints@poll$pid
sync:A$pid@poll_EC_not_drive$pid:InOut@poll_EC_not_drive$pid:rtconstraints@poll$pid
sync:A$pid@poll_not_EC_drive$pid:InOut@poll_not_EC_drive$pid:rtconstraints@poll$pid
sync:A$pid@poll_not_EC_not_drive$pid:InOut@poll_not_EC_not_drive$pid:rtconstraints@poll$pid"
done
echo -n "sync:InOut@go:Ctrl@go:rtconstraints@go"
for pid in `seq 1 $N`; do
    echo -n ":A$pid@go"
done
echo ""
echo "sync:A1@err:A2@err"
