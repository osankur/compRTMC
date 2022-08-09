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
    std::cout << "\n// Locations and Transitions\n";
    for(int i = 0; i < spec->num_inputs; i++){
        std::cout << "<location id=\"Set" << spec->inputs[i].name << "\" x=\"" << i*120 << "\" y=\"0\" >\n";
        std::cout << "<name>Set" << spec->inputs[i].name << "</name><urgent/></location>\n\n";
    }
    std::cout << "<init ref=\"Set" << spec->inputs[0].name << "\"/>\n";
    std::cout << "<location id=\"Update\" x=\"" << spec->num_inputs*60 << "\" y=\"200\" ></location>\n";
    for(int i = 0; i < spec->num_inputs; i++){
        std::string prev_loc;
        if (i > 0 )
            prev_loc = "Set" + std::string(spec->inputs[i-1].name);
        else 
            prev_loc = "Update";
        for (int b = 0; b <= 1; b++){
            if (std::string(spec->inputs[i].name).find("controllable") != std::string::npos){
                std::cout << "<transition controllable=\"true\">\n";
            } else {
                std::cout << "<transition controllable=\"false\">\n";
            }
                std::cout << "<source ref=\"" << prev_loc << "\"/><target ref=\"Set"
                    << spec->inputs[i].name  << "\"/>\n";
            std::cout << "<label kind=\"assignment\">"<< spec->inputs[i].name
                <<" = " << b<< "</label>\n";
            std::cout << "</transition>\n\n";
        }
    }
    std::cout << "<transition controllable=\"false\" action=\"update()\" ><source ref=\"Update\"/><target ref=\"Set" 
              << spec->inputs[0].name << "\"/>"
              << "</transition>\n";
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