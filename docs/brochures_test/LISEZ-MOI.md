# Où est passée la brochure de test

`brochure_test_partenaire_CHU_Valbrune.pdf` a été déplacée le 27/11/2026 vers :

```
backend/src/test/resources/brochures-de-test/brochure_test_partenaire_CHU_Valbrune.pdf
```

**Pourquoi.** `ControleBrochureTest` la lisait par `Path.of("..", "docs", "brochures_test", …)`,
donc **hors de son module**. Le miroir de déploiement ne reçoit que `git archive HEAD:backend` :
ce test y échouait, et lui seul, avec un message qui ne désignait pas la cause. L'intégration
continue de `EXP‑3`, branchée sur le miroir, aurait rougi sans que personne comprenne pourquoi.
C'est le lot `QUA‑1a`.

Elle se lit désormais dans le classpath du module, à
`/brochures-de-test/brochure_test_partenaire_CHU_Valbrune.pdf`.

Les deux autres fichiers de ce dossier — l'exemple de brochure Optimi Santé et le comparatif
avant/après — restent ici : ce sont des documents de travail des lots `BR`, pas des pièces de
test exécutées par du code.
