import argparse
import sys

#-- Delporte-Gallet, Devismes, Fauconnier. SSS'07. Robust Stabilizing Leader Election.
computation = [
        [(7,11), (2,4), (3,3)],
        [(4,4), (4,4), (4,4)],
    ]
crash_time = [
        (15,23),
        (8,8)
    ]   
timeout_delay = 7
max_cnt = 15
max_crash = 3

def dump(n, computation, crash_time, timeout_delay, max_cnt, max_crash, timedSMV):
    if timedSMV:
        print("@TIME_DOMAIN continuous")
    print("MODULE proc(id, wakeup_ok, wakeup_timeout, crash, recv, recv_id)")
    print("VAR")
    print("\tstate : {ok, woke_up, sending, crashed};")
    print(F"\tleader : 0..{n};")
    print("""ASSIGN
    init(leader) := id;
    next(leader) := case
        wakeup_timeout : id;
        recv_id < id : recv_id;
        TRUE : leader;
    esac;
    next(state) := case
        state = ok & wakeup_ok : woke_up;
        state = ok & wakeup_timeout : woke_up;
        state = woke_up & !crash: sending;
        state = woke_up & crash: crashed;
        state = crashed & (wakeup_ok | wakeup_timeout) : ok;
        state = sending : ok;
        TRUE : state;
    esac;

MODULE main
FROZENVAR
        """)
    for i in range(n):
        print(F"\tid{i} : 0..{n};")
    print("VAR")
    print("cmd : {cmd_crash, cmd_broadcast")
    for i in range(n):
        print(F", cmd_wakeup_ok{i}, cmd_wakeup_timeout{i}, cmd_received{i}, cmd_ignored{i}")
    print("};")
    print(F"""
    cnt : 0..{max_cnt};
    crash_cnt : 0..{max_crash};""")
    for i in range(n):
        print(F"p{i} : proc(id{i}, cmd = cmd_wakeup_ok{i}, cmd = cmd_wakeup_timeout{i}, cmd = cmd_crash, cmd = cmd_received{i}, msg);")
    if timedSMV:
        print(F"""
     x : array 0..{n-1} of clock;
     y : array 0..{n-1} of clock;
            """)
        print("INIT\n TRUE ")
        for i in range(n):
            print(F" & x[{i}] = 0 & y[{i}] = 0")
    print("ASSIGN")
    if timedSMV:
        for i in range(n):
            print(F"""
        next(x[{i}]) := case
            cmd = cmd_received{i} : 0;
            TRUE : x[{i}];
        esac;
        next(y[{i}]) := case
            cmd = cmd_wakeup_ok{i} : 0;
            cmd = cmd_wakeup_timeout{i} : 0;
            TRUE : y[{i}];
        esac;""")
    print(F"""
    init(cnt) := 0;
    init(crash_cnt) := 0;
    next(crash_cnt) := case
        (cmd = cmd_crash) & crash_cnt < {max_crash} : crash_cnt + 1;
        TRUE : crash_cnt;
    esac;
    next(cnt) := case
        (cmd = cmd_wakeup_ok0 | cmd = cmd_wakeup_timeout0) & cnt < {max_cnt} : cnt + 1;
        crash_cnt != next(crash_cnt) : 0;
        TRUE : cnt;
    esac;
TRANS
    TRUE """)
    print("\t & ((cmd = cmd_broadcast & (p0.state = crashed | msg > p0.leader)) -> next(cmd) = cmd_ignored0)")
    print("\t&((cmd = cmd_broadcast & (p0.state != crashed & msg <= p0.leader)) -> next(cmd) = cmd_received0)")
    for i in range(0,n-1):
        print(F"\t & (((cmd = cmd_ignored{i} | cmd = cmd_received{i}) & (p{i+1}.state = crashed | msg > p{i+1}.leader)) -> next(cmd) = cmd_ignored{i+1})")
        print(F"\t&(((cmd = cmd_ignored{i} | cmd = cmd_received{i}) & (p{i+1}.state != crashed & msg <= p{i+1}.leader)) -> next(cmd) = cmd_received{i+1})")
    print(F"& (cmd = cmd_ignored{n-1} | cmd = cmd_received{n-1}) -> (cmd != cmd_ignored{n-1} & cmd != cmd_received{n-1})")
    print("INVAR\n\tTRUE ")
    for i in range(n):
        for j in range(i+1,n):
            print(F"\t & (id{i} != id{j})",end="")
    print("\t& (" + " | ".join(map(lambda x:F"id{x} = 0", range(n))) + ")")
    print("& (cmd = cmd_broadcast <-> (",end="")
    print(" | ".join([F"p{i}.state = sending" for i in range(n)]) + "))")
    if timedSMV:
        for i in range(n):
            print(F"& ((cmd = cmd_wakeup_ok{i} & p{i}.state = ok) -> ({computation[i][0]} <= y[{i}] & y[{i}] <= {computation[i][1]}) & x[{i}] < {timeout_delay}) -- wakeup period")
        for i in range(n):
            print(F"& (p{i}.state != crashed -> y[{i}] <= {computation[i][1]}) -- invariant")
        for i in range(n):
            print(F"& ((cmd = cmd_wakeup_timeout{i} & p{i}.state = ok) -> ({computation[i][0]} <= y[{i}] & y[{i}] <= {computation[i][1]} & x[{i}] >= {timeout_delay})) -- tiemout")
        for i in range(n):
            print(F"& ((cmd = cmd_wakeup_ok{i} & p{i}.state = crashed) -> ({crash_time[0]} <= y[{i}] & y[{i}] <= {crash_time[1]}))")
        for i in range(n):
            print(F"& (p{i}.state = crashed -> y[{i}] <= {crash_time[1]}) -- invariant")
    for i in range(n):
        print(F"& (p{i}.state = crashed -> cmd != cmd_wakeup_timeout{i})")
    print(F"& ((cmd = cmd_crash) -> crash_cnt < {max_crash})")
    print("""
DEFINE 
    msg := case""")
    for i in range(n):
        print(F"p{i}.state = sending : p{i}.leader;")
    print(F"""TRUE : {n};\nesac;""")
    print("stable := " + " & ".join([F"p{i}.leader = 0" for i in range(n)]) + ";")
    print("err := cnt=15 & !stable;")
    for i in range(n):
        print(F"_rt_wakeup_ok{i} := cmd = cmd_wakeup_ok{i};")
    for i in range(n):
        print(F"_rt_wakeup_timeout{i} := cmd = cmd_wakeup_timeout{i};")
    print("    _rt_crash := cmd = cmd_crash;")
    for i in range(n):
        print(F"_rt_received{i} := cmd = cmd_received{i};")
    print("INVARSPEC !err")
    ta_fout = sys.stderr
    if not timedSMV:
        print(F"system:leader{n}_ta",file=ta_fout)
        for i in range(n):
            print(F"event:wakeup_ok{i}",file=ta_fout)
            print(F"event:wakeup_timeout{i}",file=ta_fout)
            print(F"event:received{i}",file=ta_fout)
        print("event:crash",file=ta_fout)
        print("",file=ta_fout)
        print(F"clock:{n}:x",file=ta_fout)
        print(F"clock:{n}:y",file=ta_fout)
        print("",file=ta_fout)
        print(F"int:1:0:{n-1}:0:last",file=ta_fout)
        for i in range(n):
            print(F"process:P{i}", file=ta_fout)
            print(F"location:P{i}:ok" + "{initial::invariant:" + F"y[{i}] <= {computation[i][1]}" + "}",file=ta_fout)
            print("location:P" + str(i) + ":crashed{invariant:" + F"y[{i}] <= {crash_time[1]}" + "}",file=ta_fout)
            print(F"edge:P{i}:ok:ok:wakeup_ok{i}" + "{provided: " + F"y[{i}]>={computation[i][0]} && y[{i}] <= {computation[i][1]} && x[{i}] < {timeout_delay}: do : y[{i}] = 0; last = {i}" + "}",file=ta_fout)
            print(F"edge:P{i}:ok:ok:wakeup_timeout{i}" + "{provided: " + F"y[{i}]>={computation[i][0]} && y[{i}] <= {computation[i][1]} && x[{i}] >= {timeout_delay} : do : y[{i}] = 0; last = {i}" + "}", file=ta_fout)
            print(F"edge:P{i}:ok:ok:received{i}" + "{do : " + F"x[{i}] = 0" + "}",file=ta_fout)
            print(F"edge:P{i}:ok:crashed:crash" + "{provided: " + F"last == {i} : do : y[{i}] = 0" + "}",file=ta_fout)
            print(F"edge:P{i}:crashed:ok:wakeup_ok{i}" + "{provided: " + F"y[{i}] >= {crash_time[0]} && y[{i}] <= {crash_time[1]} : do : y[{i}] = 0" + "}",file=ta_fout)

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
    dump(n, computation[i], crash_time[i], timeout_delay, max_cnt, max_crash, timedSMV)
if __name__ == "__main__":
    main()