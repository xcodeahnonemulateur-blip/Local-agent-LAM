# PROMPT À DONNER À CODEX — Sarahia / Sarah IA

Tu travailles sur le dépôt `200012Yoel/SarahAI`. **Lis entièrement `CAHIER_DES_CHARGES_SARAHIA.md` AVANT toute modification.** Ce document fait foi pour les exigences nouvelles et prévaut sur l'ancienne règle du README qui exigeait de gros modèles dans l'IPA.

## Mission

Implémente progressivement le cahier des charges, **sans casser l'application iPhone existante** :

1. Commence par un **audit du dépôt et de ses workflows iOS/watchOS** : indique les fichiers concernés, le framework réel, les six familles fonctionnelles de Sarah Engine, les modèles embarqués, les bugs reproductibles, et ce qui existe déjà vs ce qui manque.
2. **Ne pas coder l'interface définitive Traduction Live avant validation du visuel par l'utilisateur.** La référence à valider est iPhone en paysage : français (ex. « Bonjour ») en HAUT sur fond très sombre, hébreu (ex. « שלום ») en BAS sur fond bleu mat, texte XXL, RTL correct, micro discret.
3. Passer à une **IPA légère** : interface d'accueil « Installer les modèles », téléchargement fiable de packs locaux dans Sarah Engine après installation, sans gros poids dans le bundle, tout en conservant les six familles existantes. Ne masquer les noms techniques que dans l'UI grand public, pas dans les licences légales.
4. Étudier les moteurs OMR libres **BreezeWhite/oemer** (MIT déclaré : https://github.com/BreezeWhite/oemer) et **Luckolite/Sheet-Music-Recognition** (Apache-2.0 code/poids annoncés : https://github.com/Luckolite/Sheet-Music-Recognition). NE PAS intégrer avant d'avoir vérifié les poids, dépendances et droits de redistribution commerciale, taille, précision et exécution locale sur iPhone 14. Le pack de lecture de partitions doit pouvoir être téléchargé après le premier lancement. Vérifier que photos/PDF produisent notes, durées et octaves utilisables.
5. Implémenter la vraie app **watchOS Watch SE 2** et Piano Live : cercle « Do », clavier zoomé 3–5 touches avec repères noirs corrects, touche cible rouge, note MIDI juste => suivant ; pilotage sur Watch/iPhone/les deux. Pairage MIDI sur iPhone, synchro fiable.
6. Corriger le **chat qui renvoie du code à la place d'une réponse** et la commande vocale « Traduction Live » qui ouvre une vue erronée ; créer tests de régression.
7. Corriger l'assistant **Connexion ordinateur** : ouvrir l'app Windows, scanner **avec l'iPhone** le QR affiché sur **le PC**. Si aucun lien officiel Microsoft Store n'est vérifié, ne pas en inventer.
8. Préparer un **workflow macOS GitHub Actions -> TestFlight interne** (appli iPhone + extension Watch signées), sans Mac personnel. Utiliser secrets GitHub pour la signature, ne jamais exposer les certificats. Documenter prérequis App Store Connect et installation Watch, builds testables 90 jours, limites/coûts des runners. Aucune publication App Store public sans validation.
9. Les **images générées sont des références d'UI**, PAS des captures App Store. Le propriétaire pourra te fournir l'archive des images pour les copier dans `design/references/`. Ne pas annoncer qu'elles sont déjà dans le dépôt si elles ne s'y trouvent pas.

## Méthode obligatoire

- Avant chaque groupe de modifications : expliquer l'état constaté, le plan, les risques et dépendances.
- Travailler en petites PR/commits réversibles avec tests. Conserver historiques, modèles et fonctionnalités existantes quand possible.
- Ne pas annoncer « c'est prêt » avant compilation/TestFlight et vérification sur vraie Apple Watch SE 2.
- Ne pas contourner les protections d'Apple, inventer un lien Store ou affirmer des licences non vérifiées.
- Après chaque étape, donner une consigne **simple en français** pour un débutant sous Windows, sans jargon non expliqué.
- Produire une liste `FAIT / À TESTER / BLOQUÉ` et les liens exacts GitHub / GitHub Actions / TestFlight, quand ils existent.
