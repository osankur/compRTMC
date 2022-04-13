for n in `seq 2 3`
do
    for i in `seq 0 4`
    do
        python3 leader-easy.py -n $n -i $i --timedSMV > leader_n${n}_i${i}.tsmv
        python3 leader-easy.py -n $n -i $i > leader_n${n}_i${i}-fsm.smv 2> leader_n${n}_i${i}-ta.ta
    done
done
