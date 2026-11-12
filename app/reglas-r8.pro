# Reglas de R8 de la app. R8 solo recorta el código de las librerías que no se usa.
# Sin ofuscar: los nombres del APK son los del código fuente, para poder comparar APK y repositorio.
-dontobfuscate

# Las actividades y receptores los abre Android por nombre, desde el manifiesto.
-keep class cl.itatagroup.canales.** { *; }

# Anotaciones que solo existen al compilar (no van en el APK).
-dontwarn androidx.annotation.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**
-dontwarn kotlin.**
