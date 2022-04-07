package fr.irisa.comprtmc
import io.AnsiColor._
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import collection.JavaConverters._
import collection.convert.ImplicitConversions._
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.ListBuffer
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
import net.automatalib.visualization.Visualization;

case class BadTimedAutomaton(msg: String) extends Exception(msg)
case class FailedTAModelChecking(msg: String) extends Exception(msg)

/** Read TChecker TA from given file, and store the tuple (events,
  * eventsOfProcesses, core, syncs) where 
  * - events is the list of all events,
  * - eventsOfProcesses maps process names to events that they include, 
  * - core is the list of lines of the input file except for the sync instructions,
  * - syncs contains lists of tuples encoding the syncs
  * - externEvents is a subset of events and is the set of events tagged _extern_
  */
class TCheckerTA(inputFile: java.io.File) {
  class TCheckerTAStructure(
      val events: List[String],
      val externEvents : List[String],
      val eventsOfProcesses: HashMap[String, Set[String]],
      val syncs: List[List[(String, String)]],
      val core: String,
  )
  private val logger = LoggerFactory.getLogger(classOf[TCheckerTA])

  private val taComponents = {
    val lines = Source.fromFile(inputFile).getLines().toList
    val regEvent = "\\s*event:([^ ]*).*".r
    val regExternEvent = "\\s*event:([^ ]*).*_extern_.*".r
    val regSync = "\\s*sync:(.*)\\s*".r
    val regProcess = "\\s*process:(.*)\\s*".r
    val regEdge = "\\s*edge:[^:]*:[^:]*:[^:]*:([^{]*).*".r
    // System.out.println("File: " + inputFile.toString)
    val events = lines.flatMap({
      case regEvent(event) => Some(event.strip())
      case _               => None
    })
    val externEvents = lines.flatMap({
      case regExternEvent(event) => Some(event.strip())
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
    TCheckerTAStructure(events, externEvents, eventsOfProcesses, syncs, core)
  }

  def events: List[String] = this.taComponents.events
  def syncs: List[List[(String, String)]] = taComponents.syncs
  def eventsOfProcesses: HashMap[String, Set[String]] =
    taComponents.eventsOfProcesses
  def core: String = taComponents.core
  def externEvents = taComponents.externEvents

  def getTraceFromCexDescription(cexDescription : List[String]) : List[String] = {
      val alphabetSet = events.toSet
      val word = ListBuffer[String]()
      val regEdge = ".*->.*vedge=\"<(.*)>\".*".r
      cexDescription.foreach({
        case regEdge(syncList) => 
          val singleSync = syncList.split(",").map(_.split("@")(1)).toSet.intersect(alphabetSet)
          if (singleSync.size == 1){
            val a = singleSync.toArray
            word.append(a(0))
          } else if (singleSync.size > 1){
            throw FailedTAModelChecking("The counterexample trace has a transition with syncs containing more than one letter of the alphabet:\n" + syncList)
          } else {
            // Ignore internal transition
          }
        case _ => ()
      })
      word.toList
  }
}

/**
 * Class that providing functions to generate products of the given timed automaton `ta` with traces or DFAs
 * on the common `alphabet`. The monitors are obtained by adding a monitor process in the given TA description,
 * and adding synchronizations on all events in the alphabet.
 * If acceptingLabel is None, then it is assumed that all locations of the TA are accepting. Otherwise, only those
 * locations with the said label are accepting.
 */
class TCheckerMonitorMaker (
    ta: TCheckerTA,
    alphabet: Alphabet[String],
    acceptingLabel : Option[String],
    monitorProcessName: String = "_crtmc_mon"
)  {
  val tmpDirPath = FileSystems.getDefault().getPath(ProgramConfiguration.globalConfiguration.tmpDirName);
  tmpDirPath.toFile().mkdirs()

  private val acceptingLocations = {
    val accLocs = ArrayBuffer[(String,String)]()
    val regLoc = "\\s*location:(.*):(.*)\\{(.*)\\}\\s*".r
    ta.core.split("\n").foreach( _ match
      case regLoc(proc,loc,attr) =>
        if (attr.contains("labels:error")){
          // System.out.println((proc,loc))
          accLocs.append((proc,loc))
        }
      case _ => ()
    )
    accLocs
  }

  /** Textual declaration of sync labels where the monitor process participates
    * in all synchronized edges with a label in alphabet + 
    * the monitor synchronizes on the letters of the alphabet with all processes
    * which use that action without declaring any sync.
    */
  val productTASyncs: String = {
    val strB = StringBuilder()
    val alphabetSet = alphabet.asScala.toSet
      // The monitor shall join all syncs on letters of the alphabet
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
    // For each process, store the set of letters that appear in a sync instruction
    val syncEventsOfProcesses = HashMap[String, Set[String]]()
    ta.syncs.foreach { syncLine =>
      val newSyncLine =
        syncLine.filter(alphabetSet.contains(_)).foreach(
          (proc, event) => 
            val currentSet = syncEventsOfProcesses.getOrElse(proc,Set[String]())
            syncEventsOfProcesses += proc -> currentSet
        )
    }
    strB.append("\n")
    // The monitor shall join all previously asynchronous transitions on the letters of the alphabet
    ta.eventsOfProcesses.foreach(
      (proc, syncEvents) => 
        syncEvents.intersect(alphabet.toSet).diff(syncEventsOfProcesses.getOrElse(proc, Set[String]())).foreach(
          e => strB.append("sync:%s@%s:%s@%s\n".format(monitorProcessName,e,proc,e))
        )
    )
    strB.result()
  }
  def monitorAcceptLabel = "_crtmc_monitor_accept"
  def productAcceptLabel = "_crtmc_err"

  /** Returns textual description of TA made of the product of given TA, and a
    * monitor that reads a given word. The acceptLabel is reachable if the
    * intersection is non-empty.
    * @pre This assumes that all states of the TA are accepting
    */
  def makeWordIntersecter(word: Buffer[String]): String = {
    // Build product automaton
    val strB = StringBuilder()
    strB.append(ta.core)
    strB.append("\nprocess:%s\n".format(monitorProcessName))
    for (i <- 0 to word.length) {
      strB.append("location:%s:q%d".format(monitorProcessName, i))
      val attributes = ArrayBuffer[String]()
      if (i == 0){
        attributes.append("initial:")
      }
      if (i == word.length){
        attributes.append("labels:%s".format(monitorAcceptLabel))
      }
      strB.append("{%s}\n".format(attributes.mkString(":")))
    }
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
    * the given DFA. The acceptLabel is reachable if the intersection is non-empty.
    * When complement is set to true, we compute TA /\ comp(DFA), so the emptiness
    * of this intersection is equivalent to inclusion of TA in DFA.
    * 
    * @arg complement whether given DFA should be complemented.
    */
  def makeDFAIntersecter(hypothesis: DFA[_, String], complement : Boolean): String = {
    val strStates = StringBuilder()
    val strTransitions = StringBuilder()
    // val alphabetSet = alphabet.asScala.toSet
    val initStates = hypothesis.getInitialStates().asScala
    strStates.append("process:" + monitorProcessName + "\n")
    // Add a dummy accepting node in case there is no accepting state at the end (this prevents tchecker from complaining)
    strStates.append("location:%s:dummy{labels:%s}\n".format(monitorProcessName,monitorAcceptLabel))
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
        if (!complement && hypothesis.isAccepting(state) || complement && !hypothesis.isAccepting(state)) {
          attributes.append("labels:%s".format(monitorAcceptLabel))
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
      taB.append("\n")
      taB.append(strStates.append(strTransitions.toString).toString)
      taB.append("\n")
      taB.append(productTASyncs)
      taB.append("\n")
      acceptingLabel match {
        case None => taB.toString
        case Some(errlabel) =>
          taB.append("\nevent:%s\n".format(productAcceptLabel))
          // For each process with an accepting location, add a new error state
          acceptingLocations.map(_._1).toSet.foreach{
            proc => taB.append("location:%s:%s{labels:%s}\n".format(proc, productAcceptLabel, productAcceptLabel))
            taB.append("sync:%s@%s:%s@%s\n".format(proc, productAcceptLabel, monitorProcessName, productAcceptLabel))
          }
          // Add edges from accepting locations to this new error states
          acceptingLocations.foreach{
            (proc,loc) =>
              taB.append("edge:%s:%s:%s:%s\n".format(proc,loc, productAcceptLabel, productAcceptLabel))
          }
          hypothesis.getStates().asScala.foreach(state =>
            if (!complement && hypothesis.isAccepting(state) || complement && !hypothesis.isAccepting(state)) {
              taB.append("edge:%s:q%s:q%s:%s\n".format(monitorProcessName, state.toString, state.toString, productAcceptLabel))
            }
          )
          taB.toString
      }
    }

  /** Given a string description of a product automaton, check emptiness.
   * @param generateWitness returns the actual counterexample if set to true, and an empty string otherwise.
   * @return None if the automaton is empty, and the output of the automaton
   * describing the execution otherwise.
   */
  def checkEmpty(monitorDescription : String, acceptingLabel : String, generateWitness : Boolean) : Option[String] = {
    val productFile =
      Files.createTempFile(tmpDirPath, "product", ".ta").toFile()
    val pw = PrintWriter(productFile)
    pw.write(monitorDescription)
    pw.close()

    // Model check product automaton
    var certFile : java.io.File = null
    
    val cmd = 
        if (generateWitness){
          certFile = Files.createTempFile(tmpDirPath, "cert", ".ta").toFile()
          "tck-reach -a reach %s -l %s -C %s"
            .format(productFile.toString, acceptingLabel,certFile.toString)
        } else {
          "tck-reach -a covreach %s -l %s"
            .format(productFile.toString, acceptingLabel)
        }
    System.out.println(cmd)
    // var beginTime = System.nanoTime()
    val output = cmd.!!
    // this._elapsed = this._elapsed + (System.nanoTime() - beginTime)
    // System.out.println(output)

    if (!ProgramConfiguration.globalConfiguration.keepTmpFiles){
      productFile.delete()
    }    
    if (output.contains("REACHABLE false")) then {
      if (generateWitness && !ProgramConfiguration.globalConfiguration.keepTmpFiles){
        certFile.delete()
      }
      None
    } else if (output.contains("REACHABLE true")) then {
      if (generateWitness){
        // val parts = output.split("Counterexample trace:").map(_.strip()).filter(_.length>0)      
        // val timedCex = parts(1)
        val timedCex = Source.fromFile(certFile).getLines.mkString("\n")
        if (!ProgramConfiguration.globalConfiguration.keepTmpFiles){
          certFile.delete()
        }
        // System.out.println(timedCex)
        Some(timedCex)
      } else {
        Some("")
      }
    } else {
      if (generateWitness && !ProgramConfiguration.globalConfiguration.keepTmpFiles){
        certFile.delete()
      }      
      throw FailedTAModelChecking(output)
    }
  }

}
abstract class TAMembershipOracle extends MembershipOracle[String, java.lang.Boolean]{
  /**
   * Returns witness timed trace if the given word is in the untimed language
   */
  def answerQuery(
      prefix: Word[String],
      suffix: Word[String],
      generateWitness : Boolean
  ): Option[String]

  def systemElapsedTime : Long
  def nbQueries : Long
}

class TCheckerMembershipOracle(
    ta : TCheckerTA,
    alphabet: Alphabet[String]
) extends TAMembershipOracle {
  private val logger = LoggerFactory.getLogger(classOf[TCheckerTA])
  private val taMonitorMaker = TCheckerMonitorMaker(ta, alphabet, None)

  private var _elapsed : Long = 0
  private var _nbQueries : Long = 0
  override def systemElapsedTime : Long ={
    _elapsed
  }
  override def nbQueries : Long ={
    _nbQueries
  }

  override def processQueries(
      queries: java.util.Collection[
        ? <: de.learnlib.api.query.Query[String, java.lang.Boolean]
      ]
  ): Unit = {
    MQUtil.answerQueries(this, queries);
  }

  override def answerQuery(
      prefix: Word[String],
      suffix: Word[String]
  ): java.lang.Boolean = {
    val pr = prefix.asList.asScala
    val su = suffix.asList.asScala
    // System.out.println(pr.toList.mkString(" ") + " ||| " + su.mkString(" "))
    answerQuery(prefix,suffix, false) match {
      case None => false
      case _ => true
    }
  }

  /**
   * Returns witness timed trace if the given word is in the untimed language
   */
  override def answerQuery(
      prefix: Word[String],
      suffix: Word[String],
      generateWitness : Boolean
  ): Option[String] = {
    val trace = prefix.asList().asScala ++ suffix.asList().asScala
    this._nbQueries += 1
    val monitorDescription = taMonitorMaker.makeWordIntersecter(trace)
    val verdict = taMonitorMaker.checkEmpty(monitorDescription, taMonitorMaker.monitorAcceptLabel, generateWitness)
    System.out.print(BLUE + "Membership query: " + trace + RESET)
    verdict match {
      case None =>
        System.out.println(RED + " (false)" + RESET)
        negQueries = negQueries + trace.mkString(" ")
      case cex =>
        posQueries = posQueries + trace.mkString(" ")
        System.out.println(GREEN + " (true)" + RESET)
        cex
    }
    verdict
  }
}
/*
 * Oracle to check whether hypothesis is included in TA,
 * assuming that all locations of the TA are accepting.
 */
class TCheckerInclusionOracle(
    ta: TCheckerTA,
    alphabet: Alphabet[String]
) extends EquivalenceOracle.DFAEquivalenceOracle[String] {
  private val logger = LoggerFactory.getLogger(classOf[TCheckerInclusionOracle])
  private val taMonitorMaker = TCheckerMonitorMaker(ta, alphabet, None)
  private val alphabetSet = alphabet.asScala.toSet

  val tmpDirPath = FileSystems.getDefault().getPath(ProgramConfiguration.globalConfiguration.tmpDirName);
  tmpDirPath.toFile().mkdirs()

  override def findCounterExample(
      hypothesis: DFA[_, String],
      inputs: java.util.Collection[? <: String]
  ): DefaultQuery[String, java.lang.Boolean] = {
    val productFile =
      Files.createTempFile(tmpDirPath, "productEq", ".ta").toFile()
    val pw = PrintWriter(productFile)
    pw.write(taMonitorMaker.makeDFAIntersecter(hypothesis, true))
    pw.close()

    var certFile = Files.createTempFile(tmpDirPath, "cert", ".ta").toFile()

    // Model check product automaton
    System.out.println(YELLOW + "Inclusion query: " + RESET)
    val cmd = "tck-reach -a reach %s -l %s -C %s" 
      .format(productFile.toString, taMonitorMaker.monitorAcceptLabel, certFile)
    System.out.println(cmd)
    val output = cmd.!!
    // System.out.println(output)
    if (!ProgramConfiguration.globalConfiguration.keepTmpFiles){
      productFile.delete()
    }    
    if (output.contains("REACHABLE false")) then {
      System.out.println(GREEN + "TA Inclusion holds: hypothesis with " + hypothesis.size + " states found" + RESET)
      null
    } else if (output.contains("REACHABLE true")) then {
      // val parts = output.split("Counterexample trace:").map(_.strip()).filter(_.length>0)
      // val cexLines = parts(1).split("\n").toList
      val cexLines = Source.fromFile(certFile).getLines.toList
      if (!ProgramConfiguration.globalConfiguration.keepTmpFiles){
        certFile.delete()
      }
      val word = ta.getTraceFromCexDescription(cexLines).filter(alphabet.contains(_))
      System.out.println(ta.getTraceFromCexDescription(cexLines))
      val query =  DefaultQuery[String, java.lang.Boolean](Word.fromArray[String](word.toArray,0,word.length), java.lang.Boolean.TRUE)
      System.out.println(MAGENTA + "CEX requested alphabet: " + inputs + RESET)
      System.out.println(RED + "Counterexample to inclusion (accepted by TA but not by hypothesis): " + query + RESET)
      posQueries = posQueries + word.mkString(" ")

      // Visualization.visualize(hypothesis, alphabet);
      return query
    } else {
      throw FailedTAModelChecking(output)
    }
  }

}


class UppaalTA

object TAOracles {

  object Factory{
    def getTCheckerOracles(taFile: File,
      alphabet: List[String]) : (TAMembershipOracle, EquivalenceOracle.DFAEquivalenceOracle[String]) = {        
        val alph = Alphabets.fromList(alphabet)
        val ta = TCheckerTA(taFile)
        (TCheckerMembershipOracle(ta, alph), TCheckerInclusionOracle(ta, alph))
      }

    def getUppaalOracles(ta: UppaalTA,
      alphabet: List[String]) : (MembershipOracle[String, java.lang.Boolean], EquivalenceOracle.DFAEquivalenceOracle[String]) = {
        throw Exception("Not yet implemented")
      }

  }
}