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

# Mutex (TODO)
From TChecker benchmarks database

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

## Abstract
Abstract FTSP protocol in an arbitrary network in which a particular line is modeled concretely.
The models is inspired from the incremental model checking method of Sankur, Talpin TACAS 2017.
In this abstraction, the first n-1 processes are initialized in states where they 
are already stabilized, and the n-th process is initialized in an arbitrary state.

- `concrete/ftsp-abs-X.xml` Uppaal model
- `concrete/ftsp-abs-X.tsmv` nuXmv timed automaton model
- `concrete/ftsp-abs-X.smv` FSM model for compositional alg.
- `concerete/ftsp-abs-X.ta` TA model for compositional alg.

# Pick one: Fischer or CSMA/CD (TODO)