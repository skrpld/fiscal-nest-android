# kotlinx.serialization: keep the generated serializers of the app's @Serializable classes
# (stored data, backups and navigation routes). The library ships rules for its own classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers @kotlinx.serialization.Serializable class io.github.skrpld.fiscalnest.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

-keepclasseswithmembers class io.github.skrpld.fiscalnest.** {
    kotlinx.serialization.KSerializer serializer(...);
}
