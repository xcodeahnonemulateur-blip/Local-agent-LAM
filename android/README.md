# Local Agent LLM — Android

Interface native adaptée de SarahAI pour iPhone : menu des six agents sous la capsule, thèmes clair/sombre, historique local, dictée Android et Studio HTML. Entrée envoie un message ; Maj+Entrée ajoute une ligne.

## Logo LAM

Le master noir et blanc est `branding/lam-master.png`. Le prompt et la provenance sont dans `branding/lam-source.json`. Depuis la racine, `node android/scripts/build-icons.mjs` génère les icônes Android et `store/icon-512.png` avec Sharp installé dans Desktop. Les autres applications ne sont pas modifiées.

## Compilation

Java 17, Android SDK 36, Build Tools 36.0.0 et Gradle 9.5.0. Configurer le SDK dans `local.properties` (ignoré), puis exécuter dans android :

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest lintDebug bundleRelease
```

Le package debug est `com.nathandev.localagent.debug`. La release conserve `com.nathandev.localagent`. Le bundle local reste non signé par la clé Google Play.

## QR carré

Le QR JSON `saraya-pair` version 1 reprend le contrat Desktop : HTTPS local, certificat épinglé, invitation temporaire et jeton chiffré par Android Keystore. La caméra et l'import d'une image utilisent ZXing. Le PC demande la synchronisation, qui doit être acceptée sur Android. Un registre atomique conserve les données versionnées, pièces jointes, conflits et sauvegardes avant import.

Le test `BridgeSmokeInstrumentation` isole ses préférences, clés et fichiers. `qa/desktop-bridge-fixture.ts` utilise les vrais NetworkService et Store du Desktop. Douze contrôles couvrent le décodage, les invitations invalides, un faux certificat, le jumelage, la persistance chiffrée, le refus du consentement, l'échange dans les deux sens, l'idempotence et l'oubli local. La caméra physique doit aussi être essayée sur téléphone.

## Google Play

Le workflow `.github/workflows/saraiya-internal-play.yml` s'exécute lors d'un changement Android sur main. Il choisit un code supérieur aux bundles et pistes existants, vérifie et compile Android, signe avec la clé existante et soumet à la production. Le nom, l'icône et les captures sont actualisés dans la même transaction. Les descriptions et l'URL vidéo existantes sont conservées. Google Play exige une URL YouTube pour la vidéo promotionnelle ; le MP4 reste un livrable distinct tant que cette URL n'est pas fournie. Une soumission peut encore attendre la validation Google.

Secrets GitHub : ANDROID_UPLOAD_KEYSTORE_BASE64, ANDROID_STORE_PASSWORD, ANDROID_KEY_PASSWORD, ANDROID_KEY_ALIAS, GOOGLE_PLAY_SERVICE_ACCOUNT_JSON. Ne jamais afficher leurs valeurs.

## Limites

Le moteur léger répond à des commandes simples, dont la démonstration en anglais. Whisper, l'inférence GGUF et la traduction continue nécessitent encore un moteur Android opérationnel. Le Studio affiche du HTML sans JavaScript. Le moteur léger n'analyse pas les pièces jointes sélectionnées. La dictée et la lecture dépendent des services Android disponibles.
