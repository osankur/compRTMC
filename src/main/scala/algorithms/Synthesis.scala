package fr.irisa.comprtmc.algorithms

import io.AnsiColor._
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import collection.JavaConverters._
import collection.convert.ImplicitConversions._
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.Buffer
import de.learnlib.api.oracle.EquivalenceOracle
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.api.oracle._
import de.learnlib.api.oracle.MembershipOracle
import de.learnlib.oracle.equivalence.DFAWpMethodEQOracle
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.util.automata.builders.AutomatonBuilders;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.words.impl.Alphabets;
import net.automatalib.words._
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.util.automata.builders.AutomatonBuilders;
import net.automatalib.visualization.Visualization;
import de.learnlib.util.Experiment.DFAExperiment;
import de.learnlib.util.statistics.SimpleProfiler;
import de.learnlib.algorithms.lstar.dfa.ClassicLStarDFA;
import de.learnlib.algorithms.lstar.dfa.ClassicLStarDFABuilder;
import net.automatalib.serialization.aut._
import net.automatalib.visualization.Visualization;
import java.io.File
import de.learnlib.algorithms.kv.dfa.KearnsVaziraniDFA
import de.learnlib.algorithms.kv.dfa.KearnsVaziraniDFABuilder
import de.learnlib.algorithms.ttt.dfa.TTTLearnerDFA
import de.learnlib.algorithms.ttt.dfa.TTTLearnerDFABuilder
import de.learnlib.algorithms.discriminationtree.dfa.DTLearnerDFA
import de.learnlib.algorithms.discriminationtree.dfa.DTLearnerDFABuilder
import de.learnlib.oracle.equivalence.DFARandomWpMethodEQOracle
import net.automatalib.util.automata.minimizer.paigetarjan.PaigeTarjanMinimization
import net.automatalib.serialization.aut.AUTSerializationProvider
import net.automatalib.automata.fsa.impl.compact.CompactNFA;

import net.automatalib.util.automata.fsa.NFAs
import net.automatalib.util.automata.fsa.DFAs

import fr.irisa.comprtmc.fsm
import fr.irisa.comprtmc.ta
import fr.irisa.comprtmc.configuration
import fr.irisa.comprtmc.statistics
import fr.irisa.comprtmc.synthesis
import fr.irisa.comprtmc.configuration.Synthesis
import scala.util.hashing.Hashing.Default
import de.learnlib.api.oracle.MembershipOracle.DFAMembershipOracle

class CompSynthesisAlgorithm(
    verilog: synthesis.Verilog,
    synthesisOracle: synthesis.SynthesisOracle,
    taMembershipOracle: ta.TAMembershipOracle,
    taInclusionOracle: EquivalenceOracle.DFAEquivalenceOracle[String],
) {
  private val logger = LoggerFactory.getLogger("compRTMC")
  val tmpDirPath = configuration.globalConfiguration.tmpDirPath()

  private sealed abstract class Phase
  private case object UnderApprPhase extends Phase
  private case object OverApprPhase extends Phase

  private object SynthesisLearningLock {
    var phase: Phase = OverApprPhase
    var strategy = File("")
    // None : not yet known, true : controllable, false : uncontrollable
    var verdict : Option[Boolean] = None
    // Query returned by the underApprPhase
    var query = DefaultQuery[String, java.lang.Boolean](
      Word.fromList(List("")),
      java.lang.Boolean.FALSE
    )

    // The current overapproximation aka overH
    var overApproximation : Option[DFA[?, String]] = None
    // The current underapproximation aka underH
    var underApproximation : Option[DFA[?, String]] = None

    def switchPhase(): Unit = {
      phase = phase match {
        case OverApprPhase  => UnderApprPhase
        case UnderApprPhase => OverApprPhase
      }
      this.synchronized {
        this.notifyAll()
      }
    }
    def waitForPhase(targetPhase: Phase): Unit = {
      this.synchronized {
        while (phase != targetPhase && verdict == None) {
          try {
            this.wait();
          } catch {
            case e: InterruptedException =>
              Thread.currentThread().interrupt();
              logger.debug("Thread Interrupted");
          }
        }
        if (verdict == None) 
          logger.debug("<--" + BOLD + BLUE + " Switched to phase " + GREEN + targetPhase.toString  + RESET + " -->")
      }
    }
    def setVerdict(v : Boolean) : Unit = {
        this.synchronized{
            verdict = Some(v)
            notifyAll()
        }
    }
    def setOverApproximation(hypothesis: DFA[_, String]) : Unit = {
      this.overApproximation = Some(hypothesis)
    }
    def setUnderApproximation(hypothesis: DFA[_, String]) : Unit = {
      this.underApproximation = Some(hypothesis)
    }
  }

  def strategy = SynthesisLearningLock.strategy

  /** Oracle for compositional synthesis. Given hypothesis DFA which includes
    * TA, the oracle checks if FSM x hypothesis is controllable using a reactive
    * synthesis solver. If yes, then the system is controllable. If not, the
    * closed system controlled by a counterstrategy is synthesized, and the
    * underapproximation learner is invoked, which can either validate the
    * counterstrategy or return a query for the upper bound oracle.
    */
  class UpperBoundOracle // (fsmIntersectionOracle : fsm.FSMIntersectionOracle, taInclusionOracle : EquivalenceOracle.DFAEquivalenceOracle[String])
      extends EquivalenceOracle.DFAEquivalenceOracle[String] {

    override def findCounterExample(
        hypothesis: DFA[_, String],
        inputs: java.util.Collection[? <: String]
    ): DefaultQuery[String, java.lang.Boolean] = {
      statistics.Counters.incrementCounter("conjecture_overH")
      if (configuration.globalConfiguration.verbose)
        logger.debug(statistics.Counters.toString)
      taInclusionOracle.findCounterExample(hypothesis, inputs) match {
        case null => // T <= barH
          if (configuration.globalConfiguration.verbose)
            logger.debug("New over approximation found")
          SynthesisLearningLock.setOverApproximation(hypothesis)
          if (configuration.globalConfiguration.visualizeDFA) then
            Visualization.visualize(hypothesis,inputs)
          synthesisOracle.synthesizeIntersection(hypothesis) match {
            case synthesis.Controllable(strat) =>
              // Synthesis succeeded
              // Visualization.visualize(hypothesis, inputs)
              SynthesisLearningLock.strategy = strat
              SynthesisLearningLock.setVerdict(true)              
              logger.info(GREEN + BOLD + "Controllable" + RESET)
              logger.info("Total number of conjectures made: " + (statistics.Counters.get("conjecture_overH") + statistics.Counters.get("conjecture_underH")))
              SynthesisLearningLock.overApproximation match 
              { case Some(aut) =>
                logger.info("Size of conjecture overH: " + aut.size())
                case _ => ()
              }
              SynthesisLearningLock.underApproximation match 
              { case Some(aut) =>
                logger.info("Size of conjecture underH: " + aut.size())
                case _ => ()
              }
              logger.info(statistics.Counters.counter.toString)

              if (configuration.globalConfiguration.visualizeDFA) then
                Visualization.visualize(hypothesis,inputs)
              null
            case synthesis.Uncontrollable(strat) =>
              if (configuration.globalConfiguration.verbose)
                logger.debug(
                  RED + "OverAppr Query: Uncontrollable" + RESET + "\n"
                )
                logger.debug(
                  RED + "Checking the feasibility of the counterstrategy w.r.t. TA" + RESET + "\n"
                )

              SynthesisLearningLock.strategy = strat
              SynthesisLearningLock.switchPhase()
              SynthesisLearningLock.waitForPhase(OverApprPhase)
              if (SynthesisLearningLock.verdict != None) {
                null
              } else {
                if (configuration.globalConfiguration.verbose)
                  logger.debug("Adding Query: " + SynthesisLearningLock.query)
                SynthesisLearningLock.query
              }
            case _ => throw Exception("Unknown synthesis result")
          }
        case query => query
      }
    }
  }
  class DFARandomWpMethodInclusionOracle(mqOracle : DFAMembershipOracle[String], minimalSize : Int, rndLength : Int) extends DFARandomWpMethodEQOracle[String](mqOracle, minimalSize, rndLength) {

  }

  /** The Wp method as inclusion oracle, to check whether the hypothesis is included in the system under study.
   */
  class DFAWpMethodInclusionOracle(mqOracle : DFAMembershipOracle[String], lookahead : Int) extends DFAWpMethodEQOracle[String](mqOracle,lookahead) {
    override def findCounterExample(
        hypothesis: DFA[_, String],
        inputs: java.util.Collection[? <: String]
    ): DefaultQuery[String, java.lang.Boolean] = {
        val testWordStream = generateTestWords(hypothesis, inputs);
        val queryStream : java.util.stream.Stream[DefaultQuery[String, java.lang.Boolean]] = testWordStream.map({x => DefaultQuery(x)});
        val answeredQueryStream = answerQueries(queryStream);

        val ceStream = answeredQueryStream.filter(query => {
            val hypOutput = hypothesis.computeOutput(query.getInput());
            // Only keep queries for words accepted by hypothesis, and rejected by the system (this is a cex for hypothesis <= system)
            hypOutput && !query.getOutput()
        });

        return ceStream.findFirst().orElse(null);
    }


    private def answerQueries(stream : java.util.stream.Stream[DefaultQuery[String, java.lang.Boolean]] ) : java.util.stream.Stream[DefaultQuery[String, java.lang.Boolean]] = {
        stream.peek(mqOracle.processQuery);
    }    
  }

  class LowerBoundOracle
      extends EquivalenceOracle.DFAEquivalenceOracle[String] {
    private val wpInclusionOracle = DFAWpMethodInclusionOracle(taMembershipOracle, 3)

    override def findCounterExample(
        hypothesis: DFA[_, String],
        inputs: java.util.Collection[? <: String]
    ): DefaultQuery[String, java.lang.Boolean] = {
      statistics.Counters.incrementCounter("conjecture_underH")

      logger.debug("Checking if lower bound is found |DFA|=" + hypothesis.size() + ": " + statistics.Counters.toString)
      SynthesisLearningLock.setUnderApproximation(hypothesis)
      // Visualization.visualize(hypothesis,inputs)

      // Check if underH <= T
      wpInclusionOracle.findCounterExample(hypothesis, inputs) match {
        case null => // underH <= T
          logger.debug("New under approximation found")
          if (configuration.globalConfiguration.visualizeDFA) then
            Visualization.visualize(hypothesis,inputs)
          logger.debug("Checking if the environment-controlled system " + SynthesisLearningLock.strategy + " conforms to underH")

          // Check whether FSM^sigma <= underH i.e. check FSM^sigma x comp(underH) = 0.
          val smvStrategy = fsm.SMV(SynthesisLearningLock.strategy)
          val fsmIntersectionOracle = fsm.SMVIntersectionOracle(smvStrategy)
          val compHypothesis = DFAs.complement(hypothesis, Alphabets.fromCollection(inputs))

          fsmIntersectionOracle.checkIntersection(compHypothesis) match {
            case None =>
              // We have FSM^sigma <= underH. Counterstrategy validated
              SynthesisLearningLock.setVerdict(false)
              logger.info(RED + BOLD + "\nUncontrollable\n" + RESET)
              logger.info("Total number of conjectures made: " + (statistics.Counters.get("conjecture_overH") + statistics.Counters.get("conjecture_underH")))
              SynthesisLearningLock.overApproximation match 
              { case Some(aut) =>
                logger.info("Size of conjecture overH: " + aut.size())
                case _ => ()
              }
              SynthesisLearningLock.underApproximation match 
              { case Some(aut) =>
                logger.info("Size of conjecture underH: " + aut.size())
                case _ => ()
              }

              logger.info(statistics.Counters.counter.toString)
              if (configuration.globalConfiguration.visualizeDFA) then
                Visualization.visualize(hypothesis,inputs)
              null
            case Some(fsm.CounterExample(cexDescription: String, cexTrace: List[String])) =>
              // We have FSM^sigma <=/= underH because cexTrace in FSM^sigma but not in barH.
              val word = Word.fromList(cexTrace)
              logger.debug(
                RED + "UnderAppr: Counter strategy has a trace that is not in underH: " + cexTrace + RESET + "\n"
              )
              logger.debug(
                RED + "Checking the feasibility of this trace w.r.t. TA" + RESET + "\n"
              )
              if (taMembershipOracle.answerQuery(word)){
                // cexTrace is accepted by TA. Make query so that underH accepts it as well
                val query = DefaultQuery[String, java.lang.Boolean](
                      word,
                      java.lang.Boolean.TRUE
                    )
                if (configuration.globalConfiguration.verbose){
                  logger.debug(query.toString)
                  logger.debug("cexTrace is accepted by TA. Make query so that underH accepts it as well")
                }
                query
              } else {
                // cexTrace is rejected by TA. Pause learning and go back to OverAppr learning with new query
                // barH should reject cexTrace as well
                val query = DefaultQuery[String, java.lang.Boolean](
                      word,
                      java.lang.Boolean.FALSE
                    )
                if (configuration.globalConfiguration.verbose){
                  logger.debug(query.toString)
                  logger.debug("cexTrace is rejected by TA. Update overH to exclude this trace")
                }
                SynthesisLearningLock.query = query
                SynthesisLearningLock.switchPhase()
                SynthesisLearningLock.waitForPhase(UnderApprPhase)
                if (SynthesisLearningLock.verdict != None) {
                    null
                } else {
                    // Here, the OverAppr phase has generated a new counterstrategy
                    // UnderAppr learning phase still has the same DFA hypothesis, so check it for equivalence
                    findCounterExample(hypothesis, inputs)
                }
              }
          }
        case query => query
      }
    }
  }

  def run(): Unit = {
    val alph = Alphabets.fromList(verilog.alphabet)
    val lstar = ClassicLStarDFABuilder[String]()
      .withAlphabet(alph)
      .withOracle(taMembershipOracle)
      .create()
    val dt = DTLearnerDFABuilder[String]()
      .withAlphabet(alph)
      .withOracle(taMembershipOracle)
      .create()
    val kv = KearnsVaziraniDFABuilder[String]()
      .withAlphabet(alph)
      .withOracle(taMembershipOracle)
      .create()

    val lowerTTT = TTTLearnerDFABuilder[String]()
      .withAlphabet(alph)
      .withOracle(taMembershipOracle)
      .create()

    val upperTTT = TTTLearnerDFABuilder[String]()
      .withAlphabet(alph)
      .withOracle(taMembershipOracle)
      .create()
      
    val upperEqOracle = UpperBoundOracle()
    val lowerEqOracle = LowerBoundOracle()
    // val experiment: DFAExperiment[String] = DFAExperiment(lstar, eqOracle, alph);
    val upperExperiment: DFAExperiment[String] = DFAExperiment(upperTTT, upperEqOracle, alph);
    val lowerExperiment: DFAExperiment[String] = DFAExperiment(lowerTTT, lowerEqOracle, alph);

    // turn on time profiling
    upperExperiment.setProfile(true);
    lowerExperiment.setProfile(true);

    // enable logging of models
    upperExperiment.setLogModels(true);
    lowerExperiment.setLogModels(true);
    new Thread{
        override def run() : Unit = {
            SynthesisLearningLock.waitForPhase(UnderApprPhase)
            if SynthesisLearningLock.verdict == None then {
              lowerExperiment.run()
            }
        }
    }.start()
    upperExperiment.run()

  }
}
