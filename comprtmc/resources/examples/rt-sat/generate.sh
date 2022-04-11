#!/bin/bash

function usage() {
    echo "Usage: $0 format class";
    echo "       where format is smv or tsmv";
    echo "       and class is sat or unsat";
}

if [ $# -eq 2 ]; then
    format=$1
    class=$2
    if [ $format != "uppaal" ] && [ $format != "tchecker" ] && [ $format != "smv" ] && [ $format != "tsmv" ]; then
       usage
       exit 1
    fi
    if [ $class != "sat" ] && [ $class != "unsat" ]; then
       usage
       exit 1
    fi
else
    usage
    exit 1
fi

if [ $format == "smv" ]; then
    ext="smv"
    timearg=""
fi
if [ $format == "tsmv" ]; then
    ext="tsmv"
    timearg="-t"
fi

if [ $class == "sat" ]; then
   python3 rt-sat-smv.py sat $timearg 0 > g0_nobound_sat.$ext
   python3 rt-sat-smv.py sat $timearg 1 > g1_nobound_sat.$ext
   python3 rt-sat-smv.py sat $timearg 2 > g2_nobound_sat.$ext
   python3 rt-sat-smv.py sat $timearg 3 > g3_nobound_sat.$ext
   python3 rt-sat-smv.py sat $timearg 0 -b 150 > g0_150_sat.$ext
   python3 rt-sat-smv.py sat $timearg 1 -b 150 > g1_150_sat.$ext
   python3 rt-sat-smv.py sat $timearg 2 -b 300 > g2_300_sat.$ext
   python3 rt-sat-smv.py sat $timearg 3 -b 300 > g3_300_sat.$ext
fi
if [ $class == "unsat" ]; then
    python3 rt-sat-smv.py unsat $timearg 0 > h0_nobound_unsat.$ext
    python3 rt-sat-smv.py unsat $timearg 1 > h1_nobound_unsat.$ext
    python3 rt-sat-smv.py unsat $timearg 2 > h2_nobound_unsat.$ext
    python3 rt-sat-smv.py unsat $timearg 3 > h3_nobound_unsat.$ext
    python3 rt-sat-smv.py sat $timearg 0 -b 90 > g0_90_unsat.$ext
    python3 rt-sat-smv.py sat $timearg 1 -b 100 > g1_100_unsat.$ext
    python3 rt-sat-smv.py sat $timearg 2 -b 200 > g2_200_unsat.$ext
#
fi
