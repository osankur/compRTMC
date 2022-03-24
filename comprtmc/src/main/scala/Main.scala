package fr.irisa.comprtmc

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import io.AnsiColor._
import scopt.OParser
import java.io._
import ProgramConfiguration._

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
        TAReader(config.taFile)
      case _ =>
      // arguments are bad, error message will have been displayed
    }
  }
}
