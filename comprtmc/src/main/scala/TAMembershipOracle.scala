package fr.irisa.comprtmc

import collection.JavaConverters._

import de.learnlib.api.oracle._
import de.learnlib.api.oracle.MembershipOracle
import net.automatalib.words._
import de.learnlib.util.MQUtil;

class TAMembershipOracle extends MembershipOracle[String,Boolean] {
    override def processQueries(queries : java.util.Collection[? <: de.learnlib.api.query.Query[String, Boolean]]): Unit =
        {
            MQUtil.answerQueries(this, queries);
        }
    override def answerQuery(prefix : Word[String], suffix : Word[String]) : Boolean = {
        
        val pre = prefix.asList().asScala
        val suf = suffix.asList().asScala
        val wholeTrace = (pre++suf).mkString("")
        System.out.println(wholeTrace)
        // Dump the intersection of TA for the word prefix.suffix, with input TA T, and call TChecker.
        true
    }
}
