package fr.irisa.comprtmc.fsm

import io.AnsiColor._
import scala.sys.process._

import scala.io.Source
import scala.collection.mutable.StringBuilder
import scala.collection.mutable.ListBuffer
import collection.JavaConverters._
import collection.convert.ImplicitConversions._
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import net.automatalib.automata.fsa.DFA;
import net.automatalib.words.impl.Alphabets;
import net.automatalib.words._
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.util.automata.builders.AutomatonBuilders;
import net.automatalib.visualization.Visualization;
import de.learnlib.api.oracle.EquivalenceOracle
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.api.oracle._
import java.io.File
import java.io.PrintWriter
import java.nio.file._

import fr.irisa.comprtmc.configuration.ParseError
import fr.irisa.comprtmc.configuration
import fr.irisa.comprtmc.statistics
import fr.irisa.comprtmc.ta.TCheckerMonitorMaker
import fr.irisa.comprtmc.ta.TCheckerTA

case class CounterExample(cexDescription: String, cexTrace: List[String])
    extends Exception

abstract class FSMIntersectionOracle {
  /** Synchronization alphabet between FSM and TA
   */
  def alphabet: List[String]

  /** Check the intersection of the given dfa with the FSM.
   * 
   *  @return None if the intersection is empty, and CounterExample(cexStr, trace) object
   *  where cexStr is the textual description as output by the model checker, and
   *  trace is a word over `alphabet` that is in the intersection of the two languages.
   */
  def checkIntersection(
      dfa: DFA[_, String]
  ): Option[CounterExample]

}

/** Light parser of SMV files.
 */
class SMV(inputFile: java.io.File) {
  class SMVStructure(
      // the body of the main module renamed _rtmc_main
      val fsm: String,
      // the body of the main module of the compound model
      val main: String,
      val alphabet: List[String]
  )

  private val logger = LoggerFactory.getLogger(classOf[SMV])

  private val _structure = {
    val lines = Source.fromFile(inputFile).getLines().toList
    val newLines = ListBuffer[String]()
    val alphabet = ListBuffer[String]()
    var alphabetSize = -2
    var invarErr = false
    var inMain = false // are we reading inside module main?
    val regModuleMain = "\\s*MODULE\\s*(.*)\\s*".r
    val regError = "\\s*INVARSPEC\\s*!([a-zA-Z0-9_]*)\\s*".r
    val regLetter =
      "\\s*_rt_(.+)\\s*:=.*".r
    val regInputLetter = "\\s*_rt_(.+)\\s*:\\s*boolean;\\s*".r

    lines.foreach { line =>
      line match
        case regModuleMain(name) =>
          if (name == "main") then {
            inMain = true
            newLines.append("MODULE main\n")
          } else {
            inMain = false
            newLines.append(line)
          }
        case regLetter(sigma) =>
          alphabet.append(sigma.strip())
          newLines.append(line)
        case regInputLetter(sigma) =>
          alphabet.append(sigma.strip())
          newLines.append(line)
        case regError(spec) =>
          invarErr = true
        case _ => 
          newLines.append(line)
    }
    if (alphabet.length <= 0) {
      throw ParseError(
        "Alphabet could not be identified. The SMV model must contain defines _rt_.*"
      )
    }
    if (!invarErr) {
      throw ParseError(
        "The main module must contain the precise specification \"INVARSPEC !error\"."
      )
    }
    // val alphabetAsArgs = alphabet.map("fsm._rt_"+_).mkString(", ")
    val alphabetAsArgs = alphabet.map("_rt_"+_).mkString(", ")
    val fsm = newLines.mkString("\n")
    val strB = StringBuilder()
    // strB.append("MODULE main\n")
    strB.append("VAR\n")
    // strB.append("\tfsm : _rtmc_main;\n")
    strB.append(
      "\ttime : _rtmc_time(%s);\n".format(alphabetAsArgs)
    )
    strB.append("\nDEFINE\t _rt_nonexcl := ")
    if (alphabet.size > 1){
      val uniquePairs = for {
        x <- alphabet
        y <- alphabet
        if x < y
      } yield (x, y)
      // strB.append(uniquePairs.map((x,y) => "fsm._rt_%s & fsm._rt_%s".format(x,y)).mkString(" | "))
      strB.append(uniquePairs.map((x,y) => "_rt_%s & _rt_%s".format(x,y)).mkString(" | "))
      strB.append(";\n")
    } else {
      strB.append("FALSE;\n")
    }
    strB.append("DEFINE\n\t_rtmc_error :=  (time.accepting & error);\n")
    strB.append("INVARSPEC\n\t !_rtmc_error\n")
    strB.append("INVARSPEC\n\t !_rt_nonexcl\n")
    SMVStructure(fsm, strB.toString, alphabet.toList)
  }
  def fsm = _structure.fsm
  def main = _structure.main
  def alphabet = _structure.alphabet
}

class SMVMonitorMaker(smv : SMV){
  def makeIntersectionMonitor(hypothesis: DFA[_, String]): String = {
    val timeModuleB = StringBuilder()
    val strTransitions = StringBuilder()
    val acceptingStates = ListBuffer[String]()
    // There should be a unique init state since DFA
    assert(hypothesis.getInitialStates().size == 1)
    val initialStates = hypothesis
      .getInitialStates()
      .asScala
      .map("state = q" + _.toString)
      .mkString("| ")

    val states = hypothesis.getStates().asScala.toList

    timeModuleB.append(
      "\nMODULE _rtmc_time(%s)\nVAR\n".format(smv.alphabet.mkString(", "))
    )
    timeModuleB.append(
      "\t state : {%s};\n".format(
        states
          .map("q" + _)
          .mkString(", ")
      )
    )
    timeModuleB.append("INIT\n\t %s\n".format(initialStates))
    timeModuleB.append("\nASSIGN\n")
    if (states.length > 1) {
      timeModuleB.append("\t next(state) := case\n")
      states foreach { state =>
        {
          for (sigma <- smv.alphabet) {
            val succs = hypothesis.getSuccessors(state, sigma);
            for (succ <- succs) {
              timeModuleB.append(
                "\t state = q%s & %s : q%s;\n".format(
                  state,
                  sigma,
                  succ
                )
              )
            }
          }
        }
      }
      timeModuleB.append("\t TRUE : state;\n")
      timeModuleB.append("esac;\n")
    }
    states foreach { state =>
      if (hypothesis.isAccepting(state)) then {
        acceptingStates.append("q" + state.toString)
      }
    }
    timeModuleB.append(
      "DEFINE\n\t accepting := %s;\n\n".format(
        if (acceptingStates.length > 0){
          acceptingStates.map("state = " + _).mkString(" | ")
        } else { "FALSE" }
      )
    )
    val newSMV = StringBuilder()
    newSMV.append(timeModuleB.toString)
    newSMV.append(smv.fsm)
    newSMV.append(smv.main)
    newSMV.toString
  }
}

class SMVIntersectionOracle(
    smv: SMV
) extends FSMIntersectionOracle {

  private val logger = LoggerFactory.getLogger(classOf[SMVIntersectionOracle])

  val tmpDirPath = FileSystems.getDefault().getPath(configuration.globalConfiguration.tmpDirName);
  tmpDirPath.toFile().mkdirs()

  override def alphabet = smv.alphabet
  val smvMonitorMaker = SMVMonitorMaker(smv)

  override def checkIntersection(
      timeModule: DFA[_, String]
  ): Option[CounterExample] = {
    statistics.Counters.incrementCounter("SMVIntersectionOracle")

    val regInvariantTrue ="[\\s\\S]*-- invariant.*is true[\\s\\S]*".r
    val productFile =
      Files.createTempFile(tmpDirPath, "product", ".smv").toFile()
    val pw = PrintWriter(productFile)
    pw.write(smvMonitorMaker.makeIntersectionMonitor(timeModule))
    pw.close()
    val cmd =
      configuration.globalConfiguration.fsmAlgorithm match {
        case configuration.FSM.BDDAlgorithm =>
          "echo \"read_model -i %s; go; check_invar -p \"!_rtmc_error;\"; show_traces -v; quit;\"".format(productFile) #| "%s -int".format(configuration.globalConfiguration.fsmModelChecker)
        case _ =>
          "echo \"read_model -i %s; go_msat; check_invar_ic3 -p \"!_rtmc_error;\"; show_traces -v; quit;\"".format(productFile) #| "%s -int".format(configuration.globalConfiguration.fsmModelChecker)
      }
    System.out.println(YELLOW + s"Model checking FSM with given hypothesis TA: $productFile" + RESET)
    System.out.println(cmd)
    val output = cmd.!!
    if (!configuration.globalConfiguration.keepTmpFiles){
      productFile.delete()
    }    
    if (output.contains("Trace Type: Counterexample")) {
      // if(output.contains("_rt_nonexcl = TRUE")){
      //   throw ParseError("The real-time labels _rt_ must be mutually exclusive")
      // }
      // The output should contain the counterexample twice
      // Return the first occurrence (nonverbose), but use the second occurrence (verbose) to extract trace
      val (cexStr,cexVerboseStr) = 
        {
          val reg = ".*Trace Type: Counterexample.*".r
          val parts = reg.split(output).map(_.strip()).filter(_.length > 0)
          (parts(1).strip, parts(2).strip())
        }
      val regInput = "\\s*-> Input:.*<-\\s*".r
      val regState = "\\s*-> State:.*<-\\s*".r
      val regAssignmentTRUE = "\\s*_rt_(.+)\\s*=\\s*TRUE\\s*".r
      //val regError = "error = TRUE"
      val regError = "_rtmc_error = TRUE"
      val trace = ListBuffer[String]()
      var readingInput = false
      val cexLines = regState.split(cexVerboseStr) // List of all states in the cex
      val filteredStates = ListBuffer[String]() // List of all states but those with fsm.error = TRUE
      var errorHasBeenSeen = false

      // We remove states with fsm.err = TRUE since sync labels take arbitrary values along these states, and we want to cut the trace when err is reached
      for ((state,i) <- cexLines.zipWithIndex){
        if (state.contains(regError)){
          errorHasBeenSeen = true;
        }
        if (!errorHasBeenSeen){
          filteredStates.append(state)
        }
        // System.out.println("-> state %d (error: ".format(i) + state.contains(regError)+ ")\n")
        // System.out.println(state.split("\n").filter(!_.contains("fsm.a")).filter(_.length >0).mkString("\n"))
      }
      filteredStates.foreach{
        _.split("\n").foreach{
          case regAssignmentTRUE(v) =>
            val vStripped = v.strip()
            if (alphabet.contains(vStripped)) {
              trace.append(vStripped)
            }
          case _ =>()
          }
      }
      Some(CounterExample(cexStr, trace.toList.filter(_ != "")))
    } else if (regInvariantTrue.matches(output)){
      None
    } else {
      System.out.println(output)
      throw ParseError("FSM Model checker did not return an expected result")
    }
  }
}


class TCheckerIntersectionOracle(
    fsm: TCheckerTA
) extends FSMIntersectionOracle {
  private val taMonitorMaker = TCheckerMonitorMaker(fsm, Alphabets.fromList(this.alphabet), Some("error"))
  throw Exception("not supported")
  override def alphabet : List[String] = {
    fsm.externEvents
  }
  override def checkIntersection(hypothesis: DFA[?, String]): 
    Option[CounterExample] = {
      val productTA = taMonitorMaker.makeDFAIntersecter(hypothesis, false)
      System.out.println("Checking intersection: ")
      statistics.Counters.incrementCounter("TCheckerIntersectionOracle")

      taMonitorMaker.checkEmpty(productTA, true) match {
        case taMonitorMaker.Empty => 
          None
        case taMonitorMaker.NonEmpty(cexStr) => 
          val trace = fsm.getTraceFromCexDescription(cexStr.split("\n").toList).filter(alphabet.contains(_))
          Some(CounterExample(cexStr,trace))
      }
    }
}


object Factory {
  def getSMVOracle(
      smvFile: File,
  ): FSMIntersectionOracle = {
    SMVIntersectionOracle(SMV(smvFile))
  }
  def getTCheckerOracle(
      taFile: File,
  ): FSMIntersectionOracle = {
    TCheckerIntersectionOracle(TCheckerTA(taFile))
  }

}
