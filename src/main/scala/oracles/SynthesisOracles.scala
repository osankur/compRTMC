package fr.irisa.comprtmc.synthesis

import java.io.File
import java.io.PrintWriter
import io.AnsiColor._
import scala.sys.process._
import scala.collection.mutable._
import collection.convert.ImplicitConversions._
import scala.math._
import net.automatalib.automata.fsa.DFA;
import net.automatalib.words.impl.Alphabets;
import net.automatalib.words._
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.util.automata.builders.AutomatonBuilders;
import net.automatalib.visualization.Visualization;
import de.learnlib.api.oracle.EquivalenceOracle
import de.learnlib.api.query.DefaultQuery;
import scala.io.Source
import java.nio.file.Files
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import fr.irisa.comprtmc.configuration
import fr.irisa.comprtmc.statistics
import fr.irisa.comprtmc.fsm

sealed class SynthesisResult
sealed case class Controllable(combinedSystem : File) extends SynthesisResult
sealed case class Uncontrollable(combinedSystem : File) extends SynthesisResult

class Verilog(val inputFile : File){
    // alphabet is the outputs minus the error output
    var alphabet : List[String] = List()
    var inputs : List[String] = List()
    var outputs : List[String] = List()

    // The unique output 
    var errorName = "error"
    private var moduleName = ""
    private var content = ""

    {
        val lines = Source.fromFile(inputFile).getLines().toList
        content = lines.mkString("\n")
        val regModuleMain = "\\s*module\\s*(.*)\\((.*)\\);\\s*".r
        val _inputs = ListBuffer[String]()
        val _alphabet = ListBuffer[String]()
        val _outputs = ListBuffer[String]()
        // Aggregate lines until the occurrence of ';'

        val aggLines = ListBuffer[String]()
        var insideModule = false
        var currentLine = ""
        lines foreach {
            case line => 
                if line.contains("module") then
                    insideModule = true
                if insideModule then
                    currentLine += line
                    if line.contains(";") then
                        aggLines.append(currentLine)
                        currentLine = ""
                        insideModule = false
        }
        aggLines foreach {
            case regModuleMain(name, params) =>
                moduleName = name
                val args = params.split(",").toList.map(_.split(" ").map(_.strip()).filter(_.length>0))
                args foreach{
                    case arg if arg(0) == "input" => 
                        _inputs.append(arg(1))
                    case arg if arg(0) == "output" => 
                        _outputs.append(arg(1))
                        if (arg(1).startsWith("_rt_")){
                            _alphabet.append(arg(1).substring(4))
                        } else {
                            if (arg(1) != errorName){
                                throw Exception("There should be one output named 'error', and others must be prefixed with _rt_")
                            }
                        }
                    case arg => 
                        throw Exception("All arguments of a module must be preceded by input or output: " + arg(0))
                }
                if (!_outputs.contains(errorName))
                    throw Exception("Module in file " + inputFile.toString + " does not contain an error output")
            case _ => ()
        }
        if (!_inputs.contains("clk") ){
            throw Exception("The Verilog module must contain clk as input" )
        }
        alphabet = _alphabet.toList
        inputs = _inputs.toList
        outputs = _outputs.toList
    }

    /** Returns a string description of a Verilog program in which a module named _rtmc_ is created
     *  which contains an instance of the inputFile, as well as a module encoding the given hypothesis DFA,
     *  synchronized on the alphabet (that is, all outputs but error).
     *  The _rtmc_ module contains error as its single output, which is true iff the DFA is at an accepting state,
     *  and the inputFile module has set its error output to 1. This encodes the intersection of the input verilog program
     *  with the DFA, and is in the right format for realizability.
     */
    def intersectedWith(hypothesis: DFA[_, String]) : String = {
        val sb = StringBuilder()
        val _alphabet = alphabet.map("_rt_" + _)
        // val _outputs = "error" :: outputs.filter(_ != "error").map("_rt_" + _)
        val inputsAsArgList = inputs.map("input " + _).mkString(", ")
        val alphabetAsArgList = _alphabet.map("output _rt_" + _).mkString(", ")
        val outputsAsArgList = outputs.map("output " + _).mkString(", ")
        
        val inputsAsArgs = inputs.map(x => s".$x($x)").mkString(", ")
        val alphabetAsArgs = _alphabet.map(x => s".$x($x)").mkString(", ")
        
        sb.append(content+"\n\n")
        // sb.append(s"module _rtmc_($inputsAsArgList, output $errorName);\n")
        sb.append(s"module _rtmc_($inputsAsArgList, $outputsAsArgList, output dfa_accept);\n")
        sb.append(s"\twire _dfa_accept;\n\twire _fsm_err;\n\tassign $errorName = _dfa_accept && _fsm_err;\n")
        sb.append("\tassign dfa_accept = _dfa_accept;\n")
        _alphabet foreach{
            alpha =>
                sb.append(s"\twire $alpha;\n")
        }
        sb.append(s"\tdfa _dfa_(.clk(clk), $alphabetAsArgs, ._dfa_accept(_dfa_accept));\n")
        sb.append(s"\t$moduleName _fsm_($inputsAsArgs, $alphabetAsArgs, .$errorName(_fsm_err));\n")
        sb.append("endmodule\n")

        assert(hypothesis.getInitialStates().size == 1)
        val states = hypothesis.getStates()
        val statesSize = (log10(states.size)/log10(2.0)+1).toInt
        var acceptingStates = ListBuffer[Int]()
        states foreach { state =>
            if (hypothesis.isAccepting(state)) then {
                acceptingStates.append(hypothesis.stateIDs.getStateId(state))
            }
        }
        sb.append(s"module dfa(input clk, ${_alphabet.map("input " + _).mkString(", ")}, output _dfa_accept);\n")
        sb.append("\treg notfirst;\n")
        sb.append(s"\treg[$statesSize:0] state;\n")
        sb.append("\tinitial begin\n")
        hypothesis.getInitialStates().toList match {
            case List() => throw Exception("No initial state found")
            case List(i) => sb.append(s"\t\tstate = ${hypothesis.stateIDs.getStateId(i)};\n")
            case _ => throw Exception("Automaton has several initial states")
        }
        sb.append("\t\tnotfirst = 0;\n")
        sb.append("\tend\n")
        sb.append(s"\tassign _dfa_accept = ${acceptingStates.map("state == " + _).mkString(" || ")};\n")
        sb.append("\talways @(posedge clk) begin\n")
        sb.append("\tnotfirst <= 1;\n")
        sb.append("\tif (notfirst) begin\n")
        var _firstIf = true
        states foreach { 
            state =>
            {
                for (sigma <- alphabet) {
                    val succs = hypothesis.getSuccessors(state, sigma);
                    for (succ <- succs) {
                        if (_firstIf){
                            sb.append("\t\t\t")
                            _firstIf = false
                        } else {
                            sb.append("\t\t\telse ")
                        }
                        sb.append(s"if (state == ${hypothesis.stateIDs.getStateId(state)} && _rt_$sigma) state <= ${hypothesis.stateIDs.getStateId(succ)};\n")
                    }
                }
            }
        }
        sb.append("\tend else begin\n")
        hypothesis.getInitialStates().toList match {
            case List() => throw Exception("No initial state found")
            case List(i) => sb.append(s"\t\tstate <= ${hypothesis.stateIDs.getStateId(i)};\n")
            case _ => throw Exception("Automaton has several initial states")
        }
        sb.append("\tend\n")
        sb.append("end\n")
        sb.append("endmodule\n")
        sb.toString
    }
}


abstract class SynthesisOracle{
    def synthesize() : SynthesisResult
    def synthesizeIntersection(hypothesis: DFA[_, String]) : SynthesisResult
}
/** Use abssynthe to check the realizability of the given smvFile.
 *  If realizable, return the closed system controlled by the controller (ensuring safety)
 *  If not realizable, return the closed system controlled by the adversary strategy (ensuring reachability of the error state)
 */
class AbssyntheOracle(verilog : Verilog) extends SynthesisOracle{
    val tmpDirPath = configuration.globalConfiguration.tmpDirPath()
    private val logger = LoggerFactory.getLogger(classOf[AbssyntheOracle])

    override def synthesizeIntersection(hypothesis: DFA[_, String]) : SynthesisResult = {
        val productFSM = verilog.intersectedWith(hypothesis)
        val productFSMFile = Files.createTempFile(tmpDirPath, "product", ".v").toFile()
        val pw = PrintWriter(productFSMFile)
        pw.write(productFSM)
        pw.close()
        synthesize(productFSMFile)
    }
    override def synthesize() : SynthesisResult = {
        synthesize(verilog.inputFile)
    }
    private def synthesize(verilogFile : File) : SynthesisResult = {        
        logger.info("Calling synthesis for file " + verilogFile.toString)
        val tmpDirPath = configuration.globalConfiguration.tmpDirPath()
        // val cmd = s"echo \"read_model -i ${verilogFile.toString}; flatten_hierarchy; encode_variables; build_boolean_model; write_aiger_model -p ${File(tmpDirPath.toFile,smvFile.getName).toString}; quit;\"" #| "nuXmv -int"
        // For conversion to aag
        // 1) Convert to blif with yosys
        //    echo "read_verilog $1; hierarchy; proc; opt; memory; opt; techmap; opt; write_blif $1.blif" | yosys
        // 2) Convert to aig with abc
        //    berkeley-abc -c "read_blif a.blig; strash; refactor; rewrite; dfraig; write_aiger -s a.aig"
        // 3) Convert aig to aag
        //    aigtoaig a.aig a.aag
        val tmpFilename = File(tmpDirPath.toFile,verilogFile.getName).toString
        val cmd_yosys = s"echo \"read_verilog ${verilogFile.toString}; hierarchy; proc; opt; memory; opt; techmap; opt; write_blif $tmpFilename.blif\"" #| "yosys -q"
        // val cmd_abc = s"berkeley-abc -c \"read_blif ${tmpFilename}.blif; strash; refactor; rewrite; dfraig; write_aiger -s $tmpFilename.aig\""
        val cmd_abc = s"berkeley-abc -c \"read_blif ${tmpFilename}.blif; strash; write_aiger -s $tmpFilename.aig\""
        // val cmd_aig = s"./resources/scripts/aig/bad2out $tmpFilename.aig $tmpFilename.aag"
        val cmd_aig = s"bad2out $tmpFilename.aig $tmpFilename.aag"
        if (configuration.globalConfiguration.verbose)
            System.out.println(cmd_yosys)
        
        if (cmd_yosys.! != 0){
            throw Exception("Yosys returned an error")
        }
        if (configuration.globalConfiguration.verbose)
            System.out.println(cmd_abc)
        if (cmd_abc.! != 0){
            throw Exception("berkeley-abc returned an error")
        }
        if (configuration.globalConfiguration.verbose)
            System.out.println(cmd_aig)
        if (cmd_aig.! != 0){
            throw Exception("aigtoaig returned an error")
        }
        val witnessStrategyFile = Files.createTempFile(tmpDirPath, "strategy", ".aag").toFile()
        val cmd_synth = s"abssynthe -v LD ${tmpFilename}.aag -o ${witnessStrategyFile.toString}"
        if (configuration.globalConfiguration.verbose)
            System.out.println(cmd_synth)
        val output_synth = cmd_synth.!
        val smvWitnessStrategyFile = File(witnessStrategyFile.toString + ".smv")
        val cmd_convert = s"aigtosmv -c ${witnessStrategyFile.toString}" #> smvWitnessStrategyFile
        val cmd_append = s"echo \"INVARSPEC !${verilog.errorName}\n\"" #>> smvWitnessStrategyFile
        if (configuration.globalConfiguration.verbose)
            System.out.println(cmd_convert)
            System.out.println(cmd_append)
        output_synth match {
            case 10 => // Realizable
                cmd_convert.!
                cmd_append.!
                Controllable(smvWitnessStrategyFile)
            case 20 => // Not realizable
                cmd_convert.!
                cmd_append.!
                Uncontrollable(smvWitnessStrategyFile)
            case _ => throw Exception("Abssynthe returned unexpected result")
        }
    }
}