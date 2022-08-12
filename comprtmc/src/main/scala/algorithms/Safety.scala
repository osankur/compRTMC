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

class CompSafetyAlgorithm(
    fsmIntersectionOracle: fsm.FSMIntersectionOracle,
    taMembershipOracle: ta.TAMembershipOracle,
    taInclusionOracle: EquivalenceOracle.DFAEquivalenceOracle[String]
) {
  private val logger = LoggerFactory.getLogger(classOf[CompSafetyAlgorithm])

  case class ProductCounterExample(
      smvTrace: String,
      trace: List[String],
      taTrace: String
  ) extends Exception

  /** Oracle for compositional model checking. Given hypothesis DFA which
    * includes TA, the oracle first checks if FSM x hypothesis satisfies the
    * safety specification using @fsmIntersectionOracle. If no counterexample is
    * found, then safety is established. Otherwise, @taMembershipOracle is used
    * to check if the cex is realizable in the TA. If yes, then the
    * counterexample is confirmed. Otherwise, a query is returned to rule out
    * the trace of the counterexample.
    */
  class EqOracle(
      fsmIntersectionOracle: fsm.FSMIntersectionOracle,
      taInclusionOracle: EquivalenceOracle.DFAEquivalenceOracle[String]
  ) extends EquivalenceOracle.DFAEquivalenceOracle[String] {
    override def findCounterExample(
        hypothesis: DFA[_, String],
        inputs: java.util.Collection[? <: String]
    ): DefaultQuery[String, java.lang.Boolean] = {
      assert(inputs.size == fsmIntersectionOracle.alphabet.length)
      assert(statistics.posQueries.intersect(statistics.negQueries).isEmpty)
      System.out.println(statistics.Counters.toString)
      taInclusionOracle.findCounterExample(hypothesis, inputs) match {
        case null =>
          fsmIntersectionOracle.checkIntersection(hypothesis) match {
            case None =>
              // Verification succeeded
              // Visualization.visualize(hypothesis, inputs)
              null
            case Some(fsm.CounterExample(cexDescription, trace)) =>
              // FSM x hypothesis contains a counterexample trace
              // Check feasability with TChecker
              System.out.println(
                RED + "Equiv. Query: FSM Counterexample found: " + trace + RESET + "\n"
              )
              System.out.println(
                YELLOW + "Checking the feasibility w.r.t. TA" + RESET + "\n"
              )
              val word = Word.fromList(trace)
              if (trace == statistics.lastTrace) {
                if (configuration.globalConfiguration.visualizeDFA) then
                  Visualization.visualize(hypothesis, inputs)
                throw Exception(
                  "The following counterexample trace was seen twice: " + trace
                )
              } else {
                statistics.lastTrace = trace;
              }

              //  if feasible: return counterexample
              //  otherwise, create query to rule out the trace
              taMembershipOracle.answerQuery(Word.epsilon, word, true) match {
                case Some(timedTrace) =>
                  throw ProductCounterExample(cexDescription, trace, timedTrace)
                case None =>
                  System.out.println(
                    GREEN + "Equiv. Query: Cex was spurious\n" + RESET
                  )
                  System.out.println(
                    DefaultQuery[String, java.lang.Boolean](
                      word,
                      java.lang.Boolean.FALSE
                    )
                  )
                  statistics.negQueries =
                    statistics.negQueries + word.mkString(" ")
                  // Visualization.visualize(hypothesis, inputs)
                  DefaultQuery[String, java.lang.Boolean](
                    word,
                    java.lang.Boolean.FALSE
                  )
              }

          }
        case query => query
      }
    }
  }

  def run(): Unit = {
    val alph = Alphabets.fromList(fsmIntersectionOracle.alphabet)
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
    val ttt = TTTLearnerDFABuilder[String]()
      .withAlphabet(alph)
      .withOracle(taMembershipOracle)
      .create()

    val eqOracle = EqOracle(fsmIntersectionOracle, taInclusionOracle)
    // val experiment: DFAExperiment[String] = DFAExperiment(lstar, eqOracle, alph);
    val experiment: DFAExperiment[String] = DFAExperiment(ttt, eqOracle, alph);

    // turn on time profiling
    experiment.setProfile(true);

    // enable logging of models
    experiment.setLogModels(true);
    try {
      experiment.run();
      System.out.println(GREEN + BOLD + "\nSafety holds\n" + RESET)
      val result = experiment.getFinalHypothesis();
      if (configuration.globalConfiguration.visualizeDFA) then
        Visualization.visualize(result, alph)
      System.out.println(SimpleProfiler.getResults());
      System.out.println(experiment.getRounds().getSummary());
      System.out.println("States: " + result.size());
      System.out.println("Sigma: " + alph.size());
      System.out.println(
        "Total system calls: " + taMembershipOracle.systemElapsedTime / 1000000L + "ms"
      );
      System.out.println(
        "Nb of membership queries: " + taMembershipOracle.nbQueries
      );
      System.out.println(
        "Time per query: " + taMembershipOracle.systemElapsedTime / 1000000L / taMembershipOracle.nbQueries + "ms"
      );
    } catch {
      case ProductCounterExample(smvTrace, trace, taTrace) =>
        System.out.println(
          RED + BOLD + "\nError state is reachable " + RESET + "\nOn the following synchronized word:\n"
        )
        System.out.println("\t" + YELLOW + BOLD + trace + RESET)
        System.out.println("\nSMV trace:")
        System.out.println(YELLOW + smvTrace + RESET)
        System.out.println("\nTA trace:")
        System.out.println(YELLOW + taTrace + RESET)
      case configuration.ParseError(msg) =>
        System.out.println(RED + msg + RESET)
      case e => throw e
    }
  }
}




class TARAlgorithm(
    fsmIntersectionOracle: fsm.FSMIntersectionOracle,
    taInterpolationOracle: ta.TCheckerInterpolationOracle
) {
  private val alphabet = fsmIntersectionOracle.alphabet
  private val logger = LoggerFactory.getLogger(classOf[TARAlgorithm])
  // private val learnedNFAs = List[CompactNFA[String]]()
  private var learnedDFA = {
    val dfa = CompactDFA[String](Alphabets.fromList(alphabet))
    val q = dfa.addState(true)
    alphabet.foreach(dfa.addTransition(q, _, q))
    dfa.setInitial(0, true)
    // Visualization.visualize(dfa, Alphabets.fromList(alphabet))
    dfa
  }

  case class ProductCounterExample(
      smvTrace: String,
      trace: List[String],
      taTrace: String
  ) extends Exception

  def run(): Unit = {
    var decisionReached = false
    while (!decisionReached) {
      statistics.Counters.incrementCounter("tar-iteration")
      System.out.println(statistics.Counters.toString)
      // Visualization.visualize(learnedDFA, Alphabets.fromList(alphabet))
      fsmIntersectionOracle.checkIntersection(learnedDFA) match {
        case None =>
          System.out.println(GREEN + BOLD + "\nSafety holds\n" + RESET)
          if (configuration.globalConfiguration.visualizeDFA) then
            Visualization.visualize(learnedDFA, Alphabets.fromList(alphabet))
          decisionReached = true
        case Some(fsm.CounterExample(cexDescription, trace)) =>
          System.out.println(
            RED + "FSM Counterexample found: " + trace + RESET + "\n"
          )
          System.out.println(
            YELLOW + "Checking the feasibility w.r.t. TA" + RESET + "\n"
          )
          taInterpolationOracle.checkWord(trace) match {
            case taInterpolationOracle.NonEmpty(taTrace) =>
              System.out.println(
                RED + BOLD + "\nError state is reachable " + RESET + "\nOn the following synchronized word:\n"
              )
              System.out.println("\t" + YELLOW + BOLD + trace + RESET)
              System.out.println("\nFSM trace:")
              System.out.println(YELLOW + cexDescription + RESET)
              System.out.println("\nTA trace:")
              System.out.println(YELLOW + taTrace + RESET)
              decisionReached = true
            case taInterpolationOracle.Empty(nfa) =>
              System.out.println(
                GREEN + "Trace was spurious; adding NFA\n" + RESET
              )
              val alph = Alphabets.fromList(alphabet)
              val determinized = NFAs.determinize(nfa, alph)
              val complemented = DFAs.complement(determinized, alph)
              val minimized = DFAs.minimize(complemented, alph)
              val newHypothesis = DFAs.and(learnedDFA, minimized, alph)
              System.out.println(
                YELLOW + "New hypothesis has " + newHypothesis.size + " states\n" + RESET
              )
              val newHypothesisMinimized = DFAs.minimize(newHypothesis, alph)
              System.out.println(
                YELLOW + "and " + newHypothesisMinimized.size + " once minimized\n" + RESET
              )
              learnedDFA = newHypothesisMinimized
          }
      }
    }
  }
}
