package fr.irisa.comprtmc

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import collection.JavaConverters._
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

case class BadTimedAutomaton(msg: String) extends Exception(msg)
case class FailedTAModelChecking(msg: String) extends Exception(msg)

abstract class TA

/** Read TChecker TA from given file, and store the tuple (events,
  * eventsOfProcesses, core, syncs) where events is the list of all events,
  * eventsOfProcesses maps process names to events that they include, core is
  * the list of lines of the input file except for the sync instructions, and
  * syncs contains lists of tuples encoding the syncs
  */
class TCheckerTA(inputFile: java.io.File) extends TA {
  class TCheckerTAStructure(
      val events: List[String],
      val eventsOfProcesses: HashMap[String, Set[String]],
      val syncs: List[List[(String, String)]],
      val core: String
  )
  val logger = LoggerFactory.getLogger(classOf[TCheckerTA])

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

class TCheckerMembershipOracle(
    alphabet: Alphabet[String],
    ta: TCheckerTA,
    tmpDirName: String = "./.crtmc/"
) extends MembershipOracle[String, Boolean] {
  private val logger = LoggerFactory.getLogger(classOf[TCheckerTA])

  val tmpDirPath = FileSystems.getDefault().getPath(tmpDirName);
  val productFile = Files.createTempFile(tmpDirPath, "product", ".ta").toFile()
  productFile.getParentFile().mkdirs()
    // System.out.printf("Wrote text to temporary file %s%n", tmpFile.toString());

  //private val productFile = File(tmpDirName, "product.ta")
  
  private val monitorProcessName = "_crtmc_mon"

  override def processQueries(
      queries: java.util.Collection[
        ? <: de.learnlib.api.query.Query[String, Boolean]
      ]
  ): Unit = {
    MQUtil.answerQueries(this, queries);
  }

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

  override def answerQuery(
      prefix: Word[String],
      suffix: Word[String]
  ): Boolean = {
    val length = prefix.length + suffix.length
    val trace = prefix.asList().asScala ++ suffix.asList().asScala
    // Build product automaton
    val queueStrB = StringBuilder()
    queueStrB.append(
      "# Trace Monitor Process\nprocess:%s\nlocation:%s:q0{initial:}\n".format(
        monitorProcessName,
        monitorProcessName
      )
    )
    for (i <- 1 to length - 1) {
      queueStrB.append("location:%s:q%d\n".format(monitorProcessName, i))
    }
    queueStrB.append(
      "location:%s:q%d{labels:done}\n".format(monitorProcessName, length)
    )
    trace.zipWithIndex.foreach { (label, i) =>
      queueStrB.append(
        "edge:%s:q%d:q%d:%s\n".format(monitorProcessName, i, i + 1, label)
      )
    }
    val pw = PrintWriter(productFile)
    pw.write(ta.core)
    pw.write("\n\n")
    pw.write(queueStrB.mkString)
    pw.write("\n\n")
    pw.write(productTASyncs.mkString)
    pw.close()

    // Model check product automaton
    logger.debug("Running TChecker for a membership query")
    val output = "tck-reach -a covreach %s".format(productFile.toString).!!
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
) extends EquivalenceOracle.DFAEquivalenceOracle[Character] {
  private val logger = LoggerFactory.getLogger(classOf[TCheckerTA])

  private val productFile = File(tmpDirName, "product.ta")
  productFile.getParentFile().mkdirs()
  private val monitorProcessName = "_crtmc_mon"

  override def findCounterExample(
      hypothesis: DFA[_, Character],
      inputs: java.util.Collection[? <: Character]
  ): DefaultQuery[Character, java.lang.Boolean] = {
    // Create TA process corresponding to the complement of hypothesis
    // Dump the product with ta
    // Call model checker 
    null
  }

}
