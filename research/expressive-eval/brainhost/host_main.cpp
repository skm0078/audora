// File: research/expressive-eval/brainhost/host_main.cpp
// Purpose: Runs the app's story brain once on a PC: brainhost <model.gguf> <prompt file> <grammar file> [max tokens].
//          Prints the answer, then "--- ok=<0|1> ms=<time> prompt_tokens=<n>" for llm_director.py.
#include <chrono>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <sstream>

#include "brain_core.h"

static std::string slurp(const char* path) {
    std::ifstream file(path);
    std::stringstream text;
    text << file.rdbuf();
    return text.str();
}

int main(int argc, char** argv) {
    if (argc < 4) {
        std::cerr << "usage: brainhost <model.gguf> <prompt> <grammar> [max tokens]" << std::endl;
        return 2;
    }
    audora::initBackends();
    auto* brain = audora::Brain::load(argv[1], 4096, 4);
    if (!brain) return 1;
    const std::string prompt = slurp(argv[2]);
    std::string out;
    const auto started = std::chrono::steady_clock::now();
    const bool ok = brain->answer(prompt, slurp(argv[3]), argc > 4 ? std::atoi(argv[4]) : 400, out);
    const auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - started);
    std::cout << out << "\n--- ok=" << ok << " ms=" << ms.count() << " prompt_tokens=" << brain->countTokens(prompt)
              << std::endl;
    delete brain;
    return ok ? 0 : 1;
}
