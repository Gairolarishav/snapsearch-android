# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep ONNX Runtime classes (will be expanded in Phase 1 when ONNX is integrated)
# -keep class ai.onnxruntime.** { *; }

# Keep ML Kit classes
# -keep class com.google.mlkit.** { *; }

# For Room database entities
# -keep class com.snapsearch.data.** { *; }
