package fr.irisa.comprtmc

import java.io.File
case class ProgramConfiguration(
    taFile: File = new File("."),
    fullZG: Boolean = false,
    verbose : Boolean = false,
    )
    