package fr.irisa.comprtmc.configuration

import java.io.File
import java.nio.file._

case class ParseError(msg: String) extends Exception(msg)

object FSM {
  sealed trait FSMFormat
  case object SMV extends FSMFormat
  case object AIG extends FSMFormat
  case object Murphi extends FSMFormat
  case object TCheckerTA extends FSMFormat
  case object Verilog extends FSMFormat

  sealed trait ModelChecker
  case object NuSMV extends ModelChecker {
    override def toString: String = "NuSMV"
  }
  case object NuXmv extends ModelChecker {
    override def toString: String = "nuXmv"
  }
  case object TCheckerModelChecker extends ModelChecker {
    override def toString: String = "tck-reach"
  }

  case object PReach extends ModelChecker {
    override def toString: String = "preach"
  }
  case object Abssynthe extends ModelChecker {
    override def toString: String = "abssynthe"
  }

  sealed trait ModelCheckingAlgorithm
  case object BDDAlgorithm extends ModelCheckingAlgorithm
  case object IC3Algorithm extends ModelCheckingAlgorithm
  case object DefaultAlgorithm extends ModelCheckingAlgorithm
}

sealed trait Algorithm
case object TraceAbstractionRefinement extends Algorithm
case object HypothesisLearning extends Algorithm
case object Synthesis extends Algorithm

case class Configuration(
    fsmFile: File = new File("."),
    taFile: File = new File("."),
    fsmFormat: FSM.FSMFormat = FSM.SMV,
    fsmModelChecker: FSM.ModelChecker = FSM.NuXmv,
    fsmAlgorithm: FSM.ModelCheckingAlgorithm = FSM.DefaultAlgorithm,
    algorithm: Algorithm = HypothesisLearning,
    keepTmpFiles: Boolean = false,
    verbose: Boolean = false,
    verbose_MembershipQueries : Boolean = false,
    tmpDirName: String = ".crtmc/"
) {
  private var tmpDirFile: Option[Path] = None
  def tmpDirPath(): Path = {
    tmpDirFile match {
      case None =>
        val tmpDirPath = FileSystems.getDefault().getPath(tmpDirName);
        tmpDirPath.toFile().mkdirs()
        tmpDirFile = Some(tmpDirPath)
        tmpDirPath
      case Some(f) => f
    }
  }
}
var globalConfiguration = Configuration()
