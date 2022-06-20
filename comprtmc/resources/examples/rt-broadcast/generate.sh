python3 generate_bit_counter_broadcast.py -n 2 -m 2 -i 0 > broadcast_2_2_a-fsm.smv 2> broadcast_2_2_a-ta.ta
python3 generate_bit_counter_broadcast.py -n 2 -m 2 -i 0 -t > broadcast_2_2_a.tsmv

python3 generate_bit_counter_broadcast.py -n 2 -m 2 -i 1 > broadcast_2_2_b-fsm.smv 2> broadcast_2_2_b-ta.ta
python3 generate_bit_counter_broadcast.py -n 2 -m 2 -i 1 -t > broadcast_2_2_b.tsmv

python3 generate_bit_counter_broadcast.py -n 2 -m 2 -i 2 > broadcast_2_2_c-fsm.smv 2> broadcast_2_2_c-ta.ta
python3 generate_bit_counter_broadcast.py -n 2 -m 2 -i 2 -t > broadcast_2_2_c.tsmv

python3 generate_bit_counter_broadcast.py -n 2 -m 2 -i 3 > broadcast_2_2_d-fsm.smv 2> broadcast_2_2_d-ta.ta
python3 generate_bit_counter_broadcast.py -n 2 -m 2 -i 3 -t > broadcast_2_2_d.tsmv

python3 generate_bit_counter_broadcast.py -n 3 -m 3 -i 3 > broadcast_3_3_d-fsm.smv 2> broadcast_3_3_d-ta.ta
python3 generate_bit_counter_broadcast.py -n 3 -m 3 -i 3 -t > broadcast_3_3_d.tsmv
