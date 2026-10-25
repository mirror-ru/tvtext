# Keep Android entry points, callbacks, protocol classes and generated app lambdas.
# Remove unused Kotlin library code without changing application names or members.
-keep class com.tvtext.app.** { *; }
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
