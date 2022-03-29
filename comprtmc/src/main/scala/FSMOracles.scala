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
    // val regAlphabet =
    //   "\\s*_rt_\\s*:\\s*-1..([0-9]*)\\s*;\\s*".r // "_rt_ : i..j;"
    val regLetter =
      "\\s*_rt_(.+)\\s*:=.*".r // "_rt_ : i..j;"
    // val regTau =
    //   "\\s*(.+)\\s*:=\\s*_rt_\\s*=\\s*-1\\s*;\\s*".r // "_rt_ : i..j;"
      // Name of the defined symbol for _rt_ = -1
    var tau = ""
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
    strB.append("\nDEFINE\t _rt_excl := ")
    val uniquePairs = for {
      x <- alphabet
      y <- alphabet
      if x < y
    } yield (x, y)
    // strB.append(uniquePairs.map((x,y) => "fsm._rt_%s & !fsm._rt_%s | !fsm._rt_%s & fsm._rt_%s".format(x,y,x,y)).mkString(" | "))
    strB.append(uniquePairs.map((x,y) => "fsm._rt_%s & fsm._rt_%s".format(x,y)).mkString(" | "))
    strB.append(";\n")
    strB.append("INVARSPEC\n\t !_rt_excl & (time.accepting -> !fsm.err)\n")
    SMVStructure(fsm, strB.toString, alphabet.toList)
  }
  def fsm = _structure.fsm
  def main = _structure.main
  def alphabet = _structure.alphabet
}

class SMVIntersectionOracle(
    smv: SMV,
    tmpDirName: String = "./.crtmc/"
) extends FSMIntersectionOracle {
  sealed trait ModelChecker
  case object NuSMV extends ModelChecker {
    override def toString: String = "NuSMV"
  }
  case object nuXmv extends ModelChecker {
    override def toString: String = "nuXmv"
  }

  sealed trait Algorithm
  case object BDD extends Algorithm
  case object IC3 extends Algorithm

  private val logger = LoggerFactory.getLogger(classOf[SMVIntersectionOracle])
  private var algorithm: Algorithm = BDD
  private var modelChecker: ModelChecker = NuSMV
  val tmpDirPath = FileSystems.getDefault().getPath(tmpDirName);
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
    val productFile =
      Files.createTempFile(tmpDirPath, "product", ".smv").toFile()
    val pw = PrintWriter(productFile)
    pw.write(makeIntersectionMonitor(timeModule))
    pw.close()
    val cmd =
      this.algorithm match {
        case BDD =>
          "%s %s".format(this.modelChecker, productFile)
        case IC3 =>
          productFile.delete()
          throw Exception("Come back later")
      }
    System.out.println(cmd)
    val output = cmd.!!
    System.out.println(output)    
    //productFile.delete()
    if (output.contains("Trace Type: Counterexample")) {
      if(output.contains("_rt_excl = TRUE")){
        throw ParseError("The real-time labels _rt_ must be mutually exclusive")
      }
      val cexStr = output.split("Trace Type: Counterexample")(1).strip()
      val cexLines = cexStr.split("\n")
      val regInput = "\\s*-> Input:.*<-\\s*".r
      val regState = "\\s*-> State:.*<-\\s*".r
      val regAssignmentTRUE = "\\s*fsm._rt_(.+)\\s*=\\s*TRUE\\s*".r
      val trace = ListBuffer[String]()
      var readingInput = false
      var lastLetter = ""
      // System.out.println("Trace:\n" + cexLines.mkString("\n"))
      cexLines foreach { line =>
        line match {
          case regAssignmentTRUE(v) =>
            val vStripped = v.strip()
            if (readingInput && alphabet.contains(vStripped)) {
              lastLetter = vStripped
            }
          case regInput() =>
            readingInput = true
          case regState() =>
            trace.append(lastLetter)
            readingInput = false
          case _ => ()
        }
      }
      Some(FSMOracles.CounterExample(cexStr, trace.toList.tail))
    } else {
      None
    }
  }
}
object FSMOracles {
  case class CounterExample(cexDescription: String, cexTrace: List[String])
      extends Exception

  object Factory {
    def getSMVOracle(
        smvFile: File,
        tmpDirName: String = "./.crtmc/"
    ): FSMIntersectionOracle = {
      SMVIntersectionOracle(SMV(smvFile), tmpDirName = tmpDirName)
    }
  }
}