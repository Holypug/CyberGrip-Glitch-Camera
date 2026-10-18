# Pubblicazione su GitHub — guida rapida

1. Crea un repository **Public** chiamato `CyberGrip-Glitch-Camera` su https://github.com/new. **Non** inizializzarlo con README, perché è già presente nello ZIP.
2. Estrai questo ZIP. Da Terminale, nella cartella estratta:

```bash
git init
git add .
git commit -m "Initial public release v1.5.1"
git branch -M main
git remote add origin https://github.com/TUO_USERNAME/CyberGrip-Glitch-Camera.git
git push -u origin main
```

Sostituisci `TUO_USERNAME` con il tuo username GitHub. GitHub richiede un metodo di autenticazione valido (ad es. GitHub CLI o credenziali Git configurate).

3. In Android Studio, **Build → Generate Signed App Bundle / APK → APK**, genera la variante release con una chiave privata. Conserva keystore e password fuori dal repository.
4. Su GitHub, **Releases → Draft a new release**, crea il tag `v1.5.1`, titolo `CyberGrip GLITCH v1.5.1 — UI FIX`. Incolla `RELEASE_v1.5.1.md` come descrizione e allega il file APK firmato. Pubblica.
5. Prima della distribuzione, installa e prova l'APK release su un telefono diverso o dopo un backup.

NOTA: non è incluso alcun APK compilato; la pubblicazione non è stata effettuata automaticamente.
