package fr.irisa.comprtmc
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

import de.learnlib.algorithms.kv.dfa.KearnsVaziraniDFA
import de.learnlib.algorithms.kv.dfa.KearnsVaziraniDFABuilder
import de.learnlib.algorithms.discriminationtree.dfa.DTLearnerDFA
import de.learnlib.algorithms.discriminationtree.dfa.DTLearnerDFABuilder

case class CounterExample(cexDescription : String) extends Exception

class CompSafetyAlgorithm(
    fsmIntersectionOracle : FSMIntersectionOracle,
    taMembershipOracle : TAMembershipOracle, 
    taInclusionOracle : EquivalenceOracle.DFAEquivalenceOracle[String]
){
    private val logger = LoggerFactory.getLogger(classOf[CompSafetyAlgorithm])

    case class ProductCounterExample(smvTrace : String, trace : List[String], taTrace : String) extends Exception

    /** Oracle for compositional model checking. Given hypothesis DFA which includes TA,
     *  the oracle first checks if FSM x hypothesis satisfies the safety specification
     *  using @fsmIntersectionOracle. If no counterexample is found, then safety is established.
     *  Otherwise, @taMembershipOracle is used to check if the cex is realizable in the TA.
     *  If yes, then the counterexample is confirmed. Otherwise, a query is returned to rule out
     *  the trace of the counterexample. 
     */
    class EqOracle(fsmIntersectionOracle : FSMIntersectionOracle, taInclusionOracle : EquivalenceOracle.DFAEquivalenceOracle[String]) 
        extends EquivalenceOracle.DFAEquivalenceOracle[String]{
        override def findCounterExample(
            hypothesis: DFA[_, String],
            inputs: java.util.Collection[? <: String]
        ): DefaultQuery[String, java.lang.Boolean] = {
            assert(inputs.size == fsmIntersectionOracle.alphabet.length)
            assert(posQueries.intersect(negQueries).isEmpty)
            System.out.println(Counters.toString)
            taInclusionOracle.findCounterExample(hypothesis,inputs) match {                
                case null =>
                    fsmIntersectionOracle.checkIntersection(hypothesis) match{
                        case None => 
                            // Verification succeeded
                            // Visualization.visualize(hypothesis, inputs)
                            null
                        case Some(FSMOracles.CounterExample(cexDescription, trace)) =>
                            // FSM x hypothesis contains a counterexample trace
                            // Check feasability with TChecker
                            System.out.println(RED + "Equiv. Query: FSM Counterexample found: " + trace + RESET + "\n")
                            System.out.println(YELLOW + "Checking the feasibility w.r.t. TA" + RESET + "\n")
                            val word = Word.fromList(trace)
                            if (trace == lastTrace){
                                Visualization.visualize(hypothesis, inputs)
                                throw Exception("The following counterexample trace was seen twice: " + trace)
                            } else {
                                lastTrace = trace;
                            }
                            

                            //  if feasible: return counterexample
                            //  otherwise, create query to rule out the trace
                            taMembershipOracle.answerQuery(Word.epsilon,word, true) match{
                                case Some(timedTrace) => 
                                    throw ProductCounterExample(cexDescription, trace, timedTrace)
                                case None => 
                                    System.out.println(GREEN + "Equiv. Query: Cex was spurious\n" + RESET)
                                    System.out.println(DefaultQuery[String, java.lang.Boolean](word, java.lang.Boolean.FALSE))
                                    negQueries = negQueries + word.mkString(" ")
                                    // Visualization.visualize(hypothesis, inputs)
                                    DefaultQuery[String, java.lang.Boolean](word, java.lang.Boolean.FALSE)
                            }                             
                            
                    }
                case query => query
            }
        }
    }
    
    def run() : Unit = {
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

        val eqOracle = EqOracle(fsmIntersectionOracle, taInclusionOracle)
        // val experiment: DFAExperiment[String] = DFAExperiment(lstar, eqOracle, alph);
        val experiment: DFAExperiment[String] = DFAExperiment(dt, eqOracle, alph);

        // turn on time profiling
        experiment.setProfile(true);

        // enable logging of models
        experiment.setLogModels(true);
        try {
            experiment.run();
            System.out.println(GREEN + BOLD + "\nSafety holds\n" + RESET)
            val result = experiment.getFinalHypothesis();
            Visualization.visualize(result, alph)
            System.out.println(SimpleProfiler.getResults());
            System.out.println(experiment.getRounds().getSummary());
            System.out.println("States: " + result.size());
            System.out.println("Sigma: " + alph.size());
            System.out.println("Total system calls: " + taMembershipOracle.systemElapsedTime / 1000000L + "ms");
            System.out.println("Nb of membership queries: " + taMembershipOracle.nbQueries);
            System.out.println("Time per query: " + taMembershipOracle.systemElapsedTime / 1000000L / taMembershipOracle.nbQueries + "ms");
        } catch {
            case ProductCounterExample(smvTrace, trace, taTrace) =>
                System.out.println(RED + BOLD + "\nError state is reachable " + RESET + "\nOn the following synchronized word:\n")
                System.out.println("\t" + YELLOW + BOLD + trace + RESET)
                System.out.println("\nSMV trace:")
                System.out.println(YELLOW + smvTrace + RESET)
                System.out.println("\nTA trace:")
                System.out.println(YELLOW + taTrace + RESET)                
            case ParseError(msg) =>
                System.out.println(RED + msg + RESET)
            case e => throw e
        }
    }
}