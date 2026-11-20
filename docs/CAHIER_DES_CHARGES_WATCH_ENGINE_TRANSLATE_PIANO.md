# Cahier des charges validé — Local Agent LLM / Sarah Engine / Apple Watch SE 2
**Version 1.0 — 19 novembre 2026.** Document destiné à Codex. Ce document décrit des changements à réaliser, **pas des fonctions déjà livrées**.

## Règle n°1 : protéger l'existant
Nom de l'application iPhone : **Local Agent LLM** (Sarahia est la marque/personnalité, Sarah Engine est le moteur). Auditer le code actuel avant toute modification. **Ne pas refaire l'interface principale, les agents, le chat ordinaire, ni le mode vocal Sarah existant, qui plaisent à l'utilisateur.** Ne modifier l'UI que pour : (a) Traduction Live, (b) application Apple Watch SE 2, (c) Piano Live, (d) premier lancement des modèles, (e) explication du scanner QR de connexion PC. Corriger les plantages, erreurs d'état et réponses aberrantes sans régression. Ne jamais afficher des bouts de code internes à la place d'une réponse normale ; détecter modèles indisponibles et erreurs clairement, journaliser sans données sensibles.

## 1. iPhone : onboarding « Installer les modèles »
- Ne plus embarquer plusieurs gigaoctets de poids IA dans l'IPA, l'archive ou la version TestFlight.
- Au **premier démarrage**, après l'écran d'accueil, afficher **« Installer les modèles »**, expliqué comme une installation du **Sarah Engine**. Boutons « Installer », « Plus tard », état réel par composant, taille et stockage disponible, progression réelle, reprendre après interruption, Wi-Fi conseillé, notification si pertinent.
- L'utilisateur voit des capacités : conversation, voix/reconnaissance, traduction en direct, synthèse vocale, vision, lecture de partitions et autres capacités existantes. Aucun nom technique (« llama.cpp », « Whisper », « Qwen », etc.) dans les écrans ordinaires ; **ne pas cacher les attributions/licences obligatoires** : mettre « Licences open source » dans les réglages. Ne pas promettre un mode local si une fonction dépend du réseau.
- Dans **Réglages > Sarah Engine**, permettre l'installation, la mise à jour, la suppression et la réinstallation des packs. Une capacité demandée sans modèle doit proposer son installation au lieu de planter.
- Chaque ressource téléchargeable a un manifeste versionné (identifiant interne, URL officielle stable, licence code ET poids, taille réelle, SHA-256, compatibilité, architecture, dépendances). Vérifier signature/intégrité, téléchargement atomique, reprise, quota disque. Aucun modèle téléchargé silencieusement.
- Miroirs et conservation : sauvegarder **seulement** les modèles dont les licences autorisent la redistribution, avec licence/NOTICE et provenance, idéalement dans des GitHub Releases ou stockage d'artefacts compatible (pas de gros binaires directement dans l'historique Git). Vérifier limites et droits avant publication. Ne pas inventer un lien miroir.
- **IMPORTANT :** le README actuel impose au contraire une IPA >1 Go, Whisper et plusieurs poids GGUF embarqués. Modifier les scripts, contrôles CI, documentation et manifests en cohérence avec ce nouveau cahier des charges, sans casser les releases existantes ni effacer de versions.
- Cible : IPA la plus légère possible, taille exacte mesurée par CI, modèles installés séparément. Première ouverture utilisable même si l'utilisateur reporte les téléchargements.

## 2. Traduction Live : UI à reproduire fidèlement
**Référence prioritaire :** `docs/maquettes/translate-live-reference.svg` + la dernière maquette iPhone paysage validée dans la conversation, qui fait autorité sur le style. L'interface est **exclusivement paysage** pendant la session de traduction, sans forcer le reste de l'application à tourner. Deux grands panneaux horizontaux très lisibles :
- **Haut** : fond presque noir / bleu nuit, étiquette « FRANÇAIS », traduction en **français** en très gros texte blanc, texte cumulatif.
- **Bas** : fond bleu mat lumineux, étiquette « HÉBREU », transcription source en **hébreu** RTL en très gros texte blanc, texte cumulatif.
- Barre discrète : retour, Sarahia, indicateur LIVE, microphone marche/arrêt, lecture audio, réglages et inversion des langues si disponible. Ne pas réintroduire la vieille interface.
- Exemple de correspondance : en bas « שלום », en haut « Bonjour ».
- Quand l'orateur parle très rapidement et longtemps, **ajouter les mots et phrases à la suite**, sans remettre l'écran à zéro pour chaque fragment. Séparer hypothèses partielles (révisables) de segments finalisés (immuables), gérer les espaces/punctuation et auto-défilement, conserver un historique pendant la session. Boutons pause/stop et accessibilité Dynamic Type/VoiceOver.
- Fixer l'orientation iPhone et la gestion d'interruption micro et rotation, sans dégrader les autres vues.

## 3. Reconnaissance hébreu en continu, traduction, TTS
- Auditer d'abord les moteurs existants. Prévoir **Whisper multilingue** pour hébreu via runtime iOS compatible (p. ex. whisper.cpp MIT sous réserve de licences/poids vérifiées), paramètres de langue « he », modèle suffisamment fiable, pack téléchargeable dans Sarah Engine. **Pas de téléchargement des gros modèles dans l'IPA.**
- Pipeline audio réel : capture micro -> segmentation VAD -> transcription multilingue incrémentale (mots partiels puis segments stabilisés) -> traduction hébreu vers français -> affichage cumulé -> **lecture TTS française** facultative activable. Si l'inférence est plus lente que la parole, présenter la latence sans prétendre à une transcription instantanée parfaite. Benchmarks obligatoires sur iPhone 14.
- Traduction : évaluer Google ML Kit on-device (prise en charge `he` + `fr`) avec packs installables, ou alternative légalement compatible et vérifiée. Vérifier la politique de licence/redistribution de l'ensemble du moteur et des ressources ; ne pas qualifier un service de logiciel open source sans preuve. Tester la traduction hors ligne **après** téléchargement des packs.
- TTS : AVSpeechSynthesizer/voix française système disponible hors ligne selon la voix, ou autre voix libre licenciée et téléchargeable. Lecture segmentée dans l'ordre, file anti-chevauchement, pause, réglage vitesse. Empêcher la voix de sortie d'être réécoutée/retrotranscrite autant que possible avec gestion audio/écouteurs.
- Tests réels : parole hébraïque rapide, enchaînement de longues phrases, noms propres, sens RTL, pause, retour arrière, hors réseau, interruptions, voix française, reconnaissance exacte de « שלום » et rendu « Bonjour ».
- Commande vocale « active traduction en direct » ou « translate live » depuis le mode vocal existant : déclencher la **nouvelle** UI et le pipeline, pas un simple message chat ni l'ancien écran.

## 4. watchOS : une vraie application compagnon pour Apple Watch SE 2
- Créer **target watchOS native SwiftUI**, incluse correctement dans l'archive iOS destinée à TestFlight. Adapter et tester sur SE 2 **40 et 44 mm** ; grands boutons, textes non coupés, éviter écran surchargé. Reprendre la lisibilité des **premières** maquettes, pas l'infographie compacte.
- Accueil Watch : « Parler avec Sarah », « Traduction Live », « Piano Live » ; autres agents accessibles sans encombrer. Un appui ouvre conversation vocale, petit signal de démarrage original/licite (pas de son Apple/ChatGPT copié), anneau micro, écouter/stop, réponse affichée et TTS.
- Utiliser principalement les ressources iPhone et Sarah Engine quand la montre est connectée ; indiquer la perte de liaison. Contrôle via WatchConnectivity ; évaluer un transport audio performant avec vraies contraintes watchOS (permissions, batterie, latence, réseau, arrière-plan), **ne pas supposer que WatchConnectivity peut streamer de l'audio brut continûment sans limitation**.
- Traduction depuis micro Watch : hébreu capté côté Watch -> traitement iPhone lorsque possible -> français affiché en haut de l'iPhone -> source hébreu en bas -> lecture facultative. Tester une vraie session avec écran d'iPhone visible, watch micro, et signaler explicitement les limitations quand l'iPhone est verrouillé ou la Watch hors portée.

## 5. Piano Live : iPhone + Watch synchronisés
- Lancer la commande « Piano Live » depuis Sarah. iPhone aide à connecter un clavier **Bluetooth MIDI** via CoreMIDI ; choisir partition PDF/image/MusicXML puis reconnaissance des notes.
- Sur Watch : **une note à la fois, très grosse en haut** : « Do (C) », « Ré (D) », etc. Afficher un mini-clavier **très zoomé** centré sur la bonne zone (2 ou 3 touches noires de repère) ; touche blanche/noire cible bien mise en rouge. Repère textuel bref et exact : Do = touche blanche immédiatement à gauche du groupe de deux touches noires ; Ré = touche blanche entre les deux noires. Éviter les repères pédagogiques erronés, gérer les octaves.
- Détection de la **bonne note et de la bonne octave** à partir des événements MIDI (note-on, velocity), pas d'une simple animation. Quand correcte : vert / « Bien joué », puis note suivante ; sur erreur : aide, pas progression forcée. Gérer accords et tempo plus tard.
- Si Watch absente : écran iPhone directement. Si Watch détectée, demander : « Sur la montre », « Sur le téléphone », « Sur les deux (synchronisé) ». Case « Toujours ce choix », modifiable par la voix et dans les réglages. Persister le choix et synchroniser l'état de la leçon. Déconnexion = bascule compréhensible vers iPhone.
- Références : `docs/maquettes/piano-watch-se2-reference.svg` et les premières images de montre validées dans la conversation, pas les conceptions encombrées.

## 6. Lecture de partitions : composant OMR local et légalement commercialisable
- Obligation : **petit** moteur de reconnaissance de partitions (optical music recognition / OMR), utilisable à des fins **commerciales** ; vérifier **licence du code, licence des poids, dépendances, éventuels jeux de données et redistribution**, et compatibilité iOS Apple Silicon. Une licence du dépôt n'implique pas automatiquement que tous les poids soient redistribuables.
- Pistes à **évaluer, pas à intégrer aveuglément** : `BreezeWhite/oemer` (code MIT, portage iOS/performance/poids à vérifier), `tensorflow/moonlight` (Apache-2.0, état de maintenance à vérifier), `Luckolite/Sheet-Music-Recognition` (Apache-2.0 déclaré pour code et poids, vérifier indépendamment la chaîne et le portage). Ne pas choisir automatiquement de moteurs AGPL/GPL si la stratégie commerciale/fermée du produit n'en respecte pas les obligations. Un logiciel GPL/AGPL peut être commercialisé, mais impose des obligations spécifiques : faire valider.
- Préférer import **MusicXML natif** fiable pour un premier MVP, puis PDF/images -> prétraitement -> OMR -> MusicXML -> notes MIDI synchronisées. Ne pas inventer une partition si OCR incertain : afficher une prévisualisation et demander validation/correction.
- Pack téléchargeable Sarah Engine distinct, petit, tests de précision sur vraies partitions de piano ; fournir matrice performances RAM, taille, CPU, latence, exactitude, droit commercial. Si aucune solution candidate ne satisfait tout, documenter les blocages et proposer une architecture réaliste plutôt que prétendre que c'est fait.

## 7. Connexion au PC par QR
- Préserver la fonction existante : texte explicite « Ouvre Local Agent / Sarahia sur ton ordinateur Windows et **scanne le QR code affiché SUR LE PC avec ton iPhone** ». Ce n'est pas un QR code imprimé ni celui de l'iPhone ; fournir permission caméra, explication pairing, expiration, refus et reconnexion.
- Le lien **exact** du Microsoft Store n'est pas confirmé : Codex doit rechercher la fiche publiée, vérifier éditeur et identifiant produit avant de mettre un bouton. En attendant, ne jamais inventer de lien Microsoft Store.

## 8. Compilation Windows + Mac CI + TestFlight
- Sur PC Windows, modifier le dépôt GitHub ; build iOS/watchOS sur **GitHub Actions macOS** avec Xcode, provisioning et certificats Apple gérés via **GitHub Secrets** (aucune clef/identifiant dans le dépôt ni dans les logs).
- Livrer un .xcarchive exportable App Store Connect avec **iPhone et extension Watch signées par la bonne équipe**, bon bundle/entitlements/versions, configuration compagnon. Vérifier l'installation réelle sur Apple Watch SE 2 depuis TestFlight ; ne pas promettre installation automatique sans validation.
- **TestFlight interne** sur son propre compte Apple Developer pour 1 à 2 semaines ; pas de mise en ligne publique App Store. Les builds TestFlight expirent en général après 90 jours ; l'utilisateur peut soumettre d'autres versions. Installation via TestFlight n'exige normalement pas le mode développeur watchOS, contrairement au déploiement local Xcode.
- Capture App Store **après** fonctionnement réel sur appareil/simulateur ; les images conceptuelles ne sont pas des captures App Store. Tests iPhone 14 / Watch SE 2 en priorité.

## 9. Plan d'exécution demandé à Codex
1. État des lieux : cibles iOS/watchOS, workflows, modules voice/translate, sources/poids embarqués, README et règle >1 Go ; rapport d'audit sans retoucher l'UI générale.
2. Créer une branche `feat/watch-translate-engine-piano`, commits petits, pas de suppression des releases.
3. Corriger l'onboarding Sarah Engine et le pipeline téléchargement sécurisé (puis archive légère).
4. Corriger Traduction Live fidèle à la maquette et streaming HE->FR/TTS, tests de régression.
5. Créer Watch SE 2 puis Piano Live/CoreMIDI/OMR après audit de licence.
6. Programmer CI build et TestFlight, documenter les secrets que seul le propriétaire doit saisir.
7. Fournir un rapport : fichiers modifiés, captures réelles, tailles de l'IPA et packs, compilation et tests exécutés, et tâches restantes. **Ne jamais déclarer que la Watch/TestFlight fonctionnent sans test réel.**

## 10. Critères d'acceptation
- [ ] UI chat / mode vocal actuels inchangés
- [ ] IPA sans poids IA lourds ; écran premier lancement « Installer les modèles » puis packs persistants
- [ ] Traduction paysage : **FR en haut (foncé), HE en bas (bleu)** ; transcription cumulative, streaming réel et lecture TTS française
- [ ] Bouton commande vocale Translate Live lance bien la nouvelle UI
- [ ] Watch SE 2 lisible, native, incluse à l'archive et testée réellement
- [ ] Piano : nom Do (C) + touches noires repère + cible rouge, validation via MIDI, iPhone/Watch/les deux
- [ ] OMR + licences code/poids vérifiées avant inclusion commerciale
- [ ] QR : consigne « scanner le code SUR LE PC »
- [ ] TestFlight privé installé sur iPhone et Watch ; preuves de test ; pas de validation App Store publique pour ce test interne
- [ ] Aucune vraie capture App Store remplacée par une image générée
