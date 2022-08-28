package fr.irisa.comprtmc

import scala.sys.process._
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import io.AnsiColor._
import scopt.OParser
import java.io._
import net.automatalib.words.Word
import net.automatalib.words.Alphabet;
import net.automatalib.words.impl.Alphabets;
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.util.automata.builders.AutomatonBuilders;

import fr.irisa.comprtmc.ta.Factory
import fr.irisa.comprtmc.algorithms
import fr.irisa.comprtmc.configuration._

import scala.collection.mutable._
import collection.JavaConverters._

object Main {
  val logger = LoggerFactory.getLogger("CompRTMC")

  def main(args: Array[String]): Unit = {
    val builder = OParser.builder[Configuration]
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
        opt[File]("fsm")
          .required()
          .valueName("<fsm>")
          .action((x, c) => 
              if (x.toString.contains(".smv")){
                c.copy(fsmFile = x, fsmFormat = FSM.SMV)
              } else if (x.toString.contains(".aag")){
                c.copy(fsmFile = x, fsmFormat = FSM.AIG)
              } else if (x.toString.contains(".ta")){
                c.copy(fsmFile = x, fsmFormat = FSM.TCheckerTA, fsmModelChecker=FSM.TCheckerModelChecker)
              } else if (x.toString.contains(".v")){
                c.copy(fsmFile = x, fsmFormat = FSM.Verilog)
              } else {
                throw Exception("Unknown fsm format")
              }
            )
          .text("fsm is the finite state model in smv or ta format"),
        opt[String]("alg")
          .action({(alg, c) =>
                alg match{
                case "verify" => c.copy(algorithm = HypothesisLearning)
                case "tar" => c.copy(algorithm = TraceAbstractionRefinement)
                case "synthesize" => c.copy(algorithm = Synthesis)
                case _ => throw Exception("Unknown algorithm")
              }
            }
            ).valueName("(verify|tar|synthesize)"),
        opt[Boolean]("verbose")
          .action((_, c) => c.copy(verbose = true))
          .valueName("(true|false)"),
        opt[Boolean]("keepTmpFiles")
          .action((_, c) => c.copy(keepTmpFiles = true))
          .valueName("(true|false)"),
        opt[Boolean]("visualizeDFA")
          .action((_, c) => c.copy(visualizeDFA = true))
          .valueName("(true|false)")
          .text("Visualize the DFA that was learned"),
        opt[String]("fsmModelChecker")
          .action((mc, c) => {
            mc match{
              case "nuXmv" => c.copy(fsmModelChecker = FSM.NuXmv)
              case "NuSMV" => c.copy(fsmModelChecker = FSM.NuSMV, fsmAlgorithm = FSM.BDDAlgorithm)
              case "tck-reach" => c.copy(fsmModelChecker = FSM.TCheckerModelChecker)
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

    OParser.parse(parser1, args, Configuration()) match {
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
        logger.info("FSM File: " + config.fsmFile)
        logger.info("TA File: " + config.taFile)
        if (config.fsmAlgorithm == FSM.IC3Algorithm && config.fsmModelChecker != FSM.NuXmv){
          throw Exception("IC3 is only available with nuXmv")
        }
        if (config.fsmFormat == FSM.TCheckerTA && config.fsmModelChecker != FSM.TCheckerModelChecker){
          throw Exception("TChecker model checker must be used with FSM files with extension .ta")
        }
        if ( config.fsmFormat != FSM.Verilog && config.algorithm == Synthesis){
          throw Exception("Synthesis is only supported with FSMs given in Verilog with extension .v")
        }
        if ( config.fsmFormat == FSM.Verilog && config.algorithm != Synthesis){
          throw Exception("Verilog FSM files are currently only supported with the synthesis algorithm")
        }
        //"./remove_temp.sh".!
        val tmpFolder = File(".crtmc")
        if(!tmpFolder.exists()){
          tmpFolder.mkdir()
        }
        for(file <- tmpFolder.listFiles()){
          if (!file.isDirectory()) 
            file.delete()
        }
        config.algorithm match {
          case HypothesisLearning =>
            val fsmIntersectOracle : fsm.FSMIntersectionOracle = config.fsmFormat match{
              case FSM.SMV => fsm.Factory.getSMVOracle(config.fsmFile)
              case FSM.TCheckerTA => fsm.Factory.getTCheckerOracle(config.fsmFile)
              case _ => throw Exception("FSM Format not supported")
            }
            logger.info("Alphabet: " + fsmIntersectOracle.alphabet)
            val (taMemOracle, taIncOracle) = ta.Factory.getTCheckerOracles(config.taFile, fsmIntersectOracle.alphabet) 
            val alg = algorithms.CompSafetyAlgorithm(fsmIntersectOracle, taMemOracle, taIncOracle)
            alg.run()
          case TraceAbstractionRefinement =>
            val fsmIntersectOracle : fsm.FSMIntersectionOracle = config.fsmFormat match{
              case FSM.SMV => fsm.Factory.getSMVOracle(config.fsmFile)
              case FSM.TCheckerTA => fsm.Factory.getTCheckerOracle(config.fsmFile)
              case _ => throw Exception("FSM Format not supported")
            }
            logger.info("Alphabet: " + fsmIntersectOracle.alphabet)
            val taInterpolantOracle = ta.Factory.getTCheckerInterpolationOracle(config.taFile, fsmIntersectOracle.alphabet) 
            val alg = algorithms.TARAlgorithm(fsmIntersectOracle, taInterpolantOracle)
            alg.run()
          case Synthesis =>
            val verilog = synthesis.Verilog(config.fsmFile)
            val (taMemOracle, taIncOracle) = ta.Factory.getTCheckerOracles(config.taFile, verilog.alphabet) 
            val synthesisOracle = synthesis.AbssyntheOracle(verilog)
            val alg = algorithms.CompSynthesisAlgorithm(verilog,synthesisOracle,taMemOracle, taIncOracle)
            alg.run()
        }
      case _ => 
    }
  }
}
