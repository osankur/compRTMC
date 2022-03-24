package fr.irisa.comprtmc

import net.automatalib.words.Alphabet;
import net.automatalib.words.impl.Alphabets;
import de.learnlib.util.MQUtil;
import de.learnlib.api.oracle.EquivalenceOracle
import de.learnlib.api.query.DefaultQuery;
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.util.automata.builders.AutomatonBuilders;
import net.automatalib.automata.fsa.DFA;

class TAInclusionOracle extends EquivalenceOracle.DFAEquivalenceOracle[Character] {
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
        
      override def findCounterExample(hypothesis : DFA[_,Character], inputs : java.util.Collection[? <: Character]) : DefaultQuery[Character, java.lang.Boolean] = {
          null
      }    
    
    }