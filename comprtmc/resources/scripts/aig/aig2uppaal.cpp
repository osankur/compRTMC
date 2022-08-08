#include <iostream>
#include <string>
#include <sstream>
#include "aiger.h"

std::string to_exp(aiger * spec, unsigned int lit){
    std::stringstream ss;
    if (lit == 0){
        return std::string("0");
    }
    if (lit == 1){
        return std::string("1");
    }
    unsigned stripped = aiger_strip(lit);
    if (lit & 1){
        // negated
        ss << "!";
    };

    std::string exp;
    aiger_symbol * sym = aiger_is_latch(spec, stripped);
    if (sym){
        ss << sym->name;
    }
    sym = aiger_is_input(spec, stripped);
    if (sym){
        ss << sym->name;        
    }
    aiger_and * symand = aiger_is_and(spec, stripped);
    if (symand){
        ss << "(" << to_exp(spec, symand->rhs0) << " && " << to_exp(spec, symand->rhs1) << ")";
    }
    return ss.str();
}

void to_uppaal(aiger * spec){
    std::cout << "// Inputs\n";
    for(int i = 0; i < spec->num_inputs; i++){
        std::cout << "bool " << spec->inputs[i].name << ";\n";
    }
    std::cout << "// Latches\n";
    for(int i = 0; i < spec->num_latches; i++){
        std::cout << "bool " << spec->latches[i].name << ";\n";
    }
    std::cout << "// Updates\n";
    for(int i = 0; i < spec->num_latches; i++){
        std::cout << spec->latches[i].name << " = " << to_exp(spec, spec->latches[i].next) << ";\n";
    }

}

int main(int argc, char ** args){
    if (argc < 2){
        std::cerr << "Usage: bad2out input.aag\n";
        return -1;
    }
    const char * infile = args[1];
    aiger* spec = aiger_init();
    const char* err = aiger_open_and_read_from_file (spec, infile);
    if (err) {
        std::cerr << (std::string("Error ") + err +
               " encountered while reading AIGER file " +
               infile);
        return -1;
    }
    to_uppaal(spec);
    return 0;
}