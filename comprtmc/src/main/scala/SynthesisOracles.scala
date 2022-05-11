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

class Verilog(inputFile : File){
    // alphabet is the outputs minus the error output
    var alphabet : List[String] = List()
    var inputs : List[String] = List()
    var outputs : List[String] = List()

    // The unique output 
    private var errorName = "error"
    private var moduleName = ""
    private var content = ""

    {
        val lines = Source.fromFile(inputFile).getLines().toList
        content = lines.mkString("\n")
        val regModuleMain = "\\s*module\\s*(.*)\\((.*)\\);\\s*".r
        val _inputs = ListBuffer[String]()
        val _alphabet = ListBuffer[String]()
        val _outputs = ListBuffer[String]()
        lines foreach {
            case regModuleMain(name, params) =>
                moduleName = name
                val args = params.split(",").toList.map(_.split(" ").map(_.strip()).filter(_.length>0))
                args foreach{
                    case arg if arg(0) == "input" => 
                        _inputs.append(arg(1))
                    case arg if arg(0) == "output" => 
                        _outputs.append(arg(1))
                        if (arg(1).startsWith("_rt_")){
                            _alphabet.append(arg(1))
                        } else {
                            if (arg(1) != errorName){
                                throw Exception("There should be one output named 'error', and others must be prefixed with _rt_")
                            }
                        }
                    case arg => 
                        throw Exception("All arguments of a module must be preceded by input or output")
                }
                if (!_outputs.contains(errorName))
                    throw Exception("Module in file " + inputFile.toString + " does not contain an error output")
            case _ => ()
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
    def intersect(hypothesis: DFA[_, String]) : String = {
        val sb = StringBuilder()
        val inputsAsArgList = inputs.map("input " + _).mkString(", ")
        val alphabetAsArgList = alphabet.map("output " + _).mkString(", ")
        val outputsAsArgList = outputs.map("output " + _).mkString(", ")
        
        val inputsAsArgs = inputs.map(x => s".$x($x)").mkString(", ")
        val alphabetAsArgs = alphabet.map(x => s".$x($x)").mkString(", ")
        val outputsAsArgs = inputs.map(x => s".$x($x)").mkString(", ")
        
        sb.append(content+"\n\n")
        sb.append(s"module _rtmc_($inputsAsArgList, output $errorName);\n")
        sb.append(s"\twire _dfa_accept;\n\twire _fsm_err;\n\tassign $errorName = _dfa_accept && _fsm_err;\n")
        alphabet foreach{
            alpha =>
                sb.append(s"\twire $alpha;\n")
        }
        sb.append(s"\tdfa _dfa_($alphabetAsArgs, ._dfa_accept(_dfa_accept));\n")
        sb.append(s"\t$moduleName _fsm_($inputsAsArgs, $alphabetAsArgs, .$errorName(_fsm_err));\n")
        sb.append("endmodule\n")

        assert(hypothesis.getInitialStates().size == 1)
        val states = hypothesis.getStates()
        val statesSize = (log10(states.size)/log10(2.0)+1).toInt
        var acceptingStates = ListBuffer[String]()
        states foreach { state =>
            if (hypothesis.isAccepting(state)) then {
                acceptingStates.append(state.toString)
            }
        }
        sb.append(s"module dfa(${alphabet.map("input " + _).mkString(", ")}, output _dfa_accept);\n")
        sb.append(s"\treg[$statesSize:0] state;\n")
        hypothesis.getInitialStates().toList match {
            case List() => throw Exception("No initial state found")
            case List(i) => sb.append(s"\tinitial state = $i;\n")
            case _ => throw Exception("Automaton has several initial states")
        }
        sb.append(s"\tassign _dfa_accept = ${acceptingStates.map("state == " + _).mkString(" || ")};\n")
        sb.append("\talways #1 begin\n")
        states foreach { 
            state =>
            {
                var _firstIf = true
                for (sigma <- alphabet) {
                    val succs = hypothesis.getSuccessors(state, sigma);
                    for (succ <- succs) {
                        if (_firstIf){
                            sb.append("\t\t")
                            _firstIf = false
                        } else {
                            sb.append("\t\telse ")
                        }
                        sb.append(s"if (state == $state && $sigma) state = $succ;\n")
                    }
                }
            }
        }
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
class AbssyntheOracle(smvFile : File) extends SynthesisOracle{
    val tmpDirPath = configuration.globalConfiguration.tmpDirPath()
    private val fsmMonitorMaker = fsm.SMVMonitorMaker(fsm.SMV(smvFile))
    private val logger = LoggerFactory.getLogger(classOf[AbssyntheOracle])

    override def synthesizeIntersection(hypothesis: DFA[_, String]) : SynthesisResult = {
        val productFSM = fsmMonitorMaker.makeIntersectionMonitor(hypothesis)
        val productFSMFile = Files.createTempFile(tmpDirPath, "product", ".smv").toFile()
        val pw = PrintWriter(productFSMFile)
        pw.write(productFSM)
        pw.close()
        synthesize(productFSMFile)
    }
    override def synthesize() : SynthesisResult = {
        synthesize(smvFile)
    }
    private def synthesize(smvFile : File) : SynthesisResult = {        
        logger.info("Calling synthesis for file " + smvFile.toString)
        val tmpDirPath = configuration.globalConfiguration.tmpDirPath()
        val cmd = s"echo \"read_model -i ${smvFile.toString}; flatten_hierarchy; encode_variables; build_boolean_model; write_aiger_model -p ${File(tmpDirPath.toFile,smvFile.getName).toString}; quit;\"" #| "nuXmv -int"
        System.out.println(cmd)
        val ret = cmd.!
        assert(ret == 0)
        var aigFileName = File(tmpDirPath.toFile,smvFile.getName).toString + "_invar_0.aag"
        val aigLines = Source.fromFile(File(aigFileName)).getLines().toList
        val aagHeaderReg = "aag ([0-9]*) ([0-9]*) ([0-9]*) ([0-9]*) ([0-9]*) ([0-9]*)".r
        val newaigLines = aigLines.head match{
            case aagHeaderReg(ind, latches, inputs, outputs, gates, specs) =>
                (s"aag $ind $latches $inputs 1 $gates") :: aigLines.tail
            case _ =>
                throw Exception(s"Unable to parse header from file $aigFileName")
        }
        val pw = PrintWriter(File(aigFileName))
        pw.write(newaigLines.mkString("\n"))
        pw.write("\n")
        pw.close()
        val witnessStrategyFile = Files.createTempFile(tmpDirPath, "strategy", ".aag").toFile()

        val cmd_synth = s"abssynthe -v LD ${aigFileName} -o ${witnessStrategyFile.toString}"
        System.out.println(cmd_synth)
        val output_synth = cmd_synth.!
        val smvWitnessStrategyFile = File(witnessStrategyFile.toString + ".smv")
        val cmd_convert = s"aigtosmv ${witnessStrategyFile.toString}" #> smvWitnessStrategyFile
        val cmd_append = "echo \"DEFINE err := oo0;\nINVARSPEC !err\n\"" #>> smvWitnessStrategyFile
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