# ONNX Runtime C API headers

`onnxruntime_c_api.h`, `onnxruntime_ep_c_api.h` and `onnxruntime_error_code.h`, unchanged from ONNX Runtime v1.28.0
(`include/onnxruntime/core/session/`), Copyright (c) Microsoft Corporation, MIT licence
(https://github.com/microsoft/onnxruntime/blob/v1.28.0/LICENSE).

Only the declarations are used: `libaudora_acted.so` links no ONNX Runtime library. It looks up `OrtGetApiBase` at
runtime in the `libonnxruntime.so` that sherpa-onnx's AAR already ships (ADR-059), so the APK carries one runtime.
