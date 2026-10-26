// File: speech/src/main/cpp/brain/brain_core.h
// Purpose: The story brain's model on llama.cpp (ADR 2026-10-25-story-brain): load a GGUF, answer one prompt under a grammar, greedily.

#pragma once

#include <atomic>
#include <string>

struct llama_model;
struct llama_context;

namespace audora {

/** One loaded model and its context. Not thread-safe: one answer at a time; cancel() may come from any thread. */
class Brain {
public:
    /** Null when the file does not load or the context cannot be made (the reason is in the log). */
    static Brain* load(const std::string& path, int contextTokens, int threads);

    ~Brain();
    Brain(const Brain&) = delete;
    Brain& operator=(const Brain&) = delete;

    /**
     * The model's answer to [prompt] (already in the model's chat format), constrained by the GBNF [grammar], at most
     * [maxTokens] long. False when the prompt does not fit, decoding fails, or cancel() stopped it; [out] then holds
     * what came so far.
     */
    bool answer(const std::string& prompt, const std::string& grammar, int maxTokens, std::string& out);

    /** Stops the answer in progress at its next token; the next answer() clears it. */
    void cancel() { cancelled_.store(true); }

    /** Tokens in [text] as the model counts them, for fitting pages into the context. -1 on failure. */
    int countTokens(const std::string& text) const;

    int contextTokens() const { return contextTokens_; }

private:
    Brain(llama_model* model, llama_context* context, int contextTokens);
    static bool aborted(void* self);

    llama_model* model_;
    llama_context* context_;
    int contextTokens_;
    std::atomic<bool> cancelled_{false};
};

/** Once per process, before load(): picks ggml's CPU variant for this phone. False when none runs here. */
bool initBackends();

}  // namespace audora
