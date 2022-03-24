package fr.irisa.comprtmc

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import collection.JavaConverters._
import scala.io.Source
import de.learnlib.api.oracle._
import de.learnlib.api.oracle.MembershipOracle
import net.automatalib.words._
import de.learnlib.util.MQUtil;
import java.io._

/** Read TChecker TA from given file, and return pair (events, core, syncs)
  * where events is the list of events, core is the list of lines of the file
  * except for the sync instructions, and syncs is a list of tuples encoding the
  * syncs
  */
class TAReader(inputFile: java.io.File) {
  val logger = LoggerFactory.getLogger(classOf[TAReader])

  val (events, syncs, core) = {
    val lines = Source.fromFile(inputFile).getLines().toList
    val regEvent = "\\s*event:(.*)\\s*".r
    val regSync = "\\s*sync:(.*)\\s*".r
    val events = lines.flatMap({
      case regEvent(event) => Some(event.strip())
      case _               => None
    })
    val syncs = lines.flatMap({
      case regSync(syncs) => // Some(syncs.split(":"))
        Some(syncs.split(":").toList.map(_.split("@").map(_.strip()).toList))
      case _ => None
    })
    val core = lines.filter(line => !line.startsWith("sync:")).mkString("\n")
    logger.debug("Events: " + events)
    logger.debug("Syncs: " + syncs)
    (events, syncs, core)
  }

}

class TAMembershipOracle extends MembershipOracle[String, Boolean] {
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

    val pre = prefix.asList().asScala
    val suf = suffix.asList().asScala
    val wholeTrace = (pre ++ suf).mkString("")
    System.out.println(wholeTrace)
    // Dump the intersection of TA for the word prefix.suffix, with input TA T, and call TChecker.
    true
  }
}
