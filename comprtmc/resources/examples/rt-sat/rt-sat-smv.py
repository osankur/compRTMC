import sys
import argparse
from io import StringIO
from data import *

class TAWRITER:
    def __init__(self, graph, timed=False, bound=None):
        self.graph = graph
        self.bound = bound
        self.timed = timed

    def threshold_guard(self, threshold, links):
        welldefined = " & ".join(map(lambda x: "out{0} < 2".format(x), links))
        positive = " + ".join(map(lambda x: "out{0}".format(x), links)) + " >= " + str(threshold)
        negative = " + ".join(map(lambda x: "out{0}".format(x), links)) + " < " + str(threshold)
        return (welldefined,positive,negative)

    def dump(self):
        graph = self.graph
        fout = StringIO()

        gate_identifiers = set([])
        input_identifiers = set([])

        # All undefined indices are inputs
        for node in graph:
            gate_identifiers.add(node[0])
        for node in graph:
            links = node[5]
            for x in links:
                if not (x in gate_identifiers):
                    input_identifiers.add(x)

        if not (0 in gate_identifiers):
            raise Exception("A node with identifier 0 must be given")

        print("MODULE main\nVAR\n", file=fout)
        if self.bound != None and self.timed:
            print("\tt : clock;", file=fout)
        cmds = ", ".join(map(lambda id : F"up{id}, down{id}", gate_identifiers))
        print("\tcmd : {check, " + cmds + "};",file=fout)
        for id in gate_identifiers:
            print(F"\tout{id} : 0..2;", file=fout)
            print(F"\tstate{id} : " + "{down, up};", file=fout)
        print("\terr : boolean;",file=fout)
        print("FROZENVAR",file=fout)
        for id in input_identifiers:
            print("\tout{0} : 0..2;".format(id), file=fout)

        print("ASSIGN", file=fout)
        print("\tinit(err) := FALSE;", file=fout)
        print("\tnext(err) := err | cmd = check & out0 = 1;", file=fout)
        for id in gate_identifiers:
            print(F"\tinit(out{id}) := 2;", file=fout)
        for node in graph:
            #(id, neg, th, period, duration, links) = node
            (id, period, duration, neg, th, links) = node
            print(F"\tinit(state{id}) := down;", file=fout)            
            # print("\nprocess:Node{0}".format(id), file=fout)
            # print("clock:1:x{0}".format(id), file=fout)
            # print("location:Node{0}:Down{{initial::invariant:x{0}<={1}}}".format(id,period), file=fout)
            # print("location:Node{0}:Up{{invariant:x{0}<={1}}}".format(id,duration), file=fout)
            (welldefined, positive, negative) = self.threshold_guard(th, links)
            if neg:
                tmp = negative
                negative = positive
                positive = tmp
            print(F"\tnext(state{id}) := case", file=fout)
            #for i in links:
                # print("edge:Node{0}:Down:Down:idle{{provided:x{0}=={1} && out{2} == 2: do : x{0} = 0}}".format(id, period, i), file=fout)
                # print("edge:Node{0}:Down:Down:idle{{provided:x{0}=={1} && out{2} == 2: do : x{0} = 0}}".format(id, period, i), file=fout)
            print(F"\t\tstate{id}=down & cmd = up{id} & {welldefined} : up; ",file=fout)
            print(F"\t\tstate{id}=up & cmd = down{id} : down; ",file=fout)
            print(F"\t\tTRUE : state{id};", file=fout)
            print("\tesac;",file=fout)

            print(F"\tnext(out{id}) := case", file=fout)
            #for i in links:
                # print("edge:Node{0}:Down:Down:idle{{provided:x{0}=={1} && out{2} == 2: do : x{0} = 0}}".format(id, period, i), file=fout)
                # print("edge:Node{0}:Down:Down:idle{{provided:x{0}=={1} && out{2} == 2: do : x{0} = 0}}".format(id, period, i), file=fout)
            print(F"\t\tstate{id}=down & cmd = up{id} & {welldefined} & {negative} : 0; ",file=fout)
            print(F"\t\tstate{id}=down & cmd = up{id} & {welldefined} & {positive} : 1; ",file=fout)
            print(F"\t\tstate{id}=up & cmd = down{id} : 2; ",file=fout)
            print(F"\t\tTRUE : out{id};",file=fout)
            print("\tesac;",file=fout)
        print("DEFINE",file=fout)
        for node in graph:
            (id, period, duration, neg, th, links) = node
            print(F"\t_rt_up{id} := cmd = up{id};",file=fout)
            print(F"\t_rt_down{id} := cmd = down{id};",file=fout)
        print("\t_rt_check := cmd = check;",file=fout)
        print("INVARSPEC !err",file=fout)
        print(fout.getvalue())


    def dump_timed(self):
        graph = self.graph
        fout = StringIO()

        gate_identifiers = set([])
        input_identifiers = set([])

        # All undefined indices are inputs
        for node in graph:
            gate_identifiers.add(node[0])
        for node in graph:
            links = node[5]
            for x in links:
                if not (x in gate_identifiers):
                    input_identifiers.add(x)

        if not (0 in gate_identifiers):
            raise Exception("A node with identifier 0 must be given")
        print("@TIME_DOMAIN continuous")
        print("MODULE main\nVAR\n", file=fout)
        if self.bound != None:
            print("\tt : clock;", file=fout)
        for id in gate_identifiers:
            print(F"\t x{id} : clock;",file=fout)
        cmds = ", ".join(map(lambda id : F"up{id}, down{id}, reset{id}", gate_identifiers))
        print("\tcmd : {check, " + cmds + "};",file=fout)
        for id in gate_identifiers:
            print(F"\tout{id} : 0..2;", file=fout)
            print(F"\tstate{id} : " + "{down, up};", file=fout)
        print("\terr : boolean;",file=fout)
        print("FROZENVAR",file=fout)
        for id in input_identifiers:
            print("\tout{0} : 0..2;".format(id), file=fout)

        print("ASSIGN", file=fout)
        print("\tinit(err) := FALSE;", file=fout)
        if self.bound != None:
            print("\tinit(t) := 0;",file=fout)
            print("\tnext(t) := t;",file=fout)
        if self.bound == None:
            print("\tnext(err) := err | cmd = check & out0 = 1;", file=fout)
        else:
            print(F"\tnext(err) := err | cmd = check & out0 = 1 & t <= {self.bound};", file=fout)
        for id in gate_identifiers:
            print(F"\tinit(out{id}) := 2;", file=fout)
        for node in graph:
            #(id, neg, th, period, duration, links) = node
            (id, period, duration, neg, th, links) = node
            print(F"\tinit(state{id}) := down;", file=fout)
            print(F"\tinit(x{id}) := 0;", file=fout)
            # print("\nprocess:Node{0}".format(id), file=fout)
            # print("clock:1:x{0}".format(id), file=fout)
            # print("location:Node{0}:Down{{initial::invariant:x{0}<={1}}}".format(id,period), file=fout)
            # print("location:Node{0}:Up{{invariant:x{0}<={1}}}".format(id,duration), file=fout)
            (welldefined, positive, negative) = self.threshold_guard(th, links)
            if neg:
                tmp = negative
                negative = positive
                positive = tmp
            print(F"\tnext(state{id}) := case", file=fout)
            #for i in links:
                # print("edge:Node{0}:Down:Down:idle{{provided:x{0}=={1} && out{2} == 2: do : x{0} = 0}}".format(id, period, i), file=fout)
                # print("edge:Node{0}:Down:Down:idle{{provided:x{0}=={1} && out{2} == 2: do : x{0} = 0}}".format(id, period, i), file=fout)
            print(F"\t\tstate{id}=down & cmd = up{id} & {welldefined} : up; ",file=fout)
            print(F"\t\tstate{id}=up & cmd = down{id} : down; ",file=fout)
            print(F"\t\tTRUE : state{id};", file=fout)
            print("\tesac;",file=fout)

            print(F"\tnext(out{id}) := case", file=fout)
            #for i in links:
                # print("edge:Node{0}:Down:Down:idle{{provided:x{0}=={1} && out{2} == 2: do : x{0} = 0}}".format(id, period, i), file=fout)
                # print("edge:Node{0}:Down:Down:idle{{provided:x{0}=={1} && out{2} == 2: do : x{0} = 0}}".format(id, period, i), file=fout)
            print(F"\t\tstate{id}=down & cmd = up{id} & {welldefined} & {negative} : 0; ",file=fout)
            print(F"\t\tstate{id}=down & cmd = up{id} & {welldefined} & {positive} : 1; ",file=fout)
            print(F"\t\tstate{id}=up & cmd = down{id} : 2; ",file=fout)
            print(F"\t\tTRUE : out{id};",file=fout)
            print("\tesac;",file=fout)

            print(F"\tnext(x{id}) := case", file=fout)
            print(F"\t\tcmd = up{id} | cmd = down{id} | cmd = reset{id} : 0;",file=fout)
            print(F"\t\tTRUE : x{id};",file=fout)
            print("\tesac;",file=fout)
        print("INVAR\n\tTRUE",file=fout)
        for node in graph:
            #(id, neg, th, period, duration, links) = node
            (id, period, duration, neg, th, links) = node
            (welldefined, positive, negative) = self.threshold_guard(th, links)
            if neg:
                tmp = negative
                negative = positive
                positive = tmp
            print(F"\t& (state{id} = down -> x{id} <= {period})",file=fout)
            print(F"\t& (state{id} = up -> x{id} <= {duration})",file=fout)
            print(F"\t& (cmd = up{id} -> x{id} = {period})",file=fout)
            print(F"\t& (cmd = down{id} -> x{id} <= {duration})",file=fout)
            print(F"\t& (cmd = up{id} -> state{id} = down & {welldefined})",file=fout)
            print(F"\t& (cmd = down{id} -> state{id} = up)",file=fout)
            print(F"\t& (cmd = reset{id} -> state{id} = down & !({welldefined}))",file=fout)
            
        print("INVARSPEC !err",file=fout)
        print(fout.getvalue())

def main():
    parser = argparse.ArgumentParser(description="Real-Time SAT benchmarks generator")
    parser.add_argument("benchmark_class", metavar="class", type=str,
                        help="benchmark class: sat or unsat")
    parser.add_argument("number", metavar="n", type=int,
                        help="benchmark number in the given class")
    parser.add_argument("-b", "--bound", type=int,
                        help=("Time bound before node 0 should become true. Note that a satisfiable instance can become unsatisfiable due to the bound."))
    parser.add_argument("-t", "--time", action='store_true',
                        help=("Generate SMV timed automaton."))

    args = parser.parse_args()
    benchmarks = None
    if args.benchmark_class == "sat":
        benchmarks = sat_benchmarks
    elif args.benchmark_class == "unsat":
        benchmarks = unsat_benchmarks
    else:
        raise Exception("Unrecognized benchmark class")
    if args.number >= len(sat_benchmarks):
        raise Exception("There is no benchmark number " + str(args.number) + " in the class " + args.benchmark_class)
    ta = TAWRITER(benchmarks[args.number], args.time, args.bound)
    if (args.time):
        ta.dump_timed()
    else:
        ta.dump()

if __name__ == "__main__":
    main()
