python3 leader.py -n 2 -i 0 --timedSMV > leader2a.tsmv
python3 leader.py -n 2 -i 1 --timedSMV > leader2b.tsmv
python3 leader.py -n 2 -i 0 > leader2a-fsm.smv 2> leader2a-ta.ta
python3 leader.py -n 2 -i 1 > leader2b-fsm.smv 2> leader2b-ta.ta

python3 leader.py -n 3 -i 0 --timedSMV > leader3a.tsmv
python3 leader.py -n 3 -i 1 --timedSMV > leader3b.tsmv
python3 leader.py -n 3 -i 0 > leader3a-fsm.smv 2> leader3a-ta.ta
python3 leader.py -n 3 -i 1 > leader3b-fsm.smv 2> leader3b-ta.ta
