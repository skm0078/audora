// File: speech/src/main/cpp/brain/brain_core.cpp
// Purpose: The story brain's model on llama.cpp (ADR-078): load a GGUF, answer one prompt under a grammar, greedily.

#include "brain_core.h"

#include <cstdio>
#include <vector>

#ifdef __ANDROID__
#include <dlfcn.h>
#endif

#include "ggml-backend.h"
#include "llama.h"

#ifdef __ANDROID__
#include <android/log.h>
#define BRAIN_LOG(...) __android_log_print(ANDROID_LOG_INFO, "Audora.Brain", __VA_ARGS__)
#else
#define BRAIN_LOG(...) (std::fprintf(stderr, __VA_ARGS__), std::fputc('\n', stderr))
#endif

namespace audora {
namespace {

constexpr int kPieceBytes = 256;

/** llama.cpp's own log, kept to warnings and errors: its info lines would flood logcat on every load. */
void quietLog(ggml_log_level level, const char* text, void* /*unused*/) {
    if (level >= GGML_LOG_LEVEL_WARN) BRAIN_LOG("%s", text);
}

void fill(llama_batch_ext* batch, const llama_token* tokens, int count, llama_pos start) {
    llama_batch_ext_clear(batch);
    for (int i = 0; i < count; ++i) {
        const int32_t index = llama_batch_ext_add_token(batch, 0, tokens[i]);
        const llama_pos pos = start + i;
        llama_batch_ext_set_pos(batch, index, &pos);
    }
    llama_batch_ext_set_output_logits(batch, count - 1, true);
}

#ifdef __ANDROID__
// ggml's Android CPU variants (GGML_CPU_ALL_VARIANTS), best first. They stay inside the APK (native libraries are not
// extracted), where ggml's own folder scan cannot see them, so each is asked by name, as the app's linker finds it.
constexpr const char* kCpuVariants[] = {
    "libggml-cpu-android_armv9.2_2.so", "libggml-cpu-android_armv9.2_1.so", "libggml-cpu-android_armv9.0_1.so",
    "libggml-cpu-android_armv8.6_1.so", "libggml-cpu-android_armv8.2_2.so", "libggml-cpu-android_armv8.2_1.so",
    "libggml-cpu-android_armv8.0_1.so",
};

using ScoreFn = int (*)();

/** The variant this phone's cores run best: the highest score (0 means it needs an instruction they lack). */
const char* bestCpuVariant() {
    const char* best = nullptr;
    int bestScore = 0;
    for (const char* name : kCpuVariants) {
        void* handle = dlopen(name, RTLD_NOW | RTLD_LOCAL);
        if (handle == nullptr) continue;
        auto score = reinterpret_cast<ScoreFn>(dlsym(handle, "ggml_backend_score"));
        const int value = score == nullptr ? 0 : score();
        dlclose(handle);
        if (value > bestScore) {
            bestScore = value;
            best = name;
        }
    }
    return best;
}
#endif

}  // namespace

bool initBackends() {
    llama_log_set(quietLog, nullptr);
#ifdef __ANDROID__
    const char* variant = bestCpuVariant();
    if (variant == nullptr || ggml_backend_load(variant) == nullptr) {
        BRAIN_LOG("no CPU variant of ggml loads on this phone");
        return false;
    }
    BRAIN_LOG("ggml CPU variant %s", variant);
#endif
    llama_backend_init();
    return true;
}

Brain* Brain::load(const std::string& path, int contextTokens, int threads) {
    llama_model_params modelParams = llama_model_default_params();
    modelParams.n_gpu_layers = 0;
    llama_model* model = llama_model_load_from_file(path.c_str(), modelParams);
    if (model == nullptr) {
        BRAIN_LOG("model did not load: %s", path.c_str());
        return nullptr;
    }
    llama_context_params params = llama_context_default_params();
    params.n_ctx = static_cast<uint32_t>(contextTokens);
    params.n_batch = static_cast<uint32_t>(contextTokens);
    params.n_ubatch = 512;
    params.n_threads = threads;
    params.n_threads_batch = threads;
    params.no_perf = true;
    llama_context* context = llama_init_from_model(model, params);
    if (context == nullptr) {
        BRAIN_LOG("no context of %d tokens", contextTokens);
        llama_model_free(model);
        return nullptr;
    }
    auto* brain = new Brain(model, context, contextTokens);
    llama_set_abort_callback(context, &Brain::aborted, brain);
    return brain;
}

Brain::Brain(llama_model* model, llama_context* context, int contextTokens)
    : model_(model), context_(context), contextTokens_(contextTokens) {}

Brain::~Brain() {
    llama_free(context_);
    llama_model_free(model_);
}

bool Brain::aborted(void* self) { return static_cast<Brain*>(self)->cancelled_.load(); }

int Brain::countTokens(const std::string& text) const {
    const llama_vocab* vocab = llama_model_get_vocab(model_);
    return -llama_tokenize(vocab, text.c_str(), static_cast<int32_t>(text.size()), nullptr, 0, true, true);
}

bool Brain::answer(const std::string& prompt, const std::string& grammar, int maxTokens, std::string& out) {
    out.clear();
    cancelled_.store(false);
    const llama_vocab* vocab = llama_model_get_vocab(model_);
    const int promptTokens = countTokens(prompt);
    if (promptTokens <= 0 || promptTokens + maxTokens > contextTokens_) {
        BRAIN_LOG("prompt of %d tokens does not fit %d with %d to answer", promptTokens, contextTokens_, maxTokens);
        return false;
    }
    std::vector<llama_token> tokens(static_cast<size_t>(promptTokens));
    llama_tokenize(vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()), tokens.data(), promptTokens, true, true);

    llama_memory_clear(llama_get_memory(context_), true);
    llama_sampler* sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler* constrained = llama_sampler_init_grammar(vocab, grammar.c_str(), "root");
    if (constrained == nullptr) {
        BRAIN_LOG("grammar did not parse");
        llama_sampler_free(sampler);
        return false;
    }
    llama_sampler_chain_add(sampler, constrained);
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    llama_batch_ext* batch = llama_batch_ext_init(context_);

    bool finished = false;
    bool failed = false;
    llama_pos position = 0;
    int count = promptTokens;
    fill(batch, tokens.data(), count, position);
    for (int produced = 0; produced < maxTokens; ++produced) {
        if (llama_process(context_, LLAMA_PROCESS_TYPE_DECODE, batch) != 0) {
            failed = true;
            break;
        }
        position += count;
        llama_token next = llama_sampler_sample(sampler, context_, -1);
        if (llama_vocab_is_eog(vocab, next)) {
            finished = true;
            break;
        }
        char piece[kPieceBytes];
        const int length = llama_token_to_piece(vocab, next, piece, sizeof(piece), 0, false);
        if (length < 0) {
            failed = true;
            break;
        }
        out.append(piece, static_cast<size_t>(length));
        fill(batch, &next, 1, position);
        count = 1;
    }
    llama_batch_ext_free(batch);
    llama_sampler_free(sampler);
    if (cancelled_.load()) BRAIN_LOG("answer cancelled after %zu bytes", out.size());
    return finished && !failed && !cancelled_.load();
}

}  // namespace audora
