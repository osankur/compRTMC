package fr.irisa.comprtmc

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

abstract class FSMIntersectionOracle {
  def alphabet: List[String]

  def checkIntersection(
      otherModule: DFA[_, String]
  ): Option[FSMOracles.CounterExample]

}

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
            newLines.append("MODULE _rtmc_main\n")
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
        "The main module must contain the precise specification \"INVARSPEC !err\" where err is a define."
      )
    }
    val alphabetAsArgs = alphabet.map("fsm._rt_"+_).mkString(", ")
    val fsm = newLines.mkString("\n")
    val strB = StringBuilder()
    strB.append("MODULE main\n")
    strB.append("VAR\n")
    strB.append("\tfsm : _rtmc_main;\n")
    strB.append(
      "\ttime : _rtmc_time(%s);\n".format(alphabetAsArgs)
    )
    strB.append("\nDEFINE\t _rt_nonexcl := ")
    val uniquePairs = for {
      x <- alphabet
      y <- alphabet
      if x < y
    } yield (x, y)
    // strB.append(uniquePairs.map((x,y) => "fsm._rt_%s & !fsm._rt_%s | !fsm._rt_%s & fsm._rt_%s".format(x,y,x,y)).mkString(" | "))
    strB.append(uniquePairs.map((x,y) => "fsm._rt_%s & fsm._rt_%s".format(x,y)).mkString(" | "))
    strB.append(";\n")
    strB.append("INVARSPEC\n\t !_rt_nonexcl & (time.accepting -> !fsm.err)\n")
    SMVStructure(fsm, strB.toString, alphabet.toList)
  }
  def fsm = _structure.fsm
  def main = _structure.main
  def alphabet = _structure.alphabet
}

class SMVIntersectionOracle(
    smv: SMV
) extends FSMIntersectionOracle {

  private val logger = LoggerFactory.getLogger(classOf[SMVIntersectionOracle])

  val tmpDirPath = FileSystems.getDefault().getPath(ProgramConfiguration.globalConfiguration.tmpDirName);
  tmpDirPath.toFile().mkdirs()

  override def alphabet = smv.alphabet

  def makeIntersectionMonitor(hypothesis: DFA[_, String]): String = {
    val timeModuleB = StringBuilder()
    val strTransitions = StringBuilder()
    val acceptingStates = ListBuffer[String]()
    // There should be a unique init state since DFA
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
          for (sigma <- alphabet) {
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
        acceptingStates.map("state = " + _).mkString(" | ")
      )
    )
    val newSMV = StringBuilder()
    newSMV.append(smv.fsm)
    newSMV.append(timeModuleB.toString)
    newSMV.append(smv.main)
    newSMV.toString
  }

  override def checkIntersection(
      timeModule: DFA[_, String]
  ): Option[FSMOracles.CounterExample] = {
    val regInvariantTrue ="[\\s\\S]*-- invariant.*is true[\\s\\S]*".r
    val productFile =
      Files.createTempFile(tmpDirPath, "product", ".smv").toFile()
    val pw = PrintWriter(productFile)
    pw.write(makeIntersectionMonitor(timeModule))
    pw.close()    
    val cmd =
      ProgramConfiguration.globalConfiguration.fsmAlgorithm match {
        case FSM.BDDAlgorithm =>
          "echo \"read_model -i %s; go; check_invar; show_traces -v; quit;\"".format(productFile) #| "%s -int".format(ProgramConfiguration.globalConfiguration.fsmModelChecker)
        case FSM.IC3Algorithm =>
          "echo \"read_model -i %s; go_msat; check_invar_ic3; show_traces -v; quit;\"".format(productFile) #| "%s -int".format(ProgramConfiguration.globalConfiguration.fsmModelChecker)
      }
    System.out.println(YELLOW + "Model checking FSM with given hypothesis TA" + RESET)
    System.out.println(cmd)
    val output = cmd.!!
    // System.out.println(output)
    if (!ProgramConfiguration.globalConfiguration.keepTmpFiles){
      productFile.delete()
    }    
    if (output.contains("Trace Type: Counterexample")) {
      if(output.contains("_rt_nonexcl = TRUE")){
        throw ParseError("The real-time labels _rt_ must be mutually exclusive")
      }
      // The output should contain the counterexample twice
      // Return the first occurrence (nonverbose), but use the second occurrence (verbose) to extract trace
      val (cexStr,cexVerboseStr) = 
        {
          // val reg = ".*Trace Type: Counterexample.*|.*#######.*|.*Trace Description: AG alpha Counterexample.*".r
          val reg = ".*Trace Type: Counterexample.*".r
          val parts = reg.split(output).map(_.strip()).filter(_.length > 0)
          (parts(1).strip, parts(2).strip())
        }
      // System.out.println(cexStr)
      // System.out.println("*** VERBOSE *** ")
      // System.out.println(cexVerboseStr)
      val cexLines = cexVerboseStr.split("\n")
      val regInput = "\\s*-> Input:.*<-\\s*".r
      val regState = "\\s*-> State:.*<-\\s*".r
      val regAssignmentTRUE = "\\s*fsm._rt_(.+)\\s*=\\s*TRUE\\s*".r
      val trace = ListBuffer[String]()
      var readingInput = false
      cexLines foreach { line =>
        line match {
          case regAssignmentTRUE(v) =>
            val vStripped = v.strip()
            if (alphabet.contains(vStripped)) {
              trace.append(vStripped)
            }
          case _ =>()
        }
      }
      Some(FSMOracles.CounterExample(cexStr, trace.toList.filter(_ != "")))
    } else if (regInvariantTrue.matches(output)){
      None
    } else {
      System.out.println(output)
      throw ParseError("FSM Model checker did not return an expected result")
    }
  }
}
object FSMOracles {
  case class CounterExample(cexDescription: String, cexTrace: List[String])
      extends Exception

  object Factory {
    def getSMVOracle(
        smvFile: File,
    ): FSMIntersectionOracle = {
      SMVIntersectionOracle(SMV(smvFile))
    }
  }
}
