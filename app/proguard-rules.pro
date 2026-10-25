# File: app/proguard-rules.pro
# Purpose: R8 rules for the release and dev APKs (ADR-072). R8 shrinks and optimises; it does not rename.

# Names stay as written: shared logs, crash stacks and the class names they print read as in the code. Renaming
# would save little next to the voice files and is the riskiest part of R8 for native code that looks classes up.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# Native code reaches into these by name (JNI FindClass / GetFieldID / callbacks), which R8 cannot see.
# sherpa-onnx and our own callback are kept by speech/consumer-rules.pro.
-keep class io.legere.pdfiumandroid.** { *; }
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
-keep class io.github.skm0078.audora.pdf.pdfium.PdfiumExtNative { *; }
-keep class io.github.skm0078.audora.speech.acted.ChatterboxNative { *; }
-keep class io.github.skm0078.audora.speech.acted.ChatterboxNative$* { *; }

# Annotation-only libraries some dependencies reference but do not ship at runtime.
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
