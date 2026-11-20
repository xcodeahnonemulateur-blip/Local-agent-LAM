# Sarahia Watch 1.0.0 — première intégration (prototype)

La branche \`feat/sarahia-watch-1.0.0\` ajoute un **target watchOS compagnon** à l'application Sarahia iOS existante. La version Watch est **1.0.0**. La version iPhone compagnon passe de **3.0 à 3.1.0 (build 4)**, sans rétrograder le numéro déjà publié.

## Interface validée
- Accueil Watch : Parler avec Sarahia, Translate Live, Piano Live, Autres agents.
- Écran micro bleu/violet « J'écoute… », arrêt rouge.
- Piano Live : aperçu des notes Do/Ré/Mi; **pas encore un vrai lecteur MIDI/partition**.
- iPhone paysage : français en haut (fond bleu nuit), hébreu en bas (fond bleu), texte cumulatif et lecture TTS via le moteur iPhone actuel.

## Comment fonctionne la première liaison
1. Installer **l'application iPhone signée avec le target SarahiaWatch embarqué** (même identifiant de compagnon).
2. Installer l'application Sarahia depuis l'app Watch de l'iPhone / TestFlight ou le déploiement Xcode.
3. Ouvrir Sarahia sur l'iPhone et sur la montre et autoriser le microphone sur la montre.
4. Poser la montre près de la télévision, garder l'iPhone à portée.
5. Appuyer sur Translate Live depuis la Watch ou l'iPhone.
6. Audio de la montre → WatchConnectivity (petits buffers PCM 16 kHz) → moteur reconnaissance/traduction sur l'iPhone → français affiché + prononcé.

**Limites connues, importantes :** WatchConnectivity \`sendMessageData\` n'est disponible en flux de messages que quand les deux applications sont joignables, typiquement au premier plan. **Ne fonctionne pas de façon fiable en arrière-plan ou quand l'iPhone est verrouillé**. La distance de 15 m dépend du Bluetooth/Wi-Fi. Un écran éteint n'est pas une garantie de session audio persistante. Si Watch non joignable, l'app doit indiquer l'erreur et ne pas prendre secrètement le micro de l'iPhone.

Le mode « Parler » et le piano affichent une première interface ; le traitement d'un dialogue LLM depuis la Watch et la reconnaissance MIDI/OMR restent à intégrer et à tester.

## Compilation
Sur Mac avec Xcode récent (support watchOS 10+), ouvrir \`SarahIA/SarahIA.xcodeproj\` et compiler le target \`SarahiaWatch\` pour simulateur, sans signature :
\`\`\`bash
xcodebuild -project SarahIA/SarahIA.xcodeproj -target SarahiaWatch -sdk watchsimulator -configuration Debug CODE_SIGNING_ALLOWED=NO build
\`\`\`
Puis archive iOS avec la montre intégrée. La signature de l'iPhone **et** de la montre demande une équipe Apple Developer et les profils adaptés. Une archive non signée ne constitue pas un fichier installable sur un appareil réel. Utiliser TestFlight privé ou Xcode depuis un Mac compatible pour tester sur iPhone 14 + Watch SE 2.

Le workflow \`.github/workflows/sarah-watch-mac.yml\` compile le target sur un runner Mac, mais ne publie pas une IPA installable sans certificat Apple.

## À tester sur vrais appareils
- Batterie et délai de transcription pendant TV hébraïque ; autorisations micro et Speech.
- Passage en veille écran, iPhone verrouillé, interruptions réseau, latence WatchConnectivity.
- Modèles et packs traduction réellement présents sur l'iPhone ; français TTS.
- Vérification sur Watch SE 2 40/44 mm et iPhone en paysage.
