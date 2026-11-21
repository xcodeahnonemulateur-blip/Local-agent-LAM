# Livrer Local Agent LLM depuis ton téléphone

## Tester sur iPhone et Apple Watch

1. Ouvre [la compilation iOS sur GitHub Actions](https://github.com/xcodeahnonemulateur-blip/Local-agent-LAM/actions/workflows/ios-build.yml) dans le navigateur de ton téléphone et connecte-toi à GitHub.
2. Lance **Run workflow** sur `main` et choisis `yes` pour `confirm_release`. Une compilation iOS réussie sur `main` déclenche aussi automatiquement la signature et l’envoi du build correspondant à Apple.
3. Suis [l’envoi TestFlight](https://github.com/xcodeahnonemulateur-blip/Local-agent-LAM/actions/workflows/testflight-upload.yml). Quand Apple rend la version disponible au groupe de test interne, installe-la depuis TestFlight sur ton iPhone et sur ta montre jumelée.

La compilation et l’envoi tournent sur GitHub. Tu peux fermer la page sur ton téléphone après le lancement. L’installation se fait ensuite dans TestFlight. Vérifie que TestFlight peut utiliser les données cellulaires sur ton iPhone. Le build complet contient des modèles pour l’usage hors ligne, ce qui peut représenter un téléchargement important.

## Préparer la version publique après les essais

Quand tu as terminé les tests, dis à Codex **« Le build [numéro] est bon, envoie-le à l’App Store »**. Codex peut lancer [App Store - Build testé](https://github.com/xcodeahnonemulateur-blip/Local-agent-LAM/actions/workflows/app-store-prepare.yml) sur `main` à partir du numéro de version et du numéro de build approuvés.

Le workflow crée ou met à jour le brouillon iOS et y associe précisément ce build. Avec `submit_for_review`, il soumet la version à App Review et sélectionne la publication après approbation d’Apple. Avec l’option par défaut, il prépare un brouillon. La fiche et les pièces exigées par Apple doivent être complètes pour la soumission.
