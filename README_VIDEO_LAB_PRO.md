# CyberGrip GLITCH v1.5 VIDEO LAB PRO

Progetto completo basato sulla v1.4 FIX INSTALL. Mantiene FX, Film Lab, preset, foto e registrazione.

## Novità
- Menu VIDEO SETTINGS richiudibile
- Output H.264 MP4 selezionabile 1920×1080 oppure 3840×2160
- Bitrate selezionabile 12 / 20 / 30 Mbps
- Ritaglio centrale 16:9, senza deformare i fotogrammi 4:3
- VersionCode 1000001 per aggiornamento della v1.4

## Limiti importanti
La sorgente effetti è ancora l'analisi CameraX a 960×720: i file 1080p e 4K sono **upscalati**, non riprese native a tali risoluzioni. La pipeline bitmap + conversione YUV è CPU-intensive, soprattutto a 4K: fluidità e 30 fps non sono garantiti. Il frame rate impostato nell'encoder è nominale, i timestamp rispecchiano la frequenza effettiva. Audio non incluso (come nella v1.4). Non cambiare preset durante REC. Questa build non è stata compilata su un SDK Android in questo ambiente.

Apri la cartella del progetto con Android Studio, sincronizza Gradle e avvia sul telefono. Se local.properties punta a un altro utente macOS, modifica sdk.dir con il percorso locale.
