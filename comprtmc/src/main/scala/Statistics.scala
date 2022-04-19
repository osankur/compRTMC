package fr.irisa.comprtmc.statistics

var posQueries = Set[String]()
var negQueries = Set[String]()
var lastTrace : List[String] = null

object Counters {
    var counter = Map[String,Int]()

    def incrementCounter(counterName : String) : Unit = {
        counter += counterName -> (counter.getOrElse(counterName, 0) + 1)
    }
    def apply(counterName : String ) : Int = {
        counter.getOrElse(counterName, 0)
    }
    override def toString : String = {
        counter.toString
    }
}