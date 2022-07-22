// For more information on writing tests, see
// https://scalameta.org/munit/docs/getting-started.html
package fr.irisa.comprtmc
import net.automatalib.words.Word
import net.automatalib.words.Alphabet;
import net.automatalib.words.impl.Alphabets;
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.util.automata.builders.AutomatonBuilders;
import collection.JavaConverters._
import java.io.File
import java.io.FileInputStream
import de.learnlib.api.algorithm.LearningAlgorithm.DFALearner
import de.learnlib.algorithms.lstar.dfa.ClassicLStarDFA;
import de.learnlib.algorithms.lstar.dfa.ClassicLStarDFABuilder;
import de.learnlib.api.oracle.MembershipOracle.DFAMembershipOracle;
import de.learnlib.datastructure.observationtable.OTUtils;
import de.learnlib.datastructure.observationtable.writer.ObservationTableASCIIWriter;
import de.learnlib.filter.statistic.oracle.DFACounterOracle;
import de.learnlib.oracle.equivalence.DFAWMethodEQOracle;
import de.learnlib.oracle.membership.SimulatorOracle.DFASimulatorOracle;
import de.learnlib.api.oracle.MembershipOracle
import de.learnlib.util.Experiment.DFAExperiment;
import de.learnlib.util.statistics.SimpleProfiler;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.automata.fsa.NFA;
import net.automatalib.automata.fsa.impl.compact.CompactNFA;

import net.automatalib.util.automata.builders.AutomatonBuilders;
import net.automatalib.visualization.Visualization;
import net.automatalib.words.Alphabet;
import net.automatalib.words.impl.Alphabets;
import net.automatalib.serialization.aut._
import collection.convert.ImplicitConversions
import net.automatalib.util.automata.fsa.NFAs
import net.automatalib.util.automata.fsa.DFAs
import net.automatalib.util.automata.minimizer.paigetarjan.PaigeTarjanMinimization 

import net.automatalib.serialization.taf.parser.TAFParser
import net.automatalib.automata.Automaton


/*
class BasicTests extends munit.FunSuite {
  test("example test that succeeds") {
    val obtained = 42
    val expected = 42
    assertEquals(obtained, expected)
  }

  test("a.ta") {
    val alphabetList = List("get1", "get2").asJava
    val alphabet: Alphabet[String] = Alphabets.fromList(alphabetList)
    val target: CompactDFA[String] =
      AutomatonBuilders
        .newDFA(alphabet)
        .withInitial("q0")
        .from("q0")
        .on("get1")
        .to("q0")
        .on("get2")
        .to("q1")
        .withAccepting("q0")
        .create();
    val ta = TCheckerTA(File("resources/examples/a.ta"))
    val mOracle = TCheckerMembershipOracle(ta,alphabet)
    assertEquals(
      mOracle.answerQuery(Word.fromList(List("get1", "rel1").asJava)),
      java.lang.Boolean.FALSE
    )
    val inclOracle = TCheckerInclusionOracle(ta, alphabet)
    val query = inclOracle.findCounterExample(target, null)
    if (query == null) {
      assertNotEquals(true, false)
    }
  }
}
*/
class FullZG extends munit.FunSuite {
  def learnForInclusion(input: String, containment : Boolean): Unit = {
    val tckTA = ta.TCheckerTA(File(input))
    val alphabet: Alphabet[String] = Alphabets.fromList(tckTA.events.asJava)
    val mOracle
        : de.learnlib.api.oracle.MembershipOracle[String, java.lang.Boolean] =
      ta.TCheckerMembershipOracle(tckTA,alphabet)
    val inclOracle = 
      if !containment then {
        ta.TCheckerInclusionOracle(tckTA,alphabet)
      } else {
        ta.TCheckerContainmentOracle(File(input),alphabet)
      }
    val lstar = ClassicLStarDFABuilder[String]()
      .withAlphabet(alphabet)
      .withOracle(mOracle)
      .create()
    val experiment: DFAExperiment[String] =
      DFAExperiment(lstar, inclOracle, alphabet);

    // turn on time profiling
    experiment.setProfile(true);

    // enable logging of models
    experiment.setLogModels(true);

    // run experiment
    experiment.run();

    // get learned model
    val result = experiment.getFinalHypothesis();

    // report results
    System.out.println(
      "-------------------------------------------------------"
    );

    // profiling
    System.out.println(SimpleProfiler.getResults());

    // learning statistics
    System.out.println(experiment.getRounds().getSummary());

    // model statistics
    System.out.println("States: " + result.size());
    System.out.println("Sigma: " + alphabet.size());

    // show model
    Visualization.visualize(result, alphabet);

    System.out.println(
      "-------------------------------------------------------"
    )
  }
  test("a_flat.ta") {
    // learnForInclusion("resources/examples/a_flat.ta")
    // learnForInclusion("resources/examples/tests/a_flat.ta", false)
  }
  test("untimed") {
    // learnForInclusion("resources/examples/a_flat.ta")
    // learnForInclusion("resources/examples/tests/untimed.ta", false)
  }
  test("untimed-containment"){
    // learnForInclusion("resources/examples/tests/untimed.ta", true)
  }
  test("a"){
    // learnForInclusion("resources/examples/tests/d.ta", true)
  }
}
class SynthTest extends munit.FunSuite {
  // test("abssynthe-unr"){
  //   synthesis.AbssyntheOracle(synthesis.Verilog(File("/home/osankur/ulb/AbsSynthe/examples/example2.smv"))).synthesize() match{
  //     case synthesis.Uncontrollable(_) => ()
  //     case _ => assert(false)
  //   }
  // }
  // test("abssynthe-rea"){
  //   synthesis.AbssyntheOracle(synthesis.Verilog(File("/home/osankur/ulb/AbsSynthe/examples/example1.smv"))).synthesize() match{
  //     case synthesis.Controllable(_) => ()
  //     case _ => assert(false)
  //   }
  // }
}

/*
class DFATest extends munit.FunSuite {
  test("dfa2"){
    Example.example2()
    val inp = FileInputStream("resources/examples/a.des")
    val aut = AUTParser.readAutomaton(inp)
  }
}
*/
class SMVTest extends munit.FunSuite {
  test("smv"){
    val inp = fsm.SMV(File("resources/examples/mono_scheduling/genbuf2b3unrealy.smv"))
  }
}
class VerilogTest extends munit.FunSuite {
  test("verilog1"){
    val alphabetList = List("robot", "obs1", "obs2").asJava
    val alphabet: Alphabet[String] = Alphabets.fromList(alphabetList)
    val target: CompactDFA[String] =
      AutomatonBuilders
        .newDFA(alphabet)
        .withInitial("q0")
        .from("q0")
        .on("robot")
        .to("q1")
        .from("q1")
        .on("obs1")
        .to("q0")
        .withAccepting("q0")
        .create();

    val inp = synthesis.Verilog(File("resources/examples/synthesis/safe_planning.v"))
    System.out.println("Alphabet: " + inp.alphabet)
    System.out.println("InterAlphabet: " + inp.inputs)
    System.out.println("Output: " + inp.outputs)
    System.out.println(inp.intersectedWith(target))
  }
}

class AutomataTest extends munit.FunSuite{
  test("determinize"){
  val alphabet: Alphabet[String] = Alphabets.fromList(List("a","b", "c").asJava)
  val target: CompactNFA[String] =
    AutomatonBuilders.newNFA(alphabet)
      .withInitial("q0")
      .from("q0")
      .on("a")
      .to("q0")
      .on("a")
      .to("q1")
      .from("q1")
      .on("b")
      .to("q1")
      .withAccepting("q1")
      .create();

  val aut = NFAs.determinize(target)
  val minaut = PaigeTarjanMinimization.minimizeDFA(aut)
  // val target_as : Automaton[Integer, String, Integer] = target
  // val serializer = SAFSerializationNFA.getInstance()
  // serializer.writeModel(java.lang.System.out, target, alphabet)
  // Visualization.visualize(minaut, alphabet)
  }
}

class ThreadTest extends munit.FunSuite {
  test("thread-basic"){
    // Example.threadExample()
  }
}