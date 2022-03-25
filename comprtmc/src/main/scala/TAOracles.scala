package fr.irisa.comprtmc

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import collection.JavaConverters._
import collection.convert.ImplicitConversions._
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.Buffer
import scala.sys.process._
import scala.io.Source
import scala.collection.mutable.StringBuilder
import scala.collection.mutable.HashMap
import de.learnlib.util.MQUtil;
import java.io.File
import java.nio.file._
import java.io.PrintWriter
import de.learnlib.api.oracle.EquivalenceOracle
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.api.oracle._
import de.learnlib.api.oracle.MembershipOracle
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.util.automata.builders.AutomatonBuilders;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.words.impl.Alphabets;
import net.automatalib.words._
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.util.automata.builders.AutomatonBuilders;

case class BadTimedAutomaton(msg: String) extends Exception(msg)
case class FailedTAModelChecking(msg: String) extends Exception(msg)

/** Read TChecker TA from given file, and store the tuple (events,
  * eventsOfProcesses, core, syncs) where events is the list of all events,
  * eventsOfProcesses maps process names to events that they include, core is
  * the list of lines of the input file except for the sync instructions, and
  * syncs contains lists of tuples encoding the syncs
  */
class TCheckerTA(inputFile: java.io.File) {
  class TCheckerTAStructure(
      val events: List[String],
      val eventsOfProcesses: HashMap[String, Set[String]],
      val syncs: List[List[(String, String)]],
      val core: String
  )
  private val logger = LoggerFactory.getLogger(classOf[TCheckerTA])

  private val taComponents = {
    val lines = Source.fromFile(inputFile).getLines().toList
    val regEvent = "\\s*event:(.*)\\s*".r
    val regSync = "\\s*sync:(.*)\\s*".r
    val regProcess = "\\s*process:(.*)\\s*".r
    val regEdge = "\\s*edge:[^:]*:[^:]*:[^:]*:([^{]*).*".r
    val events = lines.flatMap({
      case regEvent(event) => Some(event.strip())
      case _               => None
    })
    val syncs = lines.flatMap({
      case regSync(syncs) =>
        Some(
          syncs
            .split(":")
            .toList
            .map(
              { l =>
                val elts = l.split("@").map(_.strip()).toArray
                (elts(0), elts(1))
              }
            )
        )
      case _ => None
    })

    val eventsOfProcesses = HashMap[String, Set[String]]()
    var currentProcess = ""
    lines.foreach {
      case regProcess(monitorProcessName) =>
        currentProcess = monitorProcessName
        eventsOfProcesses += (monitorProcessName -> Set[String]())
      // System.out.println("Now reading process: " + monitorProcessName)
      case regEdge(sync) =>
        eventsOfProcesses += (currentProcess -> (eventsOfProcesses(
          currentProcess
        ) + sync))
      // System.out.println("Adding sync: " + sync)
      case _ => ()
    }
    val core = lines.filter(line => !line.startsWith("sync:")).mkString("\n")
    logger.debug("Events: " + events)
    logger.debug("Syncs: " + syncs)
    logger.debug("" + eventsOfProcesses)
    TCheckerTAStructure(events, eventsOfProcesses, syncs, core)
  }

  def events: List[String] = this.taComponents.events
  def syncs: List[List[(String, String)]] = taComponents.syncs
  def eventsOfProcesses: HashMap[String, Set[String]] =
    taComponents.eventsOfProcesses
  def core: String = taComponents.core

}

class TCheckerMonitorMaker(
    ta: TCheckerTA,
    alphabet: Alphabet[String],
    monitorProcessName: String = "_crtmc_mon"
) {

  /** Textual declaration of sync labels where the monitor process participates
    * in all synchronized edges with a label in alphabet.
    */
  val productTASyncs: String = {
    val strB = StringBuilder()
    val alphabetSet = alphabet.asScala.toSet
    ta.syncs.foreach { syncLine =>
      val newSyncLine =
        syncLine.map(_._2).toSet.intersect(alphabetSet).toList match
          case Nil          => syncLine
          case event :: Nil => (monitorProcessName, event) :: syncLine
          case _ =>
            throw BadTimedAutomaton(
              "Timed automaton must not have synchronized transitions on multiple external sync labels"
            )
      strB.append("sync:")
      strB.append(newSyncLine.map(_ + "@" + _).mkString(":"))
      strB.append("\n")
    }
    strB.result()
  }
  def acceptLabel = "_crtmc_accept"

  /** Returns textual description of TA made of the product of given TA, and a
    * monitor that reads a given word. The acceptLabel is reachable if the
    * intersection is non-empty
    */
  def makeWordMonitor(word: Buffer[String]): String = {
    // Build product automaton
    val strB = StringBuilder()
    strB.append(ta.core)
    strB.append("\n")
    strB.append(
      "# Trace Monitor Process\nprocess:%s\nlocation:%s:q0{initial:}\n".format(
        monitorProcessName,
        monitorProcessName
      )
    )
    for (i <- 1 to word.length - 1) {
      strB.append("location:%s:q%d\n".format(monitorProcessName, i))
    }
    strB.append(
      "location:%s:q%d{labels:%s}\n".format(
        monitorProcessName,
        word.length,
        acceptLabel
      )
    )
    word.zipWithIndex.foreach { (label, i) =>
      strB.append(
        "edge:%s:q%d:q%d:%s\n".format(monitorProcessName, i, i + 1, label)
      )
    }
    strB.append("\n")
    strB.append(productTASyncs.mkString)
    return strB.toString
  }

  /** Returns textual description of TA made of the product of given TA, and the
    * complement of the given DFA. The acceptLabel is reachable TA is not
    * included in the DFA, that TA /\ comp(DFA) is non-empty.
    */
  def makeInclusionMonitor(hypothesis: DFA[_, String]): String = {
    val strStates = StringBuilder()
    val strTransitions = StringBuilder()
    // val alphabetSet = alphabet.asScala.toSet
    val initStates = hypothesis.getInitialStates().asScala
    strStates.append("process:" + monitorProcessName + "\n")
    hypothesis
      .getStates()
      .asScala
      .foreach(state =>
        strStates
          .append("location:%s:q%s{".format(monitorProcessName, state.toString))
        val attributes = ArrayBuffer[String]()
        if (initStates.contains(state)) then {
          attributes.append("initial:")
        }
        // revert accepting and non-accepting states
        if (!hypothesis.isAccepting(state)) {
          attributes.append("labels:_crtmc_accept")
        }
        strStates.append(attributes.mkString(":"))
        strStates.append("}\n")
        for (sigma <- alphabet.toList) {
          val succs = hypothesis.getSuccessors(state, sigma);
          if (!succs.isEmpty()) then {
            for (succ <- succs) {
              strTransitions.append(
                "edge:%s:q%s:q%s:%s\n".format(
                  monitorProcessName,
                  state.toString,
                  succ.toString,
                  sigma
                )
              )
            }
          }
        }
      )
      val taB = StringBuilder()
      taB.append(ta.core)
      taB.append(strStates.append(strTransitions.toString).toString)
      taB.append("\n")
      taB.append(productTASyncs)
      taB.toString
  }

}

class TCheckerMembershipOracle(
    alphabet: Alphabet[String],
    ta : TCheckerTA,    
    tmpDirName: String = "./.crtmc/"
) extends MembershipOracle[String, Boolean] {
  private val logger = LoggerFactory.getLogger(classOf[TCheckerTA])
  private val taMonitorMaker = TCheckerMonitorMaker(ta, alphabet)
  val tmpDirPath = FileSystems.getDefault().getPath(tmpDirName);
  tmpDirPath.toFile().mkdirs()

  override def processQueries(
      queries: java.util.Collection[
        ? <: de.learnlib.api.query.Query[String, Boolean]
      ]
  ): Unit = {
    MQUtil.answerQueries(this, queries);
  }

  override def answerQuery(
      prefix: Word[String],
      suffix: Word[String]
  ): Boolean = {
    val trace = prefix.asList().asScala ++ suffix.asList().asScala
    val productFile =
      Files.createTempFile(tmpDirPath, "productMem", ".ta").toFile()
    val pw = PrintWriter(productFile)
    pw.write(taMonitorMaker.makeWordMonitor(trace))
    pw.close()

    // Model check product automaton
    logger.debug("Running TChecker for a membership query")
    val output = "tck-reach -a covreach %s -l %s"
      .format(productFile.toString, taMonitorMaker.acceptLabel)
      .!!
    productFile.delete()
    if (output.contains("REACHABLE false")) then {
      false
    } else if (output.contains("REACHABLE true")) then {
      true
    } else {
      throw FailedTAModelChecking(output)
    }
  }
}

class TCheckerInclusionOracle(
    alphabet: Alphabet[String],
    ta: TCheckerTA,
    tmpDirName: String = "./.crtmc/"
) extends EquivalenceOracle.DFAEquivalenceOracle[String] {
  private val logger = LoggerFactory.getLogger(classOf[TCheckerInclusionOracle])
  private val taMonitorMaker = TCheckerMonitorMaker(ta, alphabet)
  val tmpDirPath = FileSystems.getDefault().getPath(tmpDirName);
  tmpDirPath.toFile().mkdirs()

  override def findCounterExample(
      hypothesis: DFA[_, String],
      inputs: java.util.Collection[? <: String]
  ): DefaultQuery[String, java.lang.Boolean] = {
    val productFile =
      Files.createTempFile(tmpDirPath, "productEq", ".ta").toFile()
    val pw = PrintWriter(productFile)
    pw.write(taMonitorMaker.makeInclusionMonitor(hypothesis))
    pw.close()

    val certFile =
      Files.createTempFile(tmpDirPath, "certEq", ".dot").toFile()

    // Model check product automaton
    logger.debug("Running TChecker for a membership query")
    val output = "tck-reach -a covreach %s -l %s -C %s"
      .format(productFile.toString, taMonitorMaker.acceptLabel, certFile.toString).!!
    // System.out.println(output)
    
    // productFile.delete()
    if (output.contains("REACHABLE false")) then {
      null // Inclusion holds
    } else if (output.contains("REACHABLE true")) then {
      val lines = Source.fromFile(certFile).getLines().toList
      val word = ArrayBuffer[String]()
      val regEdge = "\\s*([0-9]+)\\s*->\\s*([0-9]+)\\s*\\[.*vedge=\"<(.*)>\".*\\]".r
      var index = 0
      lines.foreach({
        case regEdge(i,j,syncList) => 
          if(index != i.toInt || index+1!=j.toInt){
            throw Exception("Non sequential ordering of transitions in certificate file: %s -> %s".format(i,j))
          }
          System.out.println(syncList)
          index = index + 1
        case _ => ()
      })
      certFile.delete()
      null
      // return DefaultQuery[String, java.lang.Boolean](Word.fromArray[Character](words.toArray,0,words.length), java.lang.Boolean.TRUE)
    } else {
      throw FailedTAModelChecking(output)
    }
  }

}
