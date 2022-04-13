# This scripts generates an SMV model inspired from the abstract FTSP model used
# in the incremental verification method of the Sankur, Talpin TACAS 2017 paper.
# The model contains n concrete processes with the following identifiers:
#   1 -- 2 -- 3 -- 4 -- ... -- n
# The n-1 first processes are assumed to have stabilized: their myroot variable is already 1,
# and the and numentries=4. The last process (with id n) is a concrete process nondeterministically
# initialized at an arbitrary state.
#
# The safety specification !err is that after max_counter periods of process 1,
# all processes must agree that 1 is the root.
# This does not hold without a timed model since the system is completely asynchronous.
# The timed model introduces quasi-synchrony.

import sys

if len(sys.argv) < 2:
    print("Usage: python3 ftsp-abstract.py <n> [--timed]")
    sys.exit(-1)

# Whether we should output a timed SMV model or an untimed one
timed = False
if (len(sys.argv)>2 and sys.argv[2] == "--timed"):
	timed = True
# In the timed model, the duration interval for each period
period = [9,11]

n=int(sys.argv[1]) # number of processes
max_heartbeats = 8 # number of periods before timeout
max_numentries = 4 # number of messages received before starting broadcast
max_myseq = 10     # max of the msg seq numbers
hb_threshold = 3   # when becoming root, wait for a few periods before broadcasting
max_counter = 20   # number of periods of process 1 after which convergence is to be observed

if timed:
	print("@TIME_DOMAIN continuous")

print("""MODULE stableproc(id, wake_up, receiving, msg_root, msg_seqn, normalize)
VAR
	state : {idle, broadcasting};
""")
print(F"""
	-- hb : 0..{max_heartbeats};
	myseq : 0..{max_myseq};
INIT
	-- 0 <= hb & hb <= 4 &
	0 <= myseq & myseq <= 2
ASSIGN
	init(state) := idle;
	next(state) := case
		wake_up & state=idle : broadcasting;
		state=broadcasting : idle;
        TRUE : idle;
	esac;
	-- next(hb) := case
	--	wake_up & hb < {max_heartbeats} : hb + 1;
	--	receiving & !ignoreMsg & msg_root < id : 0;
	--	TRUE : hb;
	--esac;
	next(myseq) := case
		state = broadcasting & 1 = id & myseq < {max_heartbeats} : myseq + 1;
		receiving & !ignoreMsg : msg_seqn;
		normalize & (myseq >= 1) : myseq - 1; -- Normalization
		TRUE : myseq;
	esac;
DEFINE
	ignoreMsg := (msg_root > 1
					| 1 = id -- & hb < {hb_threshold}
					| msg_root = 1 & msg_seqn <= myseq);
					""")

print("""MODULE lastproc(id, wake_up, receiving, msg_root, msg_seqn, normalize)
VAR
	state : {idle, broadcasting};
""")
print(F"""
	hb : 0..{max_heartbeats};
	myroot : 1..{n+1};
	numentries : 0..{max_numentries};
	myseq : 0..{max_myseq};
ASSIGN
	next(state) := case
		wake_up & state=idle & (next(myroot) = id | myroot = id | numentries >= {max_numentries}) : broadcasting;
		state=broadcasting : idle;
        TRUE : idle;
	esac;
	next(hb) := case
		wake_up & hb < {max_heartbeats} : hb + 1;
		receiving & !ignoreMsg & msg_root < id : 0;
		TRUE : hb;
	esac;
	next(myroot) := case
		wake_up & myroot != id & hb >= {max_heartbeats} : id;
		receiving & msg_root < myroot : msg_root;
		TRUE : myroot;
	esac;
	next(numentries) := case
		receiving & !ignoreMsg & numentries < {max_numentries} : numentries + 1;
		TRUE : numentries;
	esac;
	next(myseq) := case
		state = broadcasting & myroot = id & myseq < {max_heartbeats} : myseq + 1;
		receiving & !ignoreMsg : msg_seqn;
		normalize & (myseq >= 1) : myseq - 1; -- Normalization
		TRUE : myseq;
	esac;
DEFINE
	ignoreMsg := (msg_root > myroot 
					| myroot = id & hb < {hb_threshold}
					| msg_root = myroot & msg_seqn <= myseq);
					""")
print(F"""
MODULE Others
IVAR
    msg_root : 2..{n+1}; -- Others can only send messages not coming from root.
    msg_seq : 0..{max_myseq}; -- with arbitrary seq numbers
""")
print("""
MODULE main
IVAR""")
for i in range(1,n+1):
	print(F"    others_sending_to_{i} : boolean;")
print("""
VAR
    cmd : {idle, decrement_seqn, others""",end="")
for i in range(n):
    print(F", wake{i+1}",end="")
print("};\n" + F"\t counter : 0..{max_counter};")
for i in range(n-1):
    print(F"proc{i+1} : stableproc({i+1}, _rt_wakeup{i+1}, receiving{i+1}, msg_root, msg_seqn, cmd = decrement_seqn);")
print(F"proc{n} : lastproc({n}, _rt_wakeup{n}, receiving{n}, msg_root, msg_seqn, cmd = decrement_seqn);")
print("oth : Others;")
if timed:
	for i in range(1,n+1):
		print(F"x{i} : clock;")	
print(F"""ASSIGN
    init(counter) := 0;
    next(counter) := case
        cmd = wake1 & counter < {max_counter} : counter + 1;
        TRUE : counter;
    esac;""")
if timed:
	for i in range(1, n+1):
		print(F"""\tinit(x{i}) := 0;
    	next(x{i}) := case
        cmd = wake{i} : 0;
        TRUE : x{i};
    	esac;""")
print("""INVAR
	(someone_broadcasting -> cmd = idle)""")
decrement_condition = " & ".join(map(lambda x: F"proc{x}.myseq >= 1", range(1,n+1)))
print(F"""\t& (!someone_broadcasting & (cmd != others) &  {decrement_condition} -> cmd = decrement_seqn)
	& (cmd = decrement_seqn -> {decrement_condition})""")
if timed:
	for i in range(1,n+1):
		print(F"    & (cmd = wake{i} -> x{i} >= {period[0]})")
	upInv = " & ".join(map(lambda x: F"x{x} <= {period[1]}", range(1,n+1)))
	print(F"& (TRUE -> {upInv})")
print("DEFINE")
#not_yet_converged = " | ".join(map(lambda x: F"proc{x}.myroot != 1", range(1,n+1)))
print(F"""\terr := counter = {max_counter} & (proc{n}.myroot != 1);""")
for i in range(1,n+1):
	if not timed:
		print(F"\t_rt_wakeup{i} := cmd = wake{i};")
	else:
		print(F"\t_rt_wakeup{i} := x{i} >= {period[0]} & cmd = wake{i};")
for i in range(1,n+1):
    neighbors = []
    if ( i + 1 <= n):
        neighbors.append(i+1)
    if (i - 1 >= 1):
        neighbors.append(i-1)
    expr = " | ".join(map(lambda x: F"proc{x}.state = broadcasting", neighbors))
    print(F"\treceiving{i} := {expr} | (cmd = others & others_sending_to_{i});")
print("\tmsg_root := case")
for i in range(1,n):
    print(F"        proc{i}.state = broadcasting : 1;")
print(F"        proc{n}.state = broadcasting : proc{n}.myroot;")
print("    cmd = others : oth.msg_root;")
print("    TRUE : 3;\n\tesac;")
print("    msg_seqn := case")
for i in range(1,n+1):
    print(F"        proc{i}.state = broadcasting : proc{i}.myseq;")
print("        cmd = others : oth.msg_seq;")

someone_broadcasting = " | ".join(map(lambda x: F"proc{x}.state = broadcasting", range(1,n+1)))
print(F"""        TRUE : {max_myseq};
    esac;
    someone_broadcasting := {someone_broadcasting};
INVARSPEC !err
""")