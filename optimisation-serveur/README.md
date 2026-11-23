# Optimisation serveur — Paper 1.21.11

Conseils classés par impact, dans l'ordre où les appliquer.
**Fais une sauvegarde complète du serveur avant de toucher aux configs.**

> Ces réglages sont des valeurs de départ équilibrées (survie Vanilla-like).
> Ajuste selon ton gameplay (ex. plus de mobs si serveur PvP farms).

## 1. Prégénérer la map (le plus important, fait par ce plugin)

La quasi-totalité des lags Minecraft vient de la génération de chunks quand un
joueur avance. Une fois la prégen du plugin terminée (`/preload status` à 100 %),
le serveur ne génère plus rien en jeu : c'est le gain n°1.

## 2. Lancement Java (flags Aikar réglés pour Paper)

```
java -Xms4G -Xmx4G --add-modules=jdk.incubator.vector \
  -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC \
  -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 \
  -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 \
  -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 \
  -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 \
  -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 \
  -jar paper-1.21.11.jar --nogui
```

- `-Xms` = `-Xmx` (réservation complète des RAM, sinon pics de GC).
- `--add-modules=jdk.incubator.vector` : Paper utilise les instructions vectorielles pour la génération.
- Alloue 6–12 Go max (au-delà, le GC ralentit tout) — 4 Go suffisent jusqu'à ~20 joueurs avec la prégen faite.

## 3. `server.properties`

```properties
view-distance=7
simulation-distance=6
sync-chunk-writes=false
network-compression-threshold=512
```

- `view-distance=7` et `simulation-distance=6` : réduit massivement la charge
  (chunks chargés + ticks d'entités). Visuellement proche de 10 grâce aux nuages/FOV.
- `sync-chunk-writes=false` : écritures disque non bloquantes (gros gain I/O pendant la prégen).

## 4. `config/paper-world-defaults.yml`

```yaml
chunks:
  max-auto-save-chunks-per-tick: 8
  prevent-moving-into-unloaded-chunks: true
entities:
  mob-spawn-range: 5
hopper:
  cooldown-when-full: true
  disable-move-event: true
tick-rates:
  mob-spawner: 2
  sensor: 2
```

- `disable-move-event: true` : énorme gain sur les systèmes à hoppers
  (à laisser `false` uniquement si un plugin écoute `InventoryMoveItemEvent`, ex. certains plugins de tri/d'élevage).
- `max-auto-save-chunks-per-tick: 8` : étale la sauvegarde au lieu de pics de lag toutes les 5 min.

## 5. `bukkit.yml`

```yaml
spawn-limits:
  monsters: 40
  animals: 8
  ambient: 2
  water-ambient: 2
  water-animals: 3
ticks-per:
  autosave: 6000
```

- `monsters: 40` au lieu de 70 par défaut : la moitié des MSPT part dans les mobs.
- `autosave: 6000` garde le comportement par défaut (5 min), compatible avec l'étalement Paper ci-dessus.

## 6. `spigot.yml`

```yaml
world-settings:
  default:
    merge-radius:
      item: 3.5
      exp: 4.0
    item-despawn-rate: 6000
    arrow-despawn-rate: 300
```

## 7. À éviter

- Les plugins "anti-lag tout-en-un" qui désactivent des mechanics (ClearLagg, etc. en agressif) : avec la prégen + réglages ci-dessus ils n'apportent presque rien et causent des bugs.
- Overclocker `view-distance` au-delà de 8 "juste au cas où".
- `-Xmx` énorme (16 Go+) : le GC G1 perd en efficacité.

## Vérification après les changements

```
/paper tps          # TPS et MSPT
/paper tickreport   # détail de la consommation
/preload status     # progression de la prégen
```

Objectif : MSPT moyen < 40 ms même avec des joueurs, et < 50 ms pendant la prégen
(le plugin reduit alors automatiquement sa charge grâce à la régulation adaptative).
