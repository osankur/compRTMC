package fr.irisa.comprtmc
import collection.JavaConverters._
import de.learnlib.api.algorithm.LearningAlgorithm.DFALearner
import de.learnlib.algorithms.lstar.dfa.ClassicLStarDFA;
import de.learnlib.algorithms.lstar.dfa.ClassicLStarDFABuilder;
import de.learnlib.api.oracle.MembershipOracle.DFAMembershipOracle;
import de.learnlib.datastructure.observationtable.OTUtils;
import de.learnlib.datastructure.observationtable.writer.ObservationTableASCIIWriter;
import de.learnlib.filter.statistic.oracle.DFACounterOracle;
import de.learnlib.oracle.equivalence.DFAWMethodEQOracle;
import de.learnlib.oracle.membership.SimulatorOracle.DFASimulatorOracle;
import de.learnlib.util.Experiment.DFAExperiment;
import de.learnlib.util.statistics.SimpleProfiler;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.util.automata.builders.AutomatonBuilders;
import net.automatalib.visualization.Visualization;
import net.automatalib.words.Alphabet;
import net.automatalib.words.impl.Alphabets;
import de.learnlib.util.MQUtil;
import de.learnlib.api.oracle.EquivalenceOracle
import de.learnlib.api.query.DefaultQuery;

import de.learnlib.api.oracle._
import de.learnlib.api.oracle.MembershipOracle
import net.automatalib.words._
import scala.util.Random
import scala.collection.mutable.ArrayBuffer

class SampleMembershipOracle extends MembershipOracle[Character,java.lang.Boolean] {
    override def processQueries(queries : java.util.Collection[? <: de.learnlib.api.query.Query[Character, java.lang.Boolean]]): Unit =
        {
                  MQUtil.answerQueries(this, queries);
        }
    override def answerQuery(prefix : Word[Character], suffix : Word[Character]) : java.lang.Boolean = {
        val pre = prefix.asList().asScala
        val suf = suffix.asList().asScala
        val wholeTrace = (pre++suf).mkString("")
        System.out.println(wholeTrace)
        // Dump the intersection of TA for the word prefix.suffix, with input TA T, and call TChecker.
        "a*b*".r.matches(wholeTrace)
    }
}

class SampleEquivalenceOracle extends EquivalenceOracle.DFAEquivalenceOracle[Character] {
    val inputs : Alphabet[Character] = Alphabets.characters('a', 'b')
    val target : CompactDFA[Character] =
        AutomatonBuilders.newDFA(inputs)
                .withInitial("q0")
                .from("q0")
                    .on('a').to("q0")
                .from("q0")
                    .on('b').to("q1")
                .from("q1")
                    .on('b').to("q1")
                .from("q1")
                    .on('a').to("q2")
                .withAccepting("q0")
                .withAccepting("q1")
                .create();
        // AutomatonBuilders.newDFA(inputs)
        //         .withInitial("q0")
        //         .from("q0")
        //             .on('a').to("q1")
        //         .from("q1")
        //             .on('a').to("q1")
        //         .from("q1")
        //             .on('b').to("q2")
        //         .from("q2")
        //             .on('c').to("q3")
        //         .from("q3")
        //             .on('a').to("q2")
        //         .from("q3")
        //             .on('d').to("q4")
        //         .withAccepting("q0")
        //         .withAccepting("q1")
        //         .withAccepting("q2")
        //         .withAccepting("q3")
        //         .withAccepting("q4")
        //         .create();
        
      override def findCounterExample(hypothesis : DFA[_,Character], inputs : java.util.Collection[? <: Character]) : DefaultQuery[Character, java.lang.Boolean] = {
        // Try traces in a*b* but not in hypothesis
        val rand = Random(0)
        for (x <- 0 to 100) {
          val i = rand.nextInt() % 10
          val j = rand.nextInt() % 10
          val words = ArrayBuffer[Character]()
          for (_ <- 1 to i){
            words.addOne('a')
          }
          for (_ <- 1 to j){
            words.addOne('b')
          }
          if (!hypothesis.accepts(words.asJava)){
            return DefaultQuery[Character, java.lang.Boolean](Word.fromArray[Character](words.toArray,0,words.length), java.lang.Boolean.TRUE)
          }
        }

        // Try traces in hypothesis and check if they are in a*b*
        for (_  <- 0 to 1000){
          val expLength = rand.nextInt() % 10
          var s = hypothesis.getInitialState()
          val word = ArrayBuffer[Character]()
          for (_ <- 1 to expLength){
            val nextLetter : Character = 
              if (rand.nextInt() % 2 == 0) then {
                'a'
              } else {
                'b'
              }
            word.addOne(nextLetter)
            s = hypothesis.getSuccessor(s, nextLetter)
          }
          if (hypothesis.isAccepting(s)){
            if (!"a*b*".r.matches(word.mkString(""))) then{
              return DefaultQuery[Character, java.lang.Boolean](Word.fromArray[Character](word.toArray,0,word.length), java.lang.Boolean.FALSE)
            }
          }
        }
        null
      }
}

object Example {

  def example2() : Unit = {
    val inputs : Alphabet[Character] = Alphabets.characters('a', 'b')
    val mqOracle : MembershipOracle[Character,java.lang.Boolean]= SampleMembershipOracle()
    val lstar = ClassicLStarDFABuilder[Character]().withAlphabet(inputs).withOracle(mqOracle).create()
    val wMethod = SampleEquivalenceOracle()
    val experiment : DFAExperiment[Character] = DFAExperiment(lstar, wMethod, inputs);

      // turn on time profiling
    experiment.setProfile(true);

      // enable logging of models
    experiment.setLogModels(true);

      // run experiment
    experiment.run();

    // get learned model
    val result = experiment.getFinalHypothesis();

    // report results
    System.out.println("-------------------------------------------------------");

    // profiling
    System.out.println(SimpleProfiler.getResults());

    // learning statistics
    System.out.println(experiment.getRounds().getSummary());

    // model statistics
    System.out.println("States: " + result.size());
    System.out.println("Sigma: " + inputs.size());

    // show model
    Visualization.visualize(result, inputs);

    System.out.println("-------------------------------------------------------");

  }

  def example1() : Unit = {
    val EXPLORATION_DEPTH = 4
    def constructSUL() : CompactDFA[Character]  = {
        // input alphabet contains characters 'a'..'b'
        val sigma : Alphabet[Character] = Alphabets.characters('a', 'b')
        // create automaton
        return AutomatonBuilders.newDFA(sigma)
                .withInitial("q0")
                .from("q0")
                    .on('a').to("q1")
                    .on('b').to("q2")
                .from("q1")
                    .on('a').to("q0")
                    .on('b').to("q3")
                .from("q2")
                    .on('a').to("q3")
                    .on('b').to("q0")
                .from("q3")
                    .on('a').to("q2")
                    .on('b').to("q1")
                .withAccepting("q0")
                .create();
    }
    val target : CompactDFA[Character] = constructSUL()
    val inputs = target.getInputAlphabet()
    val sul : DFAMembershipOracle[Character] = DFASimulatorOracle(target)

    // oracle for counting queries wraps SUL
    val mqOracle : DFACounterOracle[Character] = DFACounterOracle(sul, "membership queries");

    val lstar = ClassicLStarDFABuilder[Character]().withAlphabet(inputs).withOracle(mqOracle).create()
    val wMethod : DFAWMethodEQOracle[Character] = DFAWMethodEQOracle(mqOracle, EXPLORATION_DEPTH);

      // construct a learning experiment from
      // the learning algorithm and the conformance test.
      // The experiment will execute the main loop of
      // active learning
    val experiment : DFAExperiment[Character] = DFAExperiment(lstar, wMethod, inputs);

      // turn on time profiling
    experiment.setProfile(true);

      // enable logging of models
    experiment.setLogModels(true);

      // run experiment
    experiment.run();

    // get learned model
    val result = experiment.getFinalHypothesis();

    // report results
    System.out.println("-------------------------------------------------------");

    // profiling
    System.out.println(SimpleProfiler.getResults());

    // learning statistics
    System.out.println(experiment.getRounds().getSummary());
    System.out.println(mqOracle.getStatisticalData().getSummary());

    // model statistics
    System.out.println("States: " + result.size());
    System.out.println("Sigma: " + inputs.size());

    // show model
    Visualization.visualize(result, inputs);

    System.out.println("-------------------------------------------------------");

    // System.out.println("Final observation table:");
    // ObservationTableASCIIWriter().write[de.learnlib.datastructure.observationtable.ObservationTable[Character,Boolean],java.io.PrintStream](lstar.getObservationTable(), System.out);
  }
}