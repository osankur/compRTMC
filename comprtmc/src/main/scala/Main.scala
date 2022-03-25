package fr.irisa.comprtmc

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import io.AnsiColor._
import scopt.OParser
import java.io._
import ProgramConfiguration._
import net.automatalib.words.Word
import net.automatalib.words.Alphabet;
import net.automatalib.words.impl.Alphabets;
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.util.automata.builders.AutomatonBuilders;

import scala.collection.mutable._
import collection.JavaConverters._
object Main {
  val logger = LoggerFactory.getLogger("CompRTMC")

  def main(args: Array[String]): Unit = {
    val builder = OParser.builder[ProgramConfiguration]
    val parser1 = {
      import builder._
      OParser.sequence(
        programName("comprtmc"),
        head("comprtmc", "0.0"),
        opt[File]("ta")
          .required()
          .valueName("<ta>")
          .action((x, c) => c.copy(taFile = x))
          .text("ta is the timed automaton to be processed"),
        opt[Boolean]("verbose")
          .action((_, c) => c.copy(verbose = true)),
        opt[Boolean]("fullZG")
          .action((_, c) => c.copy(fullZG = true))
      )
    }
    // OParser.parse returns Option[Config]
    OParser.parse(parser1, args, ProgramConfiguration()) match {
      case Some(config) =>
        if (!config.taFile.exists()){
          logger.error(("%s File " + config.taFile.getAbsolutePath() + " does not exist%s").format(RED,RESET))
          return
        }        
        //val alphabetList = List("get1","rel1","get2", "rel2").asJava
        val alphabetList = List("get1","get2").asJava
        val alphabet : Alphabet[String] = Alphabets.fromList(alphabetList)
        val target : CompactDFA[String] =
            AutomatonBuilders.newDFA(alphabet)
                    .withInitial("q0")
                    .from("q0")
                      .on("get1").to("q0")
                      .on("get2").to("q1")
                      .withAccepting("q0")
                    /*
                    .from("q0")
                        .on("get1").to("q0")
                    .from("q0")
                        .on("rel1").to("q1")
                    .from("q1")
                        .on("get2").to("q1")
                    .from("q1")
                        .on("rel2").to("q2")
                    .withAccepting("q0")
                    .withAccepting("q1")
                    */
                    .create();
        val taReader = TCheckerTA(config.taFile)
        try {
          val mOracle = TCheckerMembershipOracle(alphabet, taReader)
          System.out.println("Membership question: " + mOracle.answerQuery(Word.fromList(List("get1","rel1").asJava)))
          val inclOracle = TCheckerInclusionOracle(alphabet, taReader)
          inclOracle.findCounterExample(target,null)
        } catch {
          case BadTimedAutomaton(msg) => logger.error(msg)
          case FailedTAModelChecking(msg) => 
            logger.error("TA Model checking failed. TChecker's output:")
            logger.error(msg)
        }
      case _ =>
      // arguments are bad, error message will have been displayed
    }
  }
}
