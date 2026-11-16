# The accessibility service and activities are referenced from the manifest, which R8 keeps automatically.

# Shrink, but keep real class and method names and line numbers so crash reports are readable.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
