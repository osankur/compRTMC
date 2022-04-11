# #
# # n-bit counter
# # carry[0] is an unconstrained input. 
# # Each bit[i] is incremented and carry[i] is set if
# #   cmd = seti. However, the carry[i-1] which is read by
# # bit[i] is only visible if active[i].
# #
# # Each carry[i] becomes active[i] with a period of [period[i][0], period[i][1]],
# # after which it remains active for [active_duration[i][0], active_duration[i][1]]
# #
# # number of bits
# n = 3
# active_duration = [(1,2), (15,16), (0,1), (1,2)]
# period =          [(5,6), (20,22), (15,16), (18,19  )]
# total_time = 100
# print("""@TIME_DOMAIN continuous
# MODULE main
# VAR
#     cmd : {""",end="")
# print(", ".join([F"set{i}" for i in range(1,n+1)]) + "};")
# print(F"""
#     active : array 0..{n} of boolean;
#     carry : array 0..{n} of boolean; -- carry[0] is an unconstrained input
#     bits : array 0..{n} of boolean;
#     x : array 0..{n} of clock;
#     t : clock;
# ASSIGN
# """)
# print("\tinit(t) := 0;\n\tnext(t) := t;")
# print("\tinit(x[0]) := 0;")
# for i in range(1,n+1):
#     print(F"\tinit(x[{i}]) := 0;")
#     print(F"\tinit(active[{i}]) := FALSE;")
#     print(F"\tinit(bits[{i}]) := FALSE;")
#     print(F"\tinit(carry[{i}]) := FALSE;")
#     print(F"""\tnext(bits[{i}]) := case
#         cmd = set{i} & bits[{i}] & visible_carry{i-1} : FALSE;
#         cmd = set{i} & !bits[{i}] & visible_carry{i-1} : TRUE;
#         cmd = set{i} & bits[{i}] & !visible_carry{i-1} : TRUE;
#         cmd = set{i} & !bits[{i}] & !visible_carry{i-1} : FALSE;
#         TRUE : bits[{i}];
#     esac;
#     next(carry[{i}]) := case
#         cmd = set{i} & bits[{i}] & visible_carry{i-1} : TRUE;
#         cmd = set{i} & !bits[{i}] & visible_carry{i-1} : FALSE;
#         cmd = set{i} & bits[{i}] & !visible_carry{i-1} : FALSE;
#         cmd = set{i} & !bits[{i}] & !visible_carry{i-1} : FALSE;""")
#     if (i < n):
#         print(F"\tcmd = set{i+1} & bits[{i+1}] != !next(bits[{i+1}]) : FALSE; -- next bit will consume the carry")
#     print(F"""\tTRUE : bits[{i}];
#     esac;
#     next(active[{i}]) := case
#         active[{i}] & x[{i}] >= {active_duration[i][0]} & x[{i}] <= {active_duration[i][1]} : FALSE;
#         !active[{i}] & x[{i}] >= {period[i][0]} & x[{i}] <= {period[i][1]} : TRUE;
#         TRUE : active[{i}];
#     esac;
#     next(x[{i}]) := case
#         active[{i}] != next(active[{i}]) : 0;
#         TRUE : x[{i}];
#     esac;
# """)
# print("INVAR\n\tTRUE")
# for i in range(1,n+1):
#     print(F"""\t & (active[{i}] -> x[{i}] <= {active_duration[i][1]}) & (!active[{i}] -> x[{i}] <= {period[i][1]})""")
# print("DEFINE")
# all_active = " & ".join([F"active[{i}]" for i in range(1,n+1)])
# for i in range(0,n+1):
#     print(F"""visible_carry{i} := case
#         {all_active} : carry[{i}];
#         TRUE : FALSE;
#     esac;""")
# print(F"""INVARSPEC (visible_carry{n} -> t >= {total_time})""")

# Rewrote the above model with nondeterministic active to inactive changes
#
# n-bit counter
# carry[0] is an unconstrained input. 
# Each bit[i] is incremented and carry[i] is set if
#   cmd = seti. However, the carry[i-1] which is read by
# bit[i] is only visible if active[i].
#
# Each carry[i] becomes active[i] with a period of [period[i][0], period[i][1]],
# after which it remains active for [active_duration[i][0], active_duration[i][1]]
#
# number of bits
n = 3
active_duration = [(1,2), (15,16), (0,1), (1,2)]
period =          [(5,6), (20,22), (15,16), (18,19  )]
total_time = 100
print("""@TIME_DOMAIN continuous
MODULE main
VAR
    cmd : {""",end="")
print(", ".join([F"set{i}" for i in range(1,n+1)]) + "};")
print(F"""
    active : array 0..{n} of boolean;
    carry : array 0..{n} of boolean; -- carry[0] is an unconstrained input
    bits : array 0..{n} of boolean;
    x : array 0..{n} of clock;
    t : clock;
ASSIGN
""")
print("\tinit(t) := 0;\n\tnext(t) := t;")
for i in range(1,n+1):
    print(F"\tinit(active[{i}]) := FALSE;")
    print(F"\tinit(bits[{i}]) := FALSE;")
    print(F"\tinit(carry[{i}]) := FALSE;")
    print(F"""\tnext(bits[{i}]) := case
        cmd = set{i} & bits[{i}] & visible_carry{i-1} : FALSE;
        cmd = set{i} & !bits[{i}] & visible_carry{i-1} : TRUE;
        cmd = set{i} & bits[{i}] & !visible_carry{i-1} : TRUE;
        cmd = set{i} & !bits[{i}] & !visible_carry{i-1} : FALSE;
        TRUE : bits[{i}];
    esac;
    next(carry[{i}]) := case
        cmd = set{i} & bits[{i}] & visible_carry{i-1} : TRUE;
        cmd = set{i} & !bits[{i}] & visible_carry{i-1} : FALSE;
        cmd = set{i} & bits[{i}] & !visible_carry{i-1} : FALSE;
        cmd = set{i} & !bits[{i}] & !visible_carry{i-1} : FALSE;""")
    if (i < n):
        print(F"\tcmd = set{i+1} & bits[{i+1}] != !next(bits[{i+1}]) : FALSE; -- next bit will consume the carry")
    print(F"""\tTRUE : bits[{i}];
    esac;
    """)
for i in range(0,n+1):
    print(F"\tinit(x[{i}]) := 0;")
    print(F"""next(x[{i}]) := case
        active[{i}] != next(active[{i}]) : 0;
        TRUE : x[{i}];
    esac;
        """)
print("TRANS\n\tTRUE")
for i in range(0,n+1):
    print(F"""\t&(active[{i}] & !next(active[{i}]) -> x[{i}] >= {active_duration[i][0]} & x[{i}] <= {active_duration[i][1]}) 
    &(!active[{i}] & next(active[{i}]) -> x[{i}] >= {period[i][0]} & x[{i}] <= {period[i][1]}) 
    """)
print("INVAR\n\tTRUE")
for i in range(1,n+1):
    print(F"""\t & (active[{i}] -> x[{i}] <= {active_duration[i][1]}) & (!active[{i}] -> x[{i}] <= {period[i][1]})""")
print("DEFINE")
all_active = " & ".join([F"active[{i}]" for i in range(0,n+1)])
for i in range(0,n+1):
    print(F"""visible_carry{i} := case
        {all_active} : carry[{i}];
        TRUE : FALSE;
    esac;""")
print(F"""INVARSPEC (visible_carry{n} -> t >= {total_time})""")
