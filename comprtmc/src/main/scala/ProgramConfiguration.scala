package fr.irisa.comprtmc

import java.io.File


case class ParseError(msg : String) extends Exception(msg)

object FSMFormat {
    sealed trait FSMFormat
    case object SMV extends FSMFormat
    case object Murphi extends FSMFormat
}

case class ProgramConfiguration(
    fsmFile : File = new File("."),
    taFile: File = new File("."),
    fsmFormat : FSMFormat.FSMFormat = FSMFormat.SMV,
    keepTmpFiles: Boolean = false,
    verbose : Boolean = false,
    )
    