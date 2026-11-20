# CAHIER DES CHARGES — Sarahia / Sarah IA (iPhone + Apple Watch SE 2)

**Date : 19 novembre 2026 · Statut : spécifications pour Codex, à développer et tester.**  
**IMPORTANT : les maquettes ne sont PAS des captures d'une application réelle, ni des preuves que les fonctions sont déjà implémentées.**  
**App iOS concernée : ce dépôt `200012Yoel/SarahAI`. Ne pas remplacer les autres fonctionnalités ni réécrire le projet de zéro.**

## Priorité et règles

1. Analyser le code existant et proposer une liste des fichiers à modifier, les risques et un plan de migration ; conserver les données et fonctions actuelles. Corriger les bugs sur des cas reproductibles, sans prétendre les avoir corrigés avant test.
2. Développer une **vraie extension watchOS** pour Apple Watch SE 2 (40 et 44 mm, selon compatibilité réelle de la cible de déploiement) intégrée à l'application iPhone. Interface SwiftUI compacte, lisible, contrôles gros et accessibles, aucun écran de montre géant ou surchargé.
3. **Test privé officiel avec TestFlight, sans Mac personnel** : préparation de build/signature via runner macOS GitHub Actions et App Store Connect, avec certificats/profils/secrets placés dans les secrets GitHub, jamais dans le dépôt. Inviter le propriétaire comme **testeur interne App Store Connect** ; les builds TestFlight expirent après 90 jours. L'extension watchOS doit être incluse dans le build distribué et proposée sur la montre jumelée. Le **Developer Mode de la montre n'est pas nécessaire pour une installation TestFlight**. Ne jamais promettre une signature « à vie » ni un TestFlight illimité.
4. Ne **pas** soumettre à l'App Store public avant validation de l'utilisateur. Les captures App Store seront prises plus tard sur le vrai simulateur ou des appareils exécutant l'interface réelle, aux dimensions conformes.

## A. Sarah Engine : IPA légère et modèles après installation (obligatoire)

- **Nouvelle décision prioritaire qui annule l'ancienne contrainte du README** consistant à embarquer Whisper et de gros GGUF dans une IPA dépassant 1 Go ou à refuser les IPA légères. L'IPA doit être légère : elle contient l'UI, les bibliothèques natives, les manifestes et strictement les petits éléments techniques nécessaires au lancement, **pas des gigaoctets de poids de modèles**.
- Au **premier lancement sur l'iPhone**, proposer un écran simple intitulé **« Installer les modèles »** pour alimenter **Sarah Engine**, avec taille réelle estimée, espace disque disponible, Wi-Fi recommandé, bouton **Installer** et option **Plus tard**. Ne pas figer des valeurs d'exemple comme tailles vérifiées.
- Conserver la section **Sarah Engine** et ses **six familles/systèmes fonctionnels existants**. Auditer leur inventaire exact dans le projet avant d'écrire la liste : **aucune fonction existante ne doit disparaître**. La liste des modèles est masquée à l'utilisateur normal derrière les familles fonctionnelles de Sarah Engine. Ne pas exposer « llama.cpp », noms GGUF, noms de moteurs ou paramètres techniques dans l'interface principale.
- Afficher uniquement des libellés clairs (conversation, traduction, reconnaissance, voix, musique/partitions, etc., à ajuster aux six familles réellement existantes). L'application doit cependant conserver une rubrique **Mentions légales / licences open source** donnant les attributions et avis obligatoires ; « noms masqués » ne signifie jamais dissimuler des licences.
- Faire un **gestionnaire de téléchargements** : fichiers versionnés avec URL(s) contrôlée(s), identifiant, SHA-256, licence, taille et espace requis, reprise après interruption, progression, erreurs explicites, annulation/reprise et installation atomique, vérification des empreintes, nettoyage des fichiers partiels et versionnage/mises à jour. Ne pas annoncer « prêt » quand un moteur manque. Prioriser téléchargement initial en Wi-Fi ; permettre usage de l'application et reprise du téléchargement plus tard.
- Pour la pérennité, **miroiter les poids autorisés** dans un stockage de releases/artefacts ou un hébergement contrôlé, après **vérification séparée des licences du code, des poids, du dataset et de la redistribution commerciale**. Ne jamais pousser de gros poids directement dans l'historique Git ni dépasser les limites de GitHub ; étudier GitHub Releases, stockage objet ou CDN selon les coûts/limites. Prévoir plusieurs URL et vérifications, sans contourner un retrait légal.
- Le moteur reste **local à l'iPhone une fois téléchargé**, sauf fonctions réseau explicitement signalées. La Watch utilise surtout les ressources de l'iPhone. Les traitements lourds doivent respecter l'empreinte RAM réelle de l'iPhone 14.

## B. Réparer conversation et mode vocal

- Reproduire les erreurs où Sarah répond par du **code brut**, des refus incohérents ou des réponses sans rapport à un simple message utilisateur. Identifier la cause (routage agent, prompts, parsing, streaming, code fencing, modèle absent), ajouter journalisation respectueuse de la vie privée et tests de non-régression.
- Un message de conversation ordinaire doit obtenir une **réponse conversationnelle** dans le chat ; afficher du code uniquement quand l'utilisateur demande du code ou en contexte de développement. En cas de moteur absent, afficher une aide compréhensible (« Installer les modèles de conversation ») et ne pas inventer de réponse.
- Commandes vocales **« Traduction Live » / « active traduction en direct »** et **« Piano Live »** : routage fiable vers les vrais écrans/fonctions correspondants, sans choisir un mode ancien au hasard. Déclenchement unique par intention, audio/permissions/micro vérifiés.

## C. Traduction Live : maquette À VALIDER avant implémentation définitive

- Sur **iPhone en orientation PAYSAGE**, écran plein, deux grandes zones superposées. **HAUT = traduction FRANÇAISE**, fond noir/bleu très foncé ; **BAS = texte source HÉBREU**, fond bleu mat ; texte très grand et contraste élevé.
- Exemple exact de vérification : micro capte « shalom » / « שלום » : afficher **« Bonjour » en HAUT**, **« שלום » en BAS**, dans cet ordre. Le rendu doit être RTL correct pour l'hébreu, avec caractères et ponctuation dans le bon sens.
- Micro et statut d'écoute visibles, lecture audio traduite disponible, commandes accessibles mais peu encombrantes. **Ne pas réutiliser l'ancienne interface inversée** ; ne pas confondre langue source et cible. Garder la traduction fonctionnelle hors réseau si les packs sont installés et que le moteur le permet.
- **ARRÊT DESIGN : faire valider la maquette paysage par l'utilisateur avant de modifier l'écran de traduction final.** Elle constitue une référence esthétique, non un screenshot de fonctionnement.

## D. Apple Watch SE 2 : Sarahia

- Accueil de montre minimal : **Parler avec Sarahia**, **Traduction Live**, **Piano Live** et accès aux autres agents/réglages par navigation compacte. Son discret au début du mode vocal, micro/pause/arrêt ; vérifier les autorisations audio et limites watchOS. Privilégier la capture sur Watch, traitement sur iPhone via communications adaptées quand jumelés ; comportements hors-ligne et déconnexion explicites.
- Pour **Traduction Live**, la montre peut écouter l'hébreu, transmettre les segments autorisés à l'iPhone pour transcription/traduction, qui affiche le français en haut et l'hébreu en bas. Prévoir latence, échec de liaison, état et consentement micro.

## E. Piano Live : apprentissage synchronisé Watch + iPhone

- Via l'iPhone, rechercher et connecter le **piano Bluetooth MIDI** (CoreMIDI), importer une partition (photo/PDF/MusicXML), analyser les notes et construire un parcours pédagogique. Prévoir piano sans MIDI : aide visuelle seulement, pas de détection automatique prétendue.
- À chaque lancement, **si aucune Watch compatible et connectée : iPhone automatiquement**. Sinon demander **« Sur la montre », « Sur l'iPhone » ou « Sur les deux (synchronisés) »**, avec case **« Toujours ce choix »** et préférence modifiable à tout moment. Les deux écrans affichent toujours la même note et progression quand mode synchronisé.
- Watch SE 2 : **une seule note géante dans un cercle en HAUT (« Do », avec « C » éventuellement en dessous)**, puis **un clavier TRÈS ZOOMÉ de 3 à 5 touches** avec le motif de deux ou trois touches noires réellement aligné pour servir de repère. La touche à jouer est **ROUGE**. Pas de longues phrases ni de tableau de bord encombré. Petit indicateur de progression et retour haptique discret optionnel.
- Repères pédagogiques corrects : **Do** = touche blanche **à gauche du groupe de deux noires**, **Ré** = touche blanche **entre les deux noires** ; nom de la note bien lisible. Quand le MIDI renvoie la **bonne note et la bonne octave**, passer à la note suivante avec feedback bref. Mauvaise note : signal doux et conserver la note attendue ; apprendre sans pénaliser. Gérer le timing de plusieurs notes et accords sans prétendre qu'une note unique couvre toutes les partitions.
- iPhone : partition complète avec suivi de la mesure ; montre : note/action courante zoomée. Réactivité et mémoire raisonnables.

## F. Petit moteur OMR : reconnaissance des partitions, licence commerciale et hors ligne

**Exigence essentielle :** moteur de reconnaissance optique des partitions (OMR) **petit**, **gratuit/open source**, **utilisable dans une application vendue commercialement**, fonctionnant **sur iPhone hors ligne après installation**. Entrées photo et PDF, sortie structurée **MusicXML / notes et durées / MIDI** pour Piano Live.

- Candidats à étudier (pas de choix prévalidé) :
  - `BreezeWhite/oemer` : code **MIT** déclaré ; pipeline Python/ONNX à étudier, licence effective des poids et dépendances à auditer.
  - `Luckolite/Sheet-Music-Recognition` : code et poids **Apache-2.0 annoncés** ; vérifier indépendamment le modèle livré, ses données, ses capacités sur partitions de piano et la faisabilité de conversion native iOS.
- Pour chaque candidat : produire tableau **licence code/poids/datasets**, droit de **vente et redistribution des poids**, **taille téléchargée**, RAM/CPU, possibilités Core ML / ONNX Runtime Mobile / TFLite, temps de reconnaissance sur iPhone 14, qualité sur 10 partitions de piano réelles (clefs, altérations, rythmes, accords), capacité PDF multipage, erreurs et coût d'intégration. Écarter automatiquement les variantes **NC / non commercial**, AGPL si incompatibles avec l'architecture commerciale choisie, ou droits des poids inconnus.
- Aucun nom de modèle montré dans l'UI courante : l'utilisateur ne voit que **« Lecture de partitions »** dans Sarah Engine. Ce pack est téléchargé après installation, jamais dans l'IPA lourde.
- **Ne pas inventer la justesse des partitions reconnues** : afficher les notes avec indication d'incertitude, proposer correction manuelle et aperçu avant leçon ; privilégier MusicXML importé directement lorsqu'il existe.

## G. Connexion au PC par QR code

- Dans l'iPhone, rubrique **« Connexion ordinateur »** : afficher **« Ouvre Sarahia sur ton PC Windows, puis scanne avec ton iPhone le QR code affiché sur l'écran du PC »**.
- QR affiché sur le **PC**, scanné par **l'iPhone**, et NON l'inverse. Pairage sécurisé, expiration du QR, demande de confirmation, état de connexion, révocation, reconnexion et vérification de la synchro des conversations. Tester sur le LAN, gérer permissions et erreurs.
- Rechercher la vraie fiche officielle **Microsoft Store** du logiciel Windows **Sarahia / Local Agent LLM / Saraya** et n'afficher le lien **que s'il est vérifié**. Aucun faux lien, aucune étiquette « disponible Microsoft Store » si la fiche n'est pas confirmée.

## H. Maquettes, images et préparation App Store

- Références visuelles de cette conversation : écrans Watch (clavier compact, cercle Do, touche rouge), affichage synchronisé et **nouvelle maquette paysage « Bonjour / שלום »**. Prévoir `design/references/` et y copier ces fichiers image **seulement quand les fichiers ont été effectivement transmis au dépôt** ; ne pas inventer des fichiers présents.
- Après développement : tester SE 2 physique / simulateurs 40 et 44 mm, iPhone 14, bascule du paysage, droits micro, déconnexion Bluetooth, téléchargement modèle, import PDF, notes MIDI et redémarrages.
- **Captures destinées à l'App Store** : captures d'écran réelles de la version exécutée, non des images générées, aux tailles officielles ; aucun faux QR code ni fausse fiche commerciale.

## I. Livrables attendus de Codex

1. Rapport d'audit initial du dépôt et tableau de dépendances/licences ; plan en étapes sûres.
2. PR(s) code pour IPA légère, gestionnaire Sarah Engine, régression chat, routage vocal, Watch, Piano Live + OMR, QR PC, avec tests et documentation.
3. Workflow GitHub Actions macOS de compilation et **upload TestFlight pour test interne** ; documentation pas à pas en français pour une personne ayant **Windows et iPhone, sans Mac**.
4. Rapport des builds réussis, tests réalisés/non réalisés, liens vrais vers artefacts et TestFlight, logs utiles sans secrets.
5. Check-list de validation utilisateur et **attente explicite de validation visuelle** pour l'écran paysage.
6. Pas de destruction de la release existante ou des données, pas de certificats secrets dans le code ; réversibilité et versionnage.

### Critères minimaux de réception

- [ ] L'IPA a une taille raisonnable sans gros packs préinstallés ; écran inaugural « Installer les modèles » fonctionnel.
- [ ] Sarah Engine conserve les six familles ; pack OMR commercialement redistribuable et local validé.
- [ ] Chat ne renvoie pas du code brut à une question normale ; défaut modèle signalé clairement.
- [ ] Commande « Traduction Live » ouvre la bonne vue ; **paysage FR en haut et HE en bas validé avant développement final**.
- [ ] Watch SE 2 : interface très lisible, gros « Do » rond, repère 2 touches noires, Do rouge, note correcte détectée via MIDI iPhone, choix montre/iPhone/deux.
- [ ] QR code généré par PC et scanné sur iPhone ; app Microsoft Store liée seulement si publication vérifiée.
- [ ] Build iPhone + Watch réellement distribué par TestFlight en interne et testé sur la vraie montre.
