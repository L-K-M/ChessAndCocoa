# JNI entry points are looked up by name from native code.
-keep class ch.lkmc.chessandcocoa.nativeengine.** { native <methods>; *; }
