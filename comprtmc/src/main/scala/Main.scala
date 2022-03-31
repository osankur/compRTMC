package fr.irisa.comprtmc

import scala.sys.process._
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
        opt[File]("smv")
          .required()
          .valueName("<smv>")
          .action((x, c) => 
            val format = 
              if (x.toString.contains(".smv")){
                FSM.SMV
              }else {
                throw Exception("The FSM format must be .smv")
              }                
            c.copy(fsmFile = x, fsmFormat = format)
            )
          .text("smv is the finite state model in smv format"),
        opt[Boolean]("verbose")
          .action((_, c) => c.copy(verbose = true))
          .valueName("(true|false)"),
        opt[Boolean]("keepTmpFiles")
          .action((_, c) => c.copy(keepTmpFiles = true))
          .valueName("(true|false)"),
        opt[String]("fsmModelChecker")
          .action((mc, c) => {
            mc match{
              case "nuXmv" => c.copy(fsmModelChecker = FSM.NuXmv)
              case "NuSMV" => c.copy(fsmModelChecker = FSM.NuSMV, fsmAlgorithm = FSM.BDDAlgorithm)
              case _ => throw Exception("Unknown model checker")
            }
          })
          .text("FSM Model checker: NuSMV or nuXmv"),
        opt[String]("fsmAlgorithm")
          .action((mc, c) => {
            mc match{
              case "BDD" => c.copy(fsmAlgorithm = FSM.BDDAlgorithm)
              case "IC3" => c.copy(fsmAlgorithm = FSM.IC3Algorithm)
              case _ => throw Exception("Unknown algorithm")
            }
          })
          .text("FSM Model checking algorithm: BDD or IC3")
      )
    }

    OParser.parse(parser1, args, ProgramConfiguration()) match {
      case Some(config) =>
        globalConfiguration = config
        if (!config.taFile.exists()){
          logger.error(("%s File " + config.taFile.getAbsolutePath() + " does not exist%s").format(RED,RESET))
          return
        }
        if (!config.fsmFile.exists()){
          logger.error(("%s File " + config.fsmFile.getAbsolutePath() + " does not exist%s").format(RED,RESET))
          return
        }
        logger.info("SMV File: " + config.fsmFile)
        logger.info("TA File: " + config.taFile)
        if (config.fsmAlgorithm == FSM.IC3Algorithm && config.fsmModelChecker != FSM.NuXmv){
          throw Exception("IC3 is only available with nuXmv")
        }
        "./remove_temp.sh".!
        val fsmIntersectOracle = FSMOracles.Factory.getSMVOracle(config.fsmFile)
        logger.info("Alphabet: " + fsmIntersectOracle.alphabet)
        val (taMemOracle, taIncOracle) = TAOracles.Factory.getTCheckerOracles(config.taFile, fsmIntersectOracle.alphabet) 
        val alg = CompSafetyAlgorithm(fsmIntersectOracle, taMemOracle, taIncOracle)
        alg.run()
      case _ => 
    }
  }
}
