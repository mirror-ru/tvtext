-keep class com.tvtext.mobile.app.** { *; }
# Retain the runtime ABI for a separately compiled Android instrumentation module.
# Shrinking still removes unused Kotlin classes and methods.
-dontoptimize
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
