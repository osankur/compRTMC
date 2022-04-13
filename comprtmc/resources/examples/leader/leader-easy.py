import argparse
import sys
#
# Simplified version where only process 0 can crash
# The timed language is too complex to learn even for simple timings
# cmd is not updated correctly: see the error trace in leader2a.tsmv
#
# remove receive0, wakeup_timeout0 and implement the timeout with heartbeats
# put back the crash{i} events


#-- Delporte-Gallet, Devismes, Fauconnier. SSS'07. Robust Stabilizing Leader Election.
computation = [
        [(7,11), (5,6), (3,5)], # unsafe for max_cnt=15
        [(7,10), (4,6), (3,5)],
        [(3,8), (4,7), (3,6)],
        [(7,11), (2,4), (3,3)], # unsafe
        [(4,4), (4,4), (4,4)],
        [(1,1), (1,1), (1,1)],
    ]
crash_time = [
        (15,23),
        (7,23),
        (3,19),
        (5,5)
    ]   
max_cnt = 20
max_crash = 3
max_hb = 4
def dump(n, computation, crash_time, max_cnt, max_crash, timedSMV):
    all_ids = "{" + ", ".join([F"{i}" for i in range(n)]) + "}"
    all_hbs = "{" + ", ".join([F"{i}" for i in range(max_hb)]) + "}"
    if timedSMV:
        print("@TIME_DOMAIN continuous")
    print("MODULE proc(id, wakeup, crash, broadcasting, sender_id, recv_id)")
    print("VAR")
    print("\tstate : {ok, sending, crashed};")
    print(F"\tleader : 0..{n};")
    print(F"\thb : 0..{max_hb};")
    print(F"""ASSIGN
    init(leader) := id;
    next(leader) := case
        wakeup & hb = {max_hb}: id;
        state = ok & broadcasting & recv_id < id : recv_id;
        state = crashed : {all_ids};
        TRUE : leader;
    esac;
    init(hb) := 0;
    next(hb) := case
        wakeup & hb < {max_hb} : hb + 1;
        state != sending & sender_id < id & broadcasting  : 0;
        state = crashed : {all_hbs};
        TRUE : hb;
    esac;
    next(state) := case
        state = ok & wakeup : sending;
        state = sending : ok;
        crash : crashed;
        state = crashed & wakeup : ok;
        TRUE : state;
    esac;

MODULE main
FROZENVAR
        """)
    for i in range(n):
        print(F"\tid{i} : 0..{n};")
    print("VAR")
    print("\tcmd : {cmd_broadcast", end="")
    for i in range(n):
        print(F", cmd_wakeup{i}, cmd_crash{i}", end="")
    print("};")
    print(F"""
    cnt : 0..{max_cnt};
    crash_cnt : 0..{max_crash};""")
    #print("p0 : proc(id0, cmd = cmd_wakeup_ok0, cmd = cmd_wakeup_timeout0, cmd = cmd_crash, cmd = cmd_received0, msg);")    
    #for i in range(1,n):
    #    print(F"p{i} : proc(id{i}, cmd = cmd_wakeup_ok{i}, cmd = cmd_wakeup_timeout{i}, FALSE, cmd = cmd_received{i}, msg);")
    for i in range(0,n):
        print(F"p{i} : proc(id{i}, cmd = cmd_wakeup{i}, cmd = cmd_crash{i}, cmd = cmd_broadcast, sender_id, msg);")
    if timedSMV:
        print(F"""
     y : array 0..{n-1} of clock;
            """)
        print("INIT\n TRUE ")
        for i in range(n):
            print(F" & y[{i}] = 0")
    print("ASSIGN")
    if timedSMV:
        for i in range(n):
            print(F"""
        next(y[{i}]) := case
            cmd = cmd_wakeup{i} : 0;
            TRUE : y[{i}];
        esac;""")
    crash = " | ".join([F"cmd = cmd_crash{i}" for i in range(n)])
    print(F"""
    init(cnt) := 0;
    init(crash_cnt) := 0;
    next(crash_cnt) := case
        ({crash}) & crash_cnt < {max_crash} : crash_cnt + 1;
        TRUE : crash_cnt;
    esac;
    next(cnt) := case
        (cmd = cmd_wakeup0) & cnt < {max_cnt} : cnt + 1;
        crash_cnt != next(crash_cnt) : 0;
        TRUE : cnt;
    esac;""")
    print("INVAR\n\tTRUE ")
    for i in range(n):
        print(F"& id{i} = {i} ", end="")
    # for i in range(n):
    #     for j in range(i+1,n):
    #         print(F"\t & (id{i} != id{j})",end="")
    # print("\t& (" + " | ".join(map(lambda x:F"id{x} = 0", range(n))) + ")")
    print("")
    print("& (cmd = cmd_broadcast <-> (",end="")
    print(" | ".join([F"p{i}.state = sending" for i in range(n)]) + "))")
    if timedSMV:
        for i in range(n):
            print(F"& ((cmd = cmd_wakeup{i} & p{i}.state = ok) -> ({computation[i][0]} <= y[{i}] & y[{i}] <= {computation[i][1]})) -- wakeup period")
            print(F"& (p{i}.state = ok -> y[{i}] <= {computation[i][1]}) -- invariant")
            print(F"& (p{i}.state = crashed -> y[{i}] <= {crash_time[1]}) -- invariant")
            print(F"& ((cmd = cmd_wakeup{i} & p{i}.state = crashed) -> ({crash_time[0]} <= y[{i}] & y[{i}] <= {crash_time[1]})) -- crash time")
    print(F"& (({crash}) -> crash_cnt < {max_crash})")
    print("""
DEFINE 
    msg := case""")
    for i in range(n):
        print(F"\tp{i}.state = sending : p{i}.leader;")
    print(F"""\tTRUE : {n};\nesac;""")
    print("sender_id := case")
    for i in range(n):
        print(F"\tp{i}.state = sending : {i};")
    print(F"\tTRUE : {n};")
    print("esac;")
    print("\tstable := " + " & ".join([F"p{i}.leader = 0" for i in range(n)]) + ";")
    print("\terr := cnt=15 & !stable;")
    for i in range(n):
        print(F"\t_rt_wakeup{i} := cmd = cmd_wakeup{i};")
        print(F"    _rt_crash{i} := cmd = cmd_crash{i};")
    print("INVARSPEC !err")
    ta_fout = sys.stderr
    if not timedSMV:
        print(F"system:leader{n}_ta",file=ta_fout)
        for i in range(n):
            print(F"event:wakeup{i}",file=ta_fout)
            print(F"event:crash{i}",file=ta_fout)
        print("",file=ta_fout)
        print(F"clock:{n}:y",file=ta_fout)
        print("",file=ta_fout)
        # i = 0
        # print(F"process:P{i}", file=ta_fout)
        # print(F"location:P{i}:ok" + "{initial::invariant:" + F"y[{i}] <= {computation[i][1]}" + "}",file=ta_fout)
        # print("location:P" + str(i) + ":crashed{invariant:" + F"y[{i}] <= {crash_time[1]}" + "}",file=ta_fout)
        # print(F"edge:P{i}:ok:ok:wakeup_ok{i}" + "{provided: " + F"y[{i}]>={computation[i][0]} && y[{i}] <= {computation[i][1]} && x[{i}] < {timeout_delay}: do : y[{i}] = 0" + "}",file=ta_fout)
        # print(F"edge:P{i}:ok:ok:wakeup_timeout{i}" + "{provided: " + F"y[{i}]>={computation[i][0]} && y[{i}] <= {computation[i][1]} && x[{i}] >= {timeout_delay} : do : y[{i}] = 0" + "}", file=ta_fout)
        # print(F"edge:P{i}:ok:ok:received{i}" + "{do : " + F"x[{i}] = 0" + "}",file=ta_fout)
        # print(F"edge:P{i}:ok:crashed:crash" + "{" + F"do : y[{i}] = 0" + "}",file=ta_fout)
        # print(F"edge:P{i}:crashed:ok:wakeup_ok{i}" + "{provided: " + F"y[{i}] >= {crash_time[0]} && y[{i}] <= {crash_time[1]} : do : y[{i}] = 0" + "}",file=ta_fout)
        for i in range(0,n):
            print(F"process:P{i}", file=ta_fout)
            print(F"location:P{i}:ok" + "{initial::invariant:" + F"y[{i}] <= {computation[i][1]}" + "}",file=ta_fout)
            print(F"location:P{i}:crashed" + "{invariant:" + F"y[{i}] <= {crash_time[1]}" + "}",file=ta_fout)
            print(F"edge:P{i}:ok:ok:wakeup{i}" + "{provided: " + F"y[{i}]>={computation[i][0]} && y[{i}] <= {computation[i][1]} : do : y[{i}] = 0" + "}",file=ta_fout)
            print(F"edge:P{i}:ok:crashed:crash{i}" + "{" + F"do : y[{i}] = 0" + "}",file=ta_fout)
            print(F"edge:P{i}:crashed:ok:wakeup{i}" + "{provided: " + F"y[{i}] >= {crash_time[0]} && y[{i}] <= {crash_time[1]} : do : y[{i}] = 0" + "}",file=ta_fout)

def main():
    parser = argparse.ArgumentParser(description="Real-Time bit-counter broadcast protocol generator")
    parser.add_argument("-n", type=int,
                        help="number of bits / processes", required=True)
    parser.add_argument("-t", "--timedSMV", action='store_true',
                        help=("Generate SMV timed automaton."))
    parser.add_argument("-i", "--index", type=int, required=True, help="index of the delays")
    args = parser.parse_args()
    n = args.n
    timedSMV = args.timedSMV
    i = args.index
    assert(i >= 0 and i <= len(computation)-1)
    dump(n, computation[i], crash_time[i], max_cnt, max_crash, timedSMV)
if __name__ == "__main__":
    main()