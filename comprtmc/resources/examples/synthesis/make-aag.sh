set -e
echo "read_verilog $1; hierarchy; proc; opt; memory; opt; techmap; opt; write_blif $1.blif" | yosys
berkeley-abc -c "read_blif $1.blif; strash; refactor; rewrite; dfraig; write_aiger -s $1.aig"
aigtoaig $1.aig $1.aag
# aigtosmv -c $1.aag $1.smv
