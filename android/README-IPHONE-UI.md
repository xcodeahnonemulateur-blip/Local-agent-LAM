# Sarah Android — interface issue du vrai projet iPhone

Référence : dépôt privé `200012Yoel/SarahAI`, état `8de7037f22d3fb54de09b385d98c7e69a7ea89ac`.
Le précédent projet `Local-LLM-Sarah.` était le moteur Python, pas l'application iPhone.

## Correspondance avec les sources iPhone

| Source iPhone | Adaptation Android |
| --- | --- |
| `Views/ChatScreenView.swift` | Fond noir et halo de couleur, barre supérieure, capsule d'agent centrée, composer séparé |
| `Models/AgentType.swift` | Sarah rose, Raphaël azur, Yohan bleu, Ethel rouge, Nathan violet, Tom vert |
| `Views/CommonStyles.swift` | Surfaces sombres avec dégradé, contour et coins arrondis ; rendu Android natif |
| `Views/MessageBar.swift` | Ajouter, champ extensible, dictée, action envoyer/vocal, accès au Studio pour Raphaël |
| `Views/ChatBubbleView.swift` | Bulle utilisateur bleue, réponse grise, véritable avatar Sarah, heure et lecture vocale |
| `Views/SidebarView.swift` | Tiroir à 84 % (maximum 360 dp), recherche, récents, épinglés, archives et nouveau chat |
| `Views/VoiceOrbModalView.swift` | Orbe rose avec gradients et anneaux, commandes vocales, réduction vers le chat |
| `Views/SettingsView.swift` | Réglages groupés et accès aux agents, à la voix et aux moteurs |

Les fichiers Java `MainActivity`, `PhoneStyle` et `ChatHistory` constituent le portage.
L'avatar vient directement de `SarahAvatar.imageset/SarahAvatar.jpg`.

## Amélioration demandée depuis la capture de l'émulateur

La structure retenue par l'utilisateur est conservée : menu circulaire, capsule d'agent,
fond noir et composer en bas. Un accueil propre à chaque agent remplit les nouvelles
discussions. Les actions ouvrent le Studio ou préparent réellement le champ de saisie.
Pour Raphaël, le Studio reste accessible dans l'accueil ; les raccourcis superflus de la barre supérieure sont supprimés.
Le halo est adouci et le placeholder se raccourcit. Les anciens échanges restent préservés.

## Adaptation au téléphone

Les espaces système et le clavier sont traités par les WindowInsets Android, sans déplacement fixe du composer.
Les cartes et bulles se mesurent selon la largeur disponible. Les écrans secondaires défilent.
Le geste Retour moderne utilise OnBackInvokedDispatcher sur Android 13+, avec repli pour Android 7 à 12.
Le thème, les discussions et les agents sont conservés localement. L'ancien historique `conversation_v2` est migré.

## Construire et essayer

Java 17, Android SDK 36 et Gradle 9.5.0 :

```powershell
.\gradlew.bat assembleDebug lintDebug bundleRelease
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.nathandev.localagent.debug/com.nathandev.localagent.MainActivity
```

Le package debug reste distinct de la release `com.nathandev.localagent`.
Le bundle local reste non signé. Le workflow de production signe et soumet la version avec les secrets GitHub existants, puis actualise le nom, le logo et les captures.

## Limites vérifiables

Il s'agit d'une adaptation native basée sur le code iPhone, sans comparaison pixel par pixel avec un simulateur iOS.
Le flou Material d'Apple est approché par les surfaces natives Android.
Le moteur léger et les écrans Android existants sont conservés ; porter l'interface ne porte pas les moteurs Apple.
Whisper, l'inférence GGUF, la traduction continue et MapKit ne sont pas annoncés comme opérationnels. La synchronisation PC authentifiée par QR carré est implémentée et testée contre le service Desktop.
La dictée et la lecture utilisent les services Android disponibles. L'audio réel doit encore être vérifié sur un téléphone.
Les pièces jointes affichent le nom sélectionné ; le moteur léger n'analyse pas leur contenu.
L'aperçu du Studio rend du HTML local sans exécuter JavaScript.

Les captures de l'émulateur et le compte rendu se trouvent dans `qa/`.
