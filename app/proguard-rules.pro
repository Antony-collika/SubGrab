## Rules for NewPipeExtractor
   -keep class org.schabi.newpipe.extractor.** { *; }
   -keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }

   ## Rules for Rhino and Rhino Engine (used by Extractor)
   -keep class org.mozilla.javascript.** { *; }
   -keep class org.mozilla.classfile.ClassFileWriter
   -dontwarn org.mozilla.javascript.**
   -dontwarn org.mozilla.classfile.**