// File: speech/src/main/cpp/chatterbox.h
// Purpose: Chatterbox Multilingual (Resemble AI, MIT) on ONNX Runtime: speech tokens from text tokens, then audio
//          (ADR-059). The token loop stays native so the language model's cache never crosses JNI.

#pragma once

#include <atomic>
#include <cstdint>
#include <memory>
#include <string>
#include <vector>

#include "ort_api.h"

namespace audora {

// One tensor of the voice file, row-major.
struct HostTensor {
    std::vector<int64_t> shape;
    std::vector<float> floats;   // when the tensor is float32
    std::vector<int64_t> longs;  // when it is int64
};

// The reference voice, encoded once by the export's speech encoder (scripts/build_acted_pack.py writes voice.bin),
// so the phone needs neither the encoder nor the reference recording.
struct ActedVoice {
    HostTensor cond_emb;            // [1, frames, 1024]: the language model's voice conditioning
    HostTensor prompt_token;        // [1, prompt tokens]: the reference's speech tokens, which the decoder continues
    HostTensor speaker_embeddings;  // [1, 192]
    HostTensor speaker_features;    // [1, mel frames, 80]
    HostTensor decoder_tail;        // optional [1, n]: tokens the decoder needs after the speech (Turbo's silence)
};

// Reads voice.bin: "ACTVOIC1", then the four tensors in ActedVoice's order (and the optional fifth), each as int32 type (1 float32, 7 int64,
// ONNX's numbering), int32 rank, int64 dims, then the data, all little-endian. False with [error] if malformed.
bool ReadActedVoice(const std::string& path, ActedVoice* voice, std::string* error);

struct Generation {
    std::vector<int64_t> speech_tokens;
    bool stopped = false;    // the model ended the speech itself; false = it reached max_tokens (a runaway)
    bool cancelled = false;
    std::string error;       // empty on success
};

class Chatterbox {
public:
    // [dir] holds embed_tokens.onnx, language_model.onnx, conditional_decoder.onnx (each with its _data file) and
    // voice.bin. Null with [error] on failure.
    static std::unique_ptr<Chatterbox> Load(const OrtApi* api, const std::string& dir, int threads, std::string* error);

    ~Chatterbox();
    Chatterbox(const Chatterbox&) = delete;
    Chatterbox& operator=(const Chatterbox&) = delete;

    // Greedy decoding with a repetition penalty, as the export's reference script does. [text_tokens] come from the
    // tokenizer with its template (exaggeration slot, start and stop text tokens, two start-of-speech tokens).
    Generation Speak(const std::vector<int64_t>& text_tokens, float exaggeration, int max_tokens,
                     float repetition_penalty);

    // 24 kHz mono audio for [speech_tokens], after the reference's own (which the decoder leaves out).
    bool Decode(const std::vector<int64_t>& speech_tokens, std::vector<float>* audio, std::string* error);

    // From any thread: the running Speak or Decode stops soon and reports cancelled (or a cancel error).
    void Cancel();

private:
    Chatterbox(const OrtApi* api) : api_(api) {}

    struct Value;
    bool Embed(const std::vector<int64_t>& ids, const std::vector<int64_t>& positions, float exaggeration,
               std::vector<float>* out, int64_t* rows, std::string* error);

    const OrtApi* api_;
    OrtEnv* env_ = nullptr;
    OrtSessionOptions* options_ = nullptr;
    OrtSession* embed_ = nullptr;
    OrtSession* lm_ = nullptr;
    OrtSession* decoder_ = nullptr;
    OrtMemoryInfo* memory_ = nullptr;
    OrtRunOptions* run_options_ = nullptr;
    OrtAllocator* allocator_ = nullptr;
    ActedVoice voice_;
    std::vector<std::string> past_names_;     // the language model's cache inputs, in input order
    std::vector<std::string> present_names_;  // the cache output feeding each of them
    int64_t kv_heads_ = 0;
    int64_t head_dim_ = 0;
    int64_t hidden_ = 0;
    std::atomic<bool> cancelled_{false};
};

}  // namespace audora
