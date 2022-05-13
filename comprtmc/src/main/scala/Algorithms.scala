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

case class CounterExample(cexDescription: String) extends Exception

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

class CompSynthesisAlgorithm(
    verilog: synthesis.Verilog,
    synthesisOracle: synthesis.SynthesisOracle,
    taMembershipOracle: ta.TAMembershipOracle,
    taInclusionOracle: EquivalenceOracle.DFAEquivalenceOracle[String],
    taContainmentOracle: EquivalenceOracle.DFAEquivalenceOracle[String]
) {
  private val logger = LoggerFactory.getLogger(classOf[CompSynthesisAlgorithm])
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
              System.out.println("Thread Interrupted");
          }          
        }
        if (verdict == None) 
          System.out.println("<--" + BOLD + BLUE + " Switched to phase " + GREEN + targetPhase.toString  + RESET + " -->")
      }
    }
    def setVerdict(v : Boolean) : Unit = {
        this.synchronized{
            verdict = Some(v)
            notifyAll()
        }
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
      System.out.println(statistics.Counters.toString)
      taInclusionOracle.findCounterExample(hypothesis, inputs) match {
        case null => // T <= barH
          System.out.println("New over approximation found")
          Visualization.visualize(hypothesis,inputs)
          synthesisOracle.synthesizeIntersection(hypothesis) match {
            case synthesis.Controllable(strat) =>
              // Synthesis succeeded
              // Visualization.visualize(hypothesis, inputs)
              SynthesisLearningLock.strategy = strat
              SynthesisLearningLock.setVerdict(true)
              System.out.println(GREEN + BOLD + "\nControllable\n" + RESET)
              Visualization.visualize(hypothesis,inputs)
              null
            case synthesis.Uncontrollable(strat) =>
              System.out.println(
                RED + "OverAppr Query: Uncontrollable" + RESET + "\n"
              )
              System.out.println(
                YELLOW + "Checking the feasibility of the counterstrategy w.r.t. TA" + RESET + "\n"
              )
              SynthesisLearningLock.strategy = strat
              SynthesisLearningLock.switchPhase()
              SynthesisLearningLock.waitForPhase(OverApprPhase)
              System.out.println("Got back to overappr")
              if (SynthesisLearningLock.verdict != None) {
                null
              } else {
                System.out.println("Adding Query: " + SynthesisLearningLock.query)
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
      System.out.println(statistics.Counters.toString)

      // Check if underH <= T
      wpInclusionOracle.findCounterExample(hypothesis, inputs) match {
        case null => // underH <= T
          System.out.println("New under approximation found")
          Visualization.visualize(hypothesis,inputs)
          System.out.println("Analyzing closed-loop system: " + SynthesisLearningLock.strategy)
          // Check whether FSM^sigma <= barH i.e. check FSM^sigma x comp(underH) = 0.
          val smvStrategy = fsm.SMV(SynthesisLearningLock.strategy)
          val fsmIntersectionOracle = fsm.SMVIntersectionOracle(smvStrategy)
          val compHypothesis = DFAs.complement(hypothesis, Alphabets.fromCollection(inputs))

          fsmIntersectionOracle.checkIntersection(compHypothesis) match {
            case None =>
              // We have FSM^sigma <= underH. Counterstrategy validated
              SynthesisLearningLock.setVerdict(false)
              System.out.println(RED + BOLD + "\nUncontrollable\n" + RESET)
              Visualization.visualize(hypothesis,inputs)
              null
            case Some(fsm.CounterExample(cexDescription: String, cexTrace: List[String])) =>
              // We have FSM^sigma <=/= underH because cexTrace in FSM^sigma but not in barH.
              val word = Word.fromList(cexTrace)
              System.out.println(
                RED + "UnderAppr: Counter strategy has a trace that is not in underH: " + cexTrace + RESET + "\n"
              )
              System.out.println(
                RED + "Checking the feasibility of this trace w.r.t. TA" + RESET + "\n"
              )
              if (taMembershipOracle.answerQuery(word)){
                // cexTrace is accepted by TA. Make query so that barH accepts it as well
                val query = DefaultQuery[String, java.lang.Boolean](
                      word,
                      java.lang.Boolean.TRUE
                    )
                System.out.println(query)
                query
              } else {
                // cexTrace is rejected by TA. Pause learning and go back to OverAppr learning with new query
                // barH should reject cexTrace as well
                val query = DefaultQuery[String, java.lang.Boolean](
                      word,
                      java.lang.Boolean.FALSE
                    )
                System.out.println(query)
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
    /*
    val lowerThread = new Thread{
        override def run() : Unit = {
            SynthesisLearningLock.waitForPhase(UnderApprPhase)
            lowerExperiment.run()
        }
    }
    lowerThread.start()
    */
    new Thread{
        override def run() : Unit = {
            SynthesisLearningLock.waitForPhase(UnderApprPhase)
            lowerExperiment.run()
        }
    }.start()
    upperExperiment.run()
    /*
    new Thread{
        override def run() : Unit = {
            // SynthesisLearningLock.waitForPhase(OverApprPhase)
            upperExperiment.run()
        }
    }.start()
    */
    // lowerExperiment.run()
    // upperExperiment.run()
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
