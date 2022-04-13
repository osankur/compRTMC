import argparse
import sys
#
# A protocol with n processes, each being active with a period varying in [period[i][0], period[i][1]].
# The activation duration is in [active_duration[i][0], active_duration[i][1]].
# 
# If at least m processes are active, then they all perform a one-step computation.
# However, only the active processes can provide their inputs. If a process needs to read an input from
# an inactive one, the input will be false.
#
# This script generates a timed SMV model in which this computation simulates an n-bit counter.
# The specification is whether the last bit can become 1, which is the case (model is unsafe).

dataset_active = [
        [(0,0), (0,0), (0,0), (0,0)],
        [(1,1), (1,1), (1,1), (2,2)],
        [(2,2), (3,3), (1,1), (0,0)]
    ]
dataset_period = [
       [(10,10), (15,15), (10,10), (20,20)],
       [(9,9), (14,14), (19,19), (23,23)],
       [(10,10), (15,15), (20,20), (10,10)]
    ]
dataset_total_time = [50, 100, 100]
def dump(n, m, active_duration, period,timed, total_time):
    """ n: number of processes
        m: number of active processes required to activate broadcast
    """
    if timed:
        print("@TIME_DOMAIN continuous")
    print("""
    MODULE main
    VAR""")
    #    cmd : {""",end="")
    #print(", ".join([F"set{i}" for i in range(1,n+1)]) + "};")
    print(F"""
        active : array 0..{n} of boolean;
        carry : array 0..{n} of boolean; -- carry[0] is an unconstrained input
        bits : array 1..{n} of boolean;
        broadcast : boolean;""")
    if not timed:
        print("\tdeadline_missed : boolean;")
    print("   cmd : {cmd_check, ",end="")
    print(", ".join([F"cmd_wakeup{i}, cmd_sleep{i}" for i in range(0,n+1)]),end="")
    print("};")
    if timed:
        print(F"""
            x : array 0..{n} of clock;
            t : clock;""")
    print("ASSIGN")
    if timed:
        print("\tinit(t) := 0;\n\tnext(t) := t;")
    else:
        print("\tinit(deadline_missed) := FALSE;")
        print("\tnext(deadline_missed) := deadline_missed | _rt_deadline;")
    print("\tinit(broadcast) := FALSE;")
    print("""\tnext(broadcast) := case
                !sufficient_actives & next(sufficient_actives) : TRUE;
                TRUE : FALSE;
        esac;
        """)
    for i in range(1,n+1):
        print(F"\tinit(active[{i}]) := FALSE;")
        print(F"\tinit(bits[{i}]) := FALSE;")
        print(F"\tinit(carry[{i}]) := FALSE;")
        print(F"""\tnext(active[{i}]) := case
                cmd = cmd_wakeup{i} : TRUE;
                cmd = cmd_sleep{i} : FALSE;
                TRUE : active[{i}];
            esac;
            """)
        print(F"""\tnext(bits[{i}]) := case
            bits[{i}] & active[{i}] & broadcast & visible_carry{i-1} : FALSE;
            !bits[{i}] & active[{i}] & broadcast & visible_carry{i-1} : TRUE;
            bits[{i}] & active[{i}] & broadcast & !visible_carry{i-1} : TRUE;
            !bits[{i}] & active[{i}] & broadcast & !visible_carry{i-1} : FALSE;
            TRUE : bits[{i}];
        esac;
        next(carry[{i}]) := case
            bits[{i}] & active[{i}] & broadcast & visible_carry{i-1} : TRUE;
            !bits[{i}] & active[{i}] & broadcast & visible_carry{i-1} : FALSE;
            bits[{i}] & active[{i}] & broadcast & !visible_carry{i-1} : FALSE;
            !bits[{i}] & active[{i}] & broadcast & !visible_carry{i-1} : FALSE;""")
        if (i < n):
            print(F"\tbits[{i+1}] != next(bits[{i+1}]) : FALSE; -- next bit will consume the carry")
        print(F"""\tTRUE : carry[{i}];
        esac;
        """)
    if timed:
        for i in range(0,n+1):
            print(F"\tinit(x[{i}]) := 0;")
            print(F"""next(x[{i}]) := case
                active[{i}] != next(active[{i}]) : 0;
                TRUE : x[{i}];
            esac;
                """)
        print("TRANS\n\tTRUE ", end="")
        for i in range(0,n+1):
            print(F"""
            &(cmd = cmd_sleep{i} ->  x[{i}] >= {active_duration[i][0]} & x[{i}] <= {active_duration[i][1]}) 
            &(cmd = cmd_sleep{i} -> active[{i}])
            &(cmd = cmd_wakeup{i} -> !active[{i}])
            &(cmd = cmd_wakeup{i} -> x[{i}] >= {period[i][0]} & x[{i}] <= {period[i][1]}) 
            """)
        print("INVAR\n\tTRUE")
        for i in range(0,n+1):
            print(F"""\t & (active[{i}] -> x[{i}] <= {active_duration[i][1]}) & (!active[{i}] -> x[{i}] <= {period[i][1]})""")
    print("DEFINE")
    nb_of_actives = " + ".join([F"case active[{i}] : 1; TRUE : 0; esac" for i in range(0,n+1)])
    print(F"\tnb_actives := {nb_of_actives};")
    print(F"\tsufficient_actives := nb_actives >= {m};")
    # all_active = " & ".join([F"active[{i}]" for i in range(0,n+1)])
    for i in range(0,n+1):
        print(F"""visible_carry{i} := case
            active[{i}] : carry[{i}];
            TRUE : FALSE;
        esac;""")
    if timed:
        # print(F"""INVARSPEC (visible_carry{n} -> t >= {total_time})""")
        print(F"""INVARSPEC (visible_carry{n} -> t >= {total_time})""")
    else:
        print(F"\terr := visible_carry{n} & !deadline_missed;")
        for i in range(0,n+1):
            print(F"\t_rt_wakeup{i} := cmd = cmd_wakeup{i};")
            print(F"\t_rt_sleep{i} := cmd = cmd_sleep{i};")
        print("\t_rt_deadline := cmd = cmd_check;")
        print("INVARSPEC !err")

    # Print the timed automaton as well if not timed
    ta_fout = sys.stderr
    if not timed:
        print(F"system:broadcast_bit_counter_{n}_{m}",file=ta_fout)
        for i in range(0,n+1):
            print(F"event:wakeup{i}",file=ta_fout)
            print(F"event:sleep{i}",file=ta_fout)
        print("event:deadline",file=ta_fout)
        print(F"clock:{n+1}:x",file=ta_fout)
        print("clock:1:t\n",file=ta_fout)
        for i in range(0,n+1):
            print(F"process:P{i}",file=ta_fout)
            print(F"location:P{i}:" + "sleeping{initial::invariant:" + F"x[{i}] <= {period[i][1]}" + "}",file=ta_fout)
            print(F"location:P{i}:" + "awake{invariant:" + F"x[{i}] <= {active_duration[i][1]}" + "}",file=ta_fout)
            print(F"edge:P{i}:" + F"sleeping:awake:wakeup{i}" + "{provided:" + F"x[{i}] <= {period[i][1]} && x[{i}] >= {period[i][0]}" + F" : do : x[{i}] = 0" + "}",file=ta_fout)
            print(F"edge:P{i}:" + F"awake:sleeping:sleep{i}" + "{provided:" + F"x[{i}] <= {active_duration[i][1]} && x[{i}] >= {active_duration[i][0]}" + F" : do : x[{i}] = 0" + "}",file=ta_fout)
            print("",file=ta_fout)
        print("process:Monitor\nlocation:Monitor:init{initial::invariant:" + F"t <= {total_time}"+ "}\nlocation:Monitor:err{urgent:}", file=ta_fout)
        print("edge:Monitor:init:err:deadline{provided:" + F"t >= {total_time}" + "}\n", file=ta_fout)
            
def main():
    parser = argparse.ArgumentParser(description="Real-Time bit-counter broadcast protocol generator")
    parser.add_argument("-n", type=int,
                        help="number of bits / processes", required=True)
    parser.add_argument("-m", type=int, required=True,
                        help="number of active processes required to trigger broadcast")
    parser.add_argument("-b", "--bound", type=int,
                        help=("Time bound before node 0 should become true. Note that a satisfiable instance can become unsatisfiable due to the bound."))
    parser.add_argument("-t", "--timedSMV", action='store_true',
                        help=("Generate SMV timed automaton."))
    parser.add_argument("-i", "--index", type=int, required=True, help="index of the delays")
    args = parser.parse_args()
    n = args.n
    m = args.m
    timedSMV = args.timedSMV
    i = args.index
    assert(i >= 0 and i <= len(dataset_active)-1 and i <= len(dataset_period))
    active = dataset_active[i]
    period = dataset_period[i]
    total_time = dataset_total_time[i]
    dump(n,m,active, period, timedSMV, total_time)
if __name__ == "__main__":
    main()
