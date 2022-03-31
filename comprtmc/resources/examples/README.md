# Monoprocessor real-time scheduling benchmarks
- genbuf2b3unrealy
- genbuf5f5n
- amba3b5y

These benchmarks consist in executing the given synchronous program with real-time constraints.
The smv models contain state predicates such as _rt_task1, _rt_task2, _rt_release. 
The model describes a succession of _phases_ which consist in executing real-time tasks task1, task2, task1, task1, etc. 
The duration of each task is nondeterministic and specified by the timed automaton model.
The system periodically enters a state _rt_release which marks the end of a given phase.
The particular tasks executed in a phase depends on the state space of the program.
The goal is to check whether all reachable phases have an execution time bounded by a given value.
The timed automaton model tracks the execution time of each phase and sends a _rt_err signal to the smv model in case of a deadline violation.

# Maze planning
- maze_planning1

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