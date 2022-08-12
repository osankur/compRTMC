# Real-time Broadcast Protocols
`rt-broadcast` contains protocols made of n processes that each wakes up within a period interval,
and stays active within a time interval. If at least m of them are awake at the same time, they perform
one step of a computation together and go back to sleep. The specification is whether a particular common
configuration is reachable within a time bound.

- broadcast_2_2_a-*.{smv,ta}: 22s (nuXmv 1s)
- broadcast_2_2_b-*.{smv,ta}: 9s (nuXmv 22m)
- broadcast_2_2_c-*.{smv,ta}: 12s (nuXmv 14m)
- broadcast_2_2_d-*.{smv,ta}: 10s (nuXmv 1m)
- broadcast_3_3_d-*.{smv,ta}: 1m (nuXmv 528s)
- TODO find a few other timings
- TODO show that nuXmv is faster when the error state is reachable.

Currently, if the period+activations have a large gcd, then learning the DFA is hard, and nuXmv has also difficulties.
nuXmv is either faster, or as fast as learning. Both time out on the same instances.
 
TODO: !Try other fsm protocols for which nuXmv would be slower!
    - Consider a self-stabilizing algorithm. Encode fairness with clock constraints (if enabled then taken within 20 time units)
    - Introduce random errors or Byzantine behavior but with time constraints: bounded number of errors within a time window
      and require self-stabilization within time bound under these constraints
    
    - Round based protocol: Each round has a duration within `[a,b]`, while fair events `e` must occur within `[0,d_e]`
      Due to clock synchronization: between each round increment
      Aspnes
    
# Robust Leader Election
The `leader/`  directory contains models for a leader election protocol inspired by the following paper:
_Delporte-Gallet, Devismes, Fauconnier. Robust Stabilizing Leader Election. SSS'07._

Here, processes are asynchronous and communicate by broadcast. Each process wakes up within a given period,
and broadcasts its current id, and keeps the minimum seen id as the leader. However, if no message was seen
from the current leader for a while (a certain number of wake-ups), then the node declares itself leader.

The model allows one node to crash, that is, to disappear and stop all interactions for a given period of time.
At most a given number of crashes (`max_crash = 3`) are allowed. The specification is that after `max_cnt` steps
after the last crash, all nodes should agree that the least node is the leader.

The circuit stay6y was integrated in the model stay. Only process 0 can crash, and it does so only if the circuit is at the error state.

- leader_n3.smv with leader_n3_i0-ta.ta
compRTMC: 3 minutes
Uppaal: MEMOUT
nuXMV: MEMOUT

- leader_stay_n3.smv X leader_n3_i1-ta
Learning [ms]: 5480, (5.48 s)
Searching for counterexample [ms]: 195437, (195.437 s)
learning rounds [#]: 26
States: 29
Sigma: 4
Total system calls: 0ms
Nb of membership queries: 661
Time per query: 0ms
[success] Total time: 201 s 

nuXmv-time.sh leader_n3_i1.tsmv -> memout (4GB)
Uppaal -> memout

- leader_stay_n3.smv X leader_n3_i2-ta
Learning [ms]: 9034, (9.034 s)
Searching for counterexample [ms]: 154595, (154.595 s)
learning rounds [#]: 33
States: 53
Sigma: 4
Total system calls: 0ms
Nb of membership queries: 997
Time per query: 0ms
[success] Total time: 164 s

- leader_stay_n3.smv X leader_n3_i3-ta.ta

Learning [ms]: 5423, (5.423 s)
Searching for counterexample [ms]: 205593, (205.593 s)
learning rounds [#]: 26
States: 29
Sigma: 4
Total system calls: 0ms
Nb of membership queries: 661
Time per query: 0ms
[success] Total time: 212 s


# FTSP
## Concrete
The FTSP protocol with a line topology with given number of processes
in which each process wakes up within a period that varies in [9,11].
The specification is that after a given number of steps, all processes agree that process 1 is the leader.

- `concrete/ftsp-line-X.xml` Uppaal model
- `concrete/ftsp-line-X.tsmv` nuXmv timed automaton model
- `concrete/ftsp-line-X.smv` FSM model for compositional alg.
- `concerete/ftsp-line-X.ta` TA model for compositional alg.
  
The script `concrete/ftsp-concrete.py` can be used to generate the above smv and tsmv files.

nuXmv is too slow on these instances.
Uppaal / TChecker are very fast.
Learning takes ~1-2m on ftsp-line-3.smv versus 1-2s for Uppaal.

## Abstract
Abstract FTSP protocol in an arbitrary network in which a particular line is modeled concretely.
The models is inspired from the incremental model checking method of Sankur, Talpin TACAS 2017.
In this abstraction, the first n-1 processes are initialized in states where they 
are already stabilized, and the n-th process is initialized in an arbitrary state.

- `concrete/ftsp-abs-X.xml` Uppaal model
- `concrete/ftsp-abs-X.tsmv` nuXmv timed automaton model
- `concrete/ftsp-abs-X.smv` FSM model for compositional alg.
- `concerete/ftsp-abs-X.ta` TA model for compositional alg.

On these instances, SAT-based algorithms do not perform well. The learning algorithm is run with NuSMV BDD:
`java -jar target/scala-3.1.1/compRTMC-assembly-0.1.0-SNAPSHOT.jar --fsm resources/examples/ftsp/abstract/ftsp-abs-3.smv --ta resources/examples/ftsp/abstract/ftsp-abs-3.ta --fsmModelChecker NuSMV`
This succeeds in ~1m20s.

`nuXmv-time.sh ftsp-abs-3-timed.tsmv` takes > 9m

# Monoprocessor real-time scheduling benchmarks
The `mono_scheduling` directory:
- `genbuf2b3unrealy`
- `genbuf5f5n`
- `amba3b5y`

These benchmarks consist in executing the given synchronous program with real-time constraints.
The smv models contain state predicates such as _rt_task1, _rt_task2, _rt_release. 
The model describes a succession of _phases_ which consist in executing real-time tasks task1, task2, task1, task1, etc. 
The duration of each task is nondeterministic and specified by the timed automaton model.
The system periodically enters a state _rt_release which marks the end of a given phase.
The particular tasks executed in a phase depends on the state space of the program.
The goal is to check whether all reachable phases have an execution time bounded by a given value.
The timed automaton model tracks the execution time of each phase and sends a _rt_err signal to the smv model in case of a deadline violation.

# Priority-Based Scheduling
The `prio_scheduling` directory:
- prio_sched_2{a,b}.smv and prio_sched_2.ta
- prio_sched_3a.smv and prio_sched_3.ta

Priority-based scheduler without preemption between two or three processes.
Each process sends signal _rt_readyi whenever it is ready to execute its rt task.
Whenever the scheduler is available, it schedules process 1 if it is ready,
otherwise, process 2 if it is ready, otherwise process 3, and if non of them are ready,
then schedules a low priority task called idle.
Each task ends with a _rt_release signal, while _rt_miss means a deadline miss, and leads to the error state.

At each bigstep, the smv model is queried to know whether processes are _rt_ready.
Whether a process is ready depends on its internal state described by the given circuit.
Moreover, the timed automaton model constrains the interarrival times (time elapsed between two _rt_readyi),
the task execution times, and the deadline for each process.

A single circuit model determines the joint behavior of all task arrivals.
- a: bs16y.aag
- b: moving_obstacle_8x8_1glitches.aag

Currently the difficult part is the TA learning which takes a lot of time while FSM model checking is very fast.
The timed model checker of nuXmv is currently much faster.

# STS
See `sts/README.md`

For n=3, the alphabet is probably too big for learning.
TODO write smv fsm model.

# Mutex (TODO)
From TChecker benchmarks database

# Maze planning
The `planning` directory
- maze_planning1
- maze_planning2 (TODO)

This is a high-level planning problem with real-time constraints.
An agent must go from a start cell to a goal cell in a grid maze. Some cells are obstacles,
and some edges between two consecutive cells contain a gate. Each gate opens and closes
at different time points specified by the timed automaton model.
Moreover, there is a moving object which stops and goes at predefined points:
the waiting times at these points are also specified by the timed automaton.
The model is turn-based: at each synchronous step, only one of these entities (agent, gate, moving obstacle)
makes a move.
The goal is to find a plan for the agent avoiding all obstacles and reaching the destination within bounded time.
The model is described in maze_planning1.png: the red segments are doors, and the blue point is the obstacle that moves
vertically back and forth, pausing at y=0 (top) and y=2 (middle).

The second model should contain a Boolean program to specify the behavior of a moving obstacle.
(Such as a Boolean counter whose bits determine the direction to go or how long to wait)

# RT-SAT
The `rt-sat/` directory contains rt-sat benchmarks from the TChecker benchmarks database.
nuXmv is very quick on these.

# Real-Time Requirements
From the FORMATS paper or ATVA submission...
