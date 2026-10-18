# CyberGrip Glitch v1.4 — progetto Android Studio

Apri **la cartella CyberGripGlitch_v1.4** con Android Studio (File > Open), non il singolo file Kotlin.

## SDK
È incluso `local.properties` con `sdk.dir=/Users/poz/Library/Android/sdk`, il percorso ipotizzato dalla documentazione del progetto originale. **Non è stato verificato sul tuo Mac.**
Se Android Studio segnala ancora SDK location not found, vai in Android Studio > Settings/Preferences > Languages & Frameworks > Android SDK e copia il percorso reale in `local.properties` dopo `sdk.dir=`.

## Compilazione
Sync Project with Gradle Files, poi Run su Galaxy S24 Ultra.
Android Studio può chiedere di configurare Gradle: il progetto originale non conteneva Gradle Wrapper e questa copia non lo inventa. Richiede Android SDK Platform 35, JDK 17 e Android Gradle Plugin 8.7.3.

## Funzioni conservate
22 effetti glitch, Film Looks, Film Lab richiudibile, preset P1/P2/P3, foto e video.

**Limite verifica:** non è stato possibile eseguire una compilazione Android in questo ambiente, quindi non è garantita l'assenza di ulteriori errori di compilazione.
