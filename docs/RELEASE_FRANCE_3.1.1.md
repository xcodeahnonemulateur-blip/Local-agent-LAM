# Local Agent LLM 3.1.1 — cible App Store France uniquement

Branche de préparation, pas un build validé ni une déclaration d’exemption.
Ne pas déclencher une compilation distante payante sans vérifier les crédits.

## Traduction locale
- Remplacer GoogleMLKit/Translate 8.0.0 (SarahIA/Podfile) et ses imports dans SarahIA/SarahIA/Services/TranslationEngine.swift par un moteur réellement local.
- Candidats Apache-2.0 : https://huggingface.co/Helsinki-NLP/opus-tatoeba-he-fr et https://huggingface.co/Helsinki-NLP/opus-mt-fr-he. Épingler les révisions et conserver licences et notices des modèles, tokeniseurs et runtimes.
- Convertir le modèle Marian encodeur/décodeur au format Core ML et vérifier la tokenisation SentencePiece, les tokens spéciaux, le décodage autoregressif et la parité avec les sorties de référence. Le moteur doit fonctionner sans réseau après téléchargement des poids. Ne pas confondre une traduction par dictionnaire avec la traduction neuronale.
- Maintenir le contrat asynchrone de TranslationEngine, les caches, l'indication du vrai moteur et les modes Live/Apple Watch. Conserver également EN↔FR. Tester les deux sens HE↔FR, EN↔FR, le mode avion, la gestion des packs et les erreurs; ne retirer ML Kit de la branche de livraison qu’après validation du remplaçant.
- Préserver les fonctionnalités existantes (IA locale, voix, montre), le bundle com.localagentllm.apple et son compagnon watchOS. Incrémenter la version iOS 3.1.1 et choisir un numéro de build inédit; conserver la compatibilité des versions iOS/watchOS exigée par Apple.

## Conformité et livraison
- Auditer tous les frameworks et dépendances effectivement embarqués dans l'archive iPhone + Watch, y compris leurs dépendances transitives. Ne pas désactiver HTTPS ou le chiffrement protecteur. Ne pas affirmer que ML Kit est la cause de l'exigence CCATS sans preuve.
- Ne définir ITSAppUsesNonExemptEncryption à false que si l'audit confirme l'exemption; ne pas renseigner de déclaration inexacte. Référence officielle : https://developer.apple.com/help/app-store-connect/reference/app-information/export-compliance-documentation-for-encryption
- Dans App Store Connect, sélectionner uniquement FRA et désactiver l'ajout automatique des futurs territoires. Le garde-fou de prepare-app-store.mjs bloque l'envoi à App Review lorsque ces conditions ne sont pas confirmées par l'API. Il ne modifie pas les territoires, ne déclare pas d'exemption et ne remplace pas les règles Apple.
- Compiler avec Xcode compatible App Store, signer les deux cibles avec les profils adéquats, vérifier l'archive, envoyer à TestFlight et tester réellement sur iPhone et Watch, puis soumettre ce build via le workflow App Store existant. Ne pas publier une IPA non testée.
- Les workflows existants référencent les secrets App Store Connect; ne jamais afficher ni copier leur contenu. Vérifier leur fonctionnement sans exposer les clés. Aucun crédit supplémentaire ni publication ne sont autorisés implicitement par ce document.
