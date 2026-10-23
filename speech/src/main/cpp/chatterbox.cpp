// File: speech/src/main/cpp/chatterbox.cpp
// Purpose: Chatterbox's token loop and decoder on ONNX Runtime's C API (ADR-059).

#include "chatterbox.h"

#include <algorithm>
#include <cstring>
#include <fstream>
#include <iterator>
#include <utility>

namespace audora {
namespace {

constexpr int64_t kStartSpeech = 6561;
constexpr int64_t kStopSpeech = 6562;
constexpr char kVoiceMagic[] = "ACTVOIC1";
constexpr int32_t kOnnxFloat = 1;
constexpr int32_t kOnnxInt64 = 7;
constexpr int32_t kMaxRank = 4;

template <typename T>
bool ReadRaw(const std::vector<char>& bytes, size_t* at, T* out, size_t count) {
    size_t size = sizeof(T) * count;
    if (*at + size > bytes.size()) return false;
    std::memcpy(out, bytes.data() + *at, size);
    *at += size;
    return true;
}

bool ReadTensor(const std::vector<char>& bytes, size_t* at, HostTensor* tensor) {
    int32_t type = 0;
    int32_t rank = 0;
    if (!ReadRaw(bytes, at, &type, 1) || !ReadRaw(bytes, at, &rank, 1) || rank < 1 || rank > kMaxRank) return false;
    tensor->shape.resize(rank);
    if (!ReadRaw(bytes, at, tensor->shape.data(), rank)) return false;
    int64_t count = 1;
    for (int64_t dim : tensor->shape) {
        if (dim < 0) return false;
        count *= dim;
    }
    if (type == kOnnxFloat) {
        tensor->floats.resize(count);
        return ReadRaw(bytes, at, tensor->floats.data(), count);
    }
    if (type == kOnnxInt64) {
        tensor->longs.resize(count);
        return ReadRaw(bytes, at, tensor->longs.data(), count);
    }
    return false;
}

}  // namespace

bool ReadActedVoice(const std::string& path, ActedVoice* voice, std::string* error) {
    std::ifstream in(path, std::ios::binary);
    if (!in) {
        *error = "cannot open " + path;
        return false;
    }
    std::vector<char> bytes((std::istreambuf_iterator<char>(in)), std::istreambuf_iterator<char>());
    size_t at = sizeof(kVoiceMagic) - 1;
    bool ok = bytes.size() >= at && std::memcmp(bytes.data(), kVoiceMagic, at) == 0 &&
              ReadTensor(bytes, &at, &voice->cond_emb) && ReadTensor(bytes, &at, &voice->prompt_token) &&
              ReadTensor(bytes, &at, &voice->speaker_embeddings) && ReadTensor(bytes, &at, &voice->speaker_features) &&
              at == bytes.size();
    ok = ok && voice->cond_emb.shape.size() == 3 && !voice->cond_emb.floats.empty() &&
         !voice->prompt_token.longs.empty() && !voice->speaker_embeddings.floats.empty() &&
         !voice->speaker_features.floats.empty();
    if (!ok) *error = path + " is not a valid voice file";
    return ok;
}

// An OrtValue released with its owner. Tensors made from host data must not outlive that data.
struct Chatterbox::Value {
    const OrtApi* api = nullptr;
    OrtValue* value = nullptr;

    Value() = default;
    Value(const OrtApi* a, OrtValue* v) : api(a), value(v) {}
    Value(Value&& other) noexcept : api(other.api), value(std::exchange(other.value, nullptr)) {}
    Value& operator=(Value&& other) noexcept {
        if (this != &other) {
            Reset();
            api = other.api;
            value = std::exchange(other.value, nullptr);
        }
        return *this;
    }
    Value(const Value&) = delete;
    Value& operator=(const Value&) = delete;
    ~Value() { Reset(); }

    void Reset() {
        if (value != nullptr) api->ReleaseValue(value);
        value = nullptr;
    }
};

#define AUDORA_ORT(call)                                    \
    do {                                                    \
        std::string failure = TakeError(api_, (call));      \
        if (!failure.empty()) {                             \
            *error = failure;                               \
            return false;                                   \
        }                                                   \
    } while (false)

namespace {

template <typename T>
OrtStatus* Wrap(const OrtApi* api, OrtMemoryInfo* memory, T* data, size_t count, const std::vector<int64_t>& shape,
                ONNXTensorElementDataType type, OrtValue** out) {
    return api->CreateTensorWithDataAsOrtValue(memory, data, count * sizeof(T), shape.data(), shape.size(), type, out);
}

// The names of a session's inputs or outputs, in order.
std::vector<std::string> Names(const OrtApi* api, OrtSession* session, OrtAllocator* allocator, bool inputs,
                               std::string* error) {
    size_t count = 0;
    *error = TakeError(api, inputs ? api->SessionGetInputCount(session, &count)
                                   : api->SessionGetOutputCount(session, &count));
    std::vector<std::string> names;
    for (size_t i = 0; i < count && error->empty(); ++i) {
        char* name = nullptr;
        *error = TakeError(api, inputs ? api->SessionGetInputName(session, i, allocator, &name)
                                       : api->SessionGetOutputName(session, i, allocator, &name));
        if (name != nullptr) {
            names.emplace_back(name);
            // Freeing a name the allocator handed out cannot fail in practice; its status is released all the same.
            TakeError(api, api->AllocatorFree(allocator, name));
        }
    }
    return names;
}

// Input [index]'s dimensions; a symbolic dimension reads -1.
std::vector<int64_t> InputDims(const OrtApi* api, OrtSession* session, size_t index, std::string* error) {
    OrtTypeInfo* info = nullptr;
    *error = TakeError(api, api->SessionGetInputTypeInfo(session, index, &info));
    std::vector<int64_t> dims;
    const OrtTensorTypeAndShapeInfo* tensor = nullptr;
    if (error->empty()) *error = TakeError(api, api->CastTypeInfoToTensorInfo(info, &tensor));
    size_t rank = 0;
    if (error->empty()) *error = TakeError(api, api->GetDimensionsCount(tensor, &rank));
    if (error->empty()) {
        dims.resize(rank);
        *error = TakeError(api, api->GetDimensions(tensor, dims.data(), rank));
    }
    if (info != nullptr) api->ReleaseTypeInfo(info);
    return dims;
}

std::vector<int64_t> Shape(const OrtApi* api, const OrtValue* value, std::string* error) {
    OrtTensorTypeAndShapeInfo* info = nullptr;
    *error = TakeError(api, api->GetTensorTypeAndShape(value, &info));
    std::vector<int64_t> dims;
    size_t rank = 0;
    if (error->empty()) *error = TakeError(api, api->GetDimensionsCount(info, &rank));
    if (error->empty()) {
        dims.resize(rank);
        *error = TakeError(api, api->GetDimensions(info, dims.data(), rank));
    }
    if (info != nullptr) api->ReleaseTensorTypeAndShapeInfo(info);
    return dims;
}

}  // namespace

std::unique_ptr<Chatterbox> Chatterbox::Load(const OrtApi* api, const std::string& dir, int threads,
                                             std::string* error) {
    std::unique_ptr<Chatterbox> model(new Chatterbox(api));
    auto load = [&]() -> bool {
        const OrtApi* api_ = api;
        if (!ReadActedVoice(dir + "/voice.bin", &model->voice_, error)) return false;
        AUDORA_ORT(api_->CreateEnv(ORT_LOGGING_LEVEL_WARNING, "audora-acted", &model->env_));
        AUDORA_ORT(api_->CreateSessionOptions(&model->options_));
        AUDORA_ORT(api_->SetIntraOpNumThreads(model->options_, threads));
        AUDORA_ORT(api_->SetInterOpNumThreads(model->options_, 1));
        AUDORA_ORT(api_->SetSessionGraphOptimizationLevel(model->options_, ORT_ENABLE_ALL));
        AUDORA_ORT(api_->CreateSession(model->env_, (dir + "/embed_tokens.onnx").c_str(), model->options_,
                                       &model->embed_));
        AUDORA_ORT(api_->CreateSession(model->env_, (dir + "/language_model.onnx").c_str(), model->options_,
                                       &model->lm_));
        AUDORA_ORT(api_->CreateSession(model->env_, (dir + "/conditional_decoder.onnx").c_str(), model->options_,
                                       &model->decoder_));
        AUDORA_ORT(api_->CreateCpuMemoryInfo(OrtArenaAllocator, OrtMemTypeDefault, &model->memory_));
        AUDORA_ORT(api_->CreateRunOptions(&model->run_options_));
        AUDORA_ORT(api_->GetAllocatorWithDefaultOptions(&model->allocator_));

        std::vector<std::string> inputs = Names(api, model->lm_, model->allocator_, true, error);
        if (!error->empty()) return false;
        std::vector<std::string> outputs = Names(api, model->lm_, model->allocator_, false, error);
        if (!error->empty()) return false;
        const std::string past = "past_key_values.";
        for (size_t i = 0; i < inputs.size(); ++i) {
            if (inputs[i].rfind(past, 0) != 0) continue;
            std::string present = "present." + inputs[i].substr(past.size());
            if (std::find(outputs.begin(), outputs.end(), present) == outputs.end()) {
                *error = "language model has no output " + present;
                return false;
            }
            if (model->past_names_.empty()) {
                std::vector<int64_t> dims = InputDims(api, model->lm_, i, error);
                if (!error->empty()) return false;
                if (dims.size() != 4 || dims[1] <= 0 || dims[3] <= 0) {
                    *error = "unexpected cache shape for " + inputs[i];
                    return false;
                }
                model->kv_heads_ = dims[1];
                model->head_dim_ = dims[3];
            }
            model->past_names_.push_back(inputs[i]);
            model->present_names_.push_back(present);
        }
        model->hidden_ = model->voice_.cond_emb.shape[2];
        if (model->past_names_.empty()) {
            *error = "language model has no cache inputs";
            return false;
        }
        return true;
    };
    if (!load()) return nullptr;
    return model;
}

Chatterbox::~Chatterbox() {
    if (run_options_ != nullptr) api_->ReleaseRunOptions(run_options_);
    if (memory_ != nullptr) api_->ReleaseMemoryInfo(memory_);
    if (decoder_ != nullptr) api_->ReleaseSession(decoder_);
    if (lm_ != nullptr) api_->ReleaseSession(lm_);
    if (embed_ != nullptr) api_->ReleaseSession(embed_);
    if (options_ != nullptr) api_->ReleaseSessionOptions(options_);
    if (env_ != nullptr) api_->ReleaseEnv(env_);
}

void Chatterbox::Cancel() {
    cancelled_ = true;
    TakeError(api_, api_->RunOptionsSetTerminate(run_options_));
}

bool Chatterbox::Embed(const std::vector<int64_t>& ids, const std::vector<int64_t>& positions, float exaggeration,
                       std::vector<float>* out, int64_t* rows, std::string* error) {
    std::vector<int64_t> id_data = ids;
    std::vector<int64_t> position_data = positions;
    float strength = exaggeration;
    std::vector<int64_t> shape = {1, static_cast<int64_t>(ids.size())};
    std::vector<int64_t> one = {1};
    Value id_value(api_, nullptr);
    Value position_value(api_, nullptr);
    Value strength_value(api_, nullptr);
    AUDORA_ORT(Wrap(api_, memory_, id_data.data(), id_data.size(), shape, ONNX_TENSOR_ELEMENT_DATA_TYPE_INT64,
                    &id_value.value));
    AUDORA_ORT(Wrap(api_, memory_, position_data.data(), position_data.size(), shape,
                    ONNX_TENSOR_ELEMENT_DATA_TYPE_INT64, &position_value.value));
    AUDORA_ORT(Wrap(api_, memory_, &strength, 1, one, ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT, &strength_value.value));
    const char* in_names[] = {"input_ids", "position_ids", "exaggeration"};
    const OrtValue* in_values[] = {id_value.value, position_value.value, strength_value.value};
    const char* out_names[] = {"inputs_embeds"};
    Value embeds(api_, nullptr);
    AUDORA_ORT(api_->Run(embed_, run_options_, in_names, in_values, 3, out_names, 1, &embeds.value));
    std::vector<int64_t> dims = Shape(api_, embeds.value, error);
    if (!error->empty()) return false;
    if (dims.size() != 3 || dims[2] != hidden_) {
        *error = "unexpected embedding shape";
        return false;
    }
    float* data = nullptr;
    AUDORA_ORT(api_->GetTensorMutableData(embeds.value, reinterpret_cast<void**>(&data)));
    out->assign(data, data + dims[1] * dims[2]);
    *rows = dims[1];
    return true;
}

Generation Chatterbox::Speak(const std::vector<int64_t>& text_tokens, float exaggeration, int max_tokens,
                             float repetition_penalty) {
    Generation result;
    std::string* error = &result.error;
    cancelled_ = false;
    TakeError(api_, api_->RunOptionsUnsetTerminate(run_options_));
    auto fail = [&](bool ok) {
        if (!ok && cancelled_) {
            result.cancelled = true;
            result.error.clear();
        }
        return result;
    };

    std::vector<int64_t> positions(text_tokens.size());
    for (size_t i = 0; i < text_tokens.size(); ++i) {
        positions[i] = text_tokens[i] >= kStartSpeech ? 0 : static_cast<int64_t>(i) - 1;
    }
    std::vector<float> step_embeds;
    int64_t rows = 0;
    if (!Embed(text_tokens, positions, exaggeration, &step_embeds, &rows, error)) return fail(false);
    // The first step reads the voice conditioning, then the text.
    std::vector<float> embeds = voice_.cond_emb.floats;
    embeds.insert(embeds.end(), step_embeds.begin(), step_embeds.end());
    rows += voice_.cond_emb.shape[1];
    int64_t total = rows;

    std::vector<Value> past;
    std::vector<int64_t> empty_cache = {1, kv_heads_, 0, head_dim_};
    for (size_t i = 0; i < past_names_.size(); ++i) {
        Value cache(api_, nullptr);
        if (!TakeError(api_, api_->CreateTensorAsOrtValue(allocator_, empty_cache.data(), empty_cache.size(),
                                                          ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT, &cache.value))
                 .empty()) {
            result.error = "cannot create the language model's cache";
            return result;
        }
        past.push_back(std::move(cache));
    }
    std::vector<const char*> in_names = {"inputs_embeds", "attention_mask"};
    std::vector<const char*> out_names = {"logits"};
    for (size_t i = 0; i < past_names_.size(); ++i) {
        in_names.push_back(past_names_[i].c_str());
        out_names.push_back(present_names_[i].c_str());
    }

    std::vector<char> seen;
    std::vector<int64_t> mask;
    std::vector<float> scores;
    for (int step = 0; step < max_tokens; ++step) {
        if (cancelled_) return fail(false);
        mask.assign(total, 1);
        std::vector<int64_t> embed_shape = {1, rows, hidden_};
        std::vector<int64_t> mask_shape = {1, total};
        Value embed_value(api_, nullptr);
        Value mask_value(api_, nullptr);
        std::string failure = TakeError(api_, Wrap(api_, memory_, embeds.data(), embeds.size(), embed_shape,
                                                   ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT, &embed_value.value));
        if (failure.empty()) {
            failure = TakeError(api_, Wrap(api_, memory_, mask.data(), mask.size(), mask_shape,
                                           ONNX_TENSOR_ELEMENT_DATA_TYPE_INT64, &mask_value.value));
        }
        std::vector<const OrtValue*> in_values = {embed_value.value, mask_value.value};
        for (const Value& cache : past) in_values.push_back(cache.value);
        std::vector<OrtValue*> out_values(out_names.size(), nullptr);
        if (failure.empty()) {
            failure = TakeError(api_, api_->Run(lm_, run_options_, in_names.data(), in_values.data(),
                                                in_values.size(), out_names.data(), out_names.size(),
                                                out_values.data()));
        }
        std::vector<Value> outputs;
        for (OrtValue* value : out_values) outputs.emplace_back(api_, value);
        if (!failure.empty()) {
            result.error = failure;
            return fail(false);
        }

        std::vector<int64_t> dims = Shape(api_, outputs[0].value, error);
        if (!error->empty()) return fail(false);
        if (dims.size() != 3 || dims[1] < 1) {
            result.error = "unexpected logits shape";
            return result;
        }
        int64_t vocab = dims[2];
        float* logits = nullptr;
        failure = TakeError(api_, api_->GetTensorMutableData(outputs[0].value, reinterpret_cast<void**>(&logits)));
        if (!failure.empty()) {
            result.error = failure;
            return result;
        }
        if (seen.empty()) {
            seen.assign(vocab, 0);
            if (kStartSpeech < vocab) seen[kStartSpeech] = 1;
        }
        scores.assign(logits + (dims[1] - 1) * vocab, logits + dims[1] * vocab);
        int64_t best = 0;
        for (int64_t token = 0; token < vocab; ++token) {
            if (seen[token]) {
                float& score = scores[token];
                score = score < 0 ? score * repetition_penalty : score / repetition_penalty;
            }
            if (scores[token] > scores[best]) best = token;
        }
        if (best == kStopSpeech) {
            result.stopped = true;
            break;
        }
        result.speech_tokens.push_back(best);
        seen[best] = 1;
        for (size_t i = 0; i < past.size(); ++i) past[i] = std::move(outputs[i + 1]);

        std::vector<int64_t> next = {best};
        std::vector<int64_t> next_position = {step + 1};
        if (!Embed(next, next_position, exaggeration, &embeds, &rows, error)) return fail(false);
        total += rows;
    }
    return result;
}

bool Chatterbox::Decode(const std::vector<int64_t>& speech_tokens, std::vector<float>* audio, std::string* error) {
    cancelled_ = false;
    TakeError(api_, api_->RunOptionsUnsetTerminate(run_options_));
    std::vector<int64_t> tokens = voice_.prompt_token.longs;
    tokens.insert(tokens.end(), speech_tokens.begin(), speech_tokens.end());
    std::vector<int64_t> token_shape = {1, static_cast<int64_t>(tokens.size())};
    Value token_value(api_, nullptr);
    Value embedding_value(api_, nullptr);
    Value feature_value(api_, nullptr);
    AUDORA_ORT(Wrap(api_, memory_, tokens.data(), tokens.size(), token_shape, ONNX_TENSOR_ELEMENT_DATA_TYPE_INT64,
                    &token_value.value));
    AUDORA_ORT(Wrap(api_, memory_, voice_.speaker_embeddings.floats.data(), voice_.speaker_embeddings.floats.size(),
                    voice_.speaker_embeddings.shape, ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT, &embedding_value.value));
    AUDORA_ORT(Wrap(api_, memory_, voice_.speaker_features.floats.data(), voice_.speaker_features.floats.size(),
                    voice_.speaker_features.shape, ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT, &feature_value.value));
    const char* in_names[] = {"speech_tokens", "speaker_embeddings", "speaker_features"};
    const OrtValue* in_values[] = {token_value.value, embedding_value.value, feature_value.value};
    const char* out_names[] = {"waveform"};
    Value waveform(api_, nullptr);
    std::string failure =
        TakeError(api_, api_->Run(decoder_, run_options_, in_names, in_values, 3, out_names, 1, &waveform.value));
    if (!failure.empty()) {
        *error = cancelled_ ? "cancelled" : failure;
        return false;
    }
    std::vector<int64_t> dims = Shape(api_, waveform.value, error);
    if (!error->empty()) return false;
    int64_t samples = 1;
    for (int64_t dim : dims) samples *= dim;
    float* data = nullptr;
    AUDORA_ORT(api_->GetTensorMutableData(waveform.value, reinterpret_cast<void**>(&data)));
    audio->assign(data, data + samples);
    return true;
}

#undef AUDORA_ORT

}  // namespace audora
