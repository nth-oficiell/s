# ChunkPreloader (Paper 1.21.11)

Plugin Paper 1.21.11 (Java 21) :

1. **Prégénération** d'un carré de **15 000 x 15 000 blocs** dans le monde normal
   (demi-taille 7 500 autour du centre `0, 0`, soit **938 x 938 chunks**).
2. **Le nether est divisé par 8** (échelle 1:8) : demi-taille 937, soit
   **1 874 x 1 874 blocs** (118 x 118 chunks). L'end n'est pas pris en charge.
3. **`/rtp <world|nether>`** : téléportation aléatoire dans la même zone
   (±7500 blocs en surface, ±937 dans le nether), avec recherche d'un bloc sûr
   (pas de lave, pas de chute, pas de toit du nether).
4. **Optimisé pour le serveur** : génération 100 % asynchrone, charge adaptative
   qui monte/descend selon les TPS du serveur (pause automatique si besoin),
   sauvegardes espacées — le serveur reste jouable pendant toute la prégen.

## Compilation

Prérequis : **JDK 21** et **Maven 3.9+**.

```bash
mvn package
```

Le jar est généré dans `target/ChunkPreloader-1.0.0.jar`. Le copier dans le
dossier `plugins/` d'un serveur Paper 1.21.11.

## Commandes

| Commande | Description | Permission |
|---|---|---|
| `/preload start [world|nether]` | Démarre la prégen (les deux mondes si aucun argument) | `chunkpreloader.admin` (op) |
| `/preload stop [world|nether]` | Arrête la prégen et sauvegarde la progression | `chunkpreloader.admin` |
| `/preload status` | Progression, %, vitesse et ETA par monde | `chunkpreloader.admin` |
| `/rtp <world|nether>` | Téléportation aléatoire sûre | `chunkpreloader.rtp` (tous) |

## Configuration (`config.yml`)

- `overworld-half-size-blocks: 7500` — demi-taille de la zone (15 000 x 15 000).
- `nether-divisor: 8` — le nether = surface ÷ 8.
- `auto-start: false` — lancer automatiquement la prégen au démarrage.
- `max-outstanding-chunks: 64` — plafond de chunks générés en parallèle.
- `adaptive: true` — **régulation adaptative de la charge** :
  - démarrage doux (un quart du plafond) puis montée progressive tant que le TPS est sain (≥ 19.5) ;
  - la charge est **coupée en deux** dès que le TPS passe sous `min-tps`, et la prégen se met en pause si le TPS chute de plus de 2 points sous le seuil.
  - Résultat : le serveur garde ses TPS et les joueurs ne sentent rien, tout en saturant les threads de génération dès que possible.
- `min-tps: 17.0` — seuil de recul/pause automatique.
- `worlds.overworld` / `worlds.nether` — noms des mondes.

## Reprise après redémarrage

La progression est sauvegardée dans `plugins/ChunkPreloader/progress/<monde>.yml`.
Relancer `/preload start` (ou `auto-start: true`) reprend là où la prégen
s'était arrêtée — les chunks déjà générés sont sautés.

## Volumétrie à prévoir

- Monde normal : 938² ≈ **879 000 chunks**.
- Nether : 118² ≈ **13 900 chunks**.

À raison de quelques dizaines de chunks/seconde (selon CPU), comptez
plusieurs heures pour la surface. La prégen tourne en tâche asynchrone et se
met en pause si les TPS chutent ; les joueurs peuvent jouer pendant ce temps.
