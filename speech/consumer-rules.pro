# File: speech/consumer-rules.pro
# Purpose: Keeps sherpa-onnx's Kotlin classes intact if the app ever enables R8: its JNI code reads config fields and
#          builds GeneratedAudio by name, and its AAR ships no keep rules of its own.
-keep class com.k2fsa.sherpa.onnx.** { *; }
# sherpa-onnx's JNI calls invoke([F)Ljava/lang/Integer; on the generation callback by name.
-keep class io.github.skm0078.audora.speech.sherpa.SherpaStopCallback { *; }
