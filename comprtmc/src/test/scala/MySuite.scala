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
import net.automatalib.util.automata.builders.AutomatonBuilders;
import net.automatalib.visualization.Visualization;
import net.automatalib.words.Alphabet;
import net.automatalib.words.impl.Alphabets;
import net.automatalib.serialization.aut._
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

class FullZG extends munit.FunSuite {
  def learnForInclusion(input: String): Unit = {
    val ta = TCheckerTA(File(input))
    val alphabet: Alphabet[String] = Alphabets.fromList(ta.events.asJava)
    val mOracle
        : de.learnlib.api.oracle.MembershipOracle[String, java.lang.Boolean] =
      TCheckerMembershipOracle(ta,alphabet)
    val inclOracle = TCheckerInclusionOracle(ta,alphabet)
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
    // Visualization.visualize(result, alphabet);

    System.out.println(
      "-------------------------------------------------------"
    )
  }
  test("a_flat.ta") {
    // learnForInclusion("resources/examples/a_flat.ta")
    learnForInclusion("resources/examples/a_flat.ta")
  }
  test("untimed") {
    // learnForInclusion("resources/examples/a_flat.ta")
    learnForInclusion("resources/examples/untimed.ta")
  }
}

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
    val inp = SMV(File("resources/examples/genbuf2b3unrealy.smv"))
  }
}