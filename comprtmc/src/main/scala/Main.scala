package fr.irisa.comprtmc

import scopt.OParser
import java.io._
import ProgramConfiguration._

object Main {
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
        System.out.println(config.taFile.exists())
      case _ =>
      // arguments are bad, error message will have been displayed
    }
  }
}
