#include <iostream>
#include "aiger.h"

void process_bad2out(aiger * spec){
    for(int i = 0; i < spec->num_bad; i++){
        aiger_add_output(spec, spec->bad[i].lit, spec->bad[i].name);
    }
    spec->num_bad = 0; // I know it's bad
}

int main(int argc, char ** args){
    if (argc < 3){
        std::cerr << "Usage: bad2out input.aag output.aag\n";
        return -1;
    }
    const char * infile = args[1];
    const char * outfile = args[2];
    aiger* spec = aiger_init();
    const char* err = aiger_open_and_read_from_file (spec, infile);
    if (err) {
        std::cerr << (std::string("Error ") + err +
               " encountered while reading AIGER file " +
               infile);
        return -1;
    }
    process_bad2out(spec);
    return !aiger_open_and_write_to_file(spec, outfile);
}