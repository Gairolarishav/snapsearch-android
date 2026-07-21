# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# ObjectBox's own bundled consumer rules (objectbox-java.pro) keep Cursor/DbException/
# @Entity classes but not io.objectbox.query.ObjectWithScore, which nearestNeighbors()'s
# findWithScores() constructs via reflection from native code — without this, HNSW vector
# search (the core of this app's search) throws "ObjectWithScore class not found" at runtime
# on a minified build even though it compiles and the app launches fine. Keep the whole
# package rather than chase individual classes ObjectBox's native side reflects into.
-keep class io.objectbox.** { *; }
-dontwarn io.objectbox.**

# ONNX Runtime Mobile — native/JNI bindings resolve Java classes by name.
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ML Kit Text Recognition — model loading + Play Services Task callbacks use reflection.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.mlkit.**

# App's own ObjectBox entities/generated classes (ImageEntity_, MyObjectBox, Cursor subclasses).
-keep class com.snapsearch.data.** { *; }
