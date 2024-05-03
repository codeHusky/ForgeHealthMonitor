Health Monitor (Forge)
---

Adds a number of helpful utilities for Forge server owners to make diagnosing issues and general server administration easier.

Features
---
- Watches for hangs on the main server thread, and prints stack traces when the TPS drops too much for diagnostic purposes
  - Reports lagspikes with stacktraces, allowing you to pinpoint laggy mods and assist mod devs in optimizations
- Watches for high entity counts (>8000) and intervenes when theres an excessive amount of a certain entity type (>1500)
  - Removes these excess entities just before they're about to be ticked, which prevents server deadlocks
- Translates "obfuscated" Minecraft names (`net.minecraft.class_3435_`, etc) to names useful to Forge developers
  - Makes it easier to diagnose crashes, errors, and lag at a glance
- Adds `prevent-moving-into-unloaded-chunks` functionality from Paper to avoid massive server lag when generating terrain
- Logs when mods add NBT data to specifically `minecraft:torch`, useful for debugging mods that use NBT wrong
- Adds `connection-throttle` functionality from Bukkit/Spigot/Paper to thwart basic Denial of Service (DoS/DDoS) attacks

License
---
FHM is licensed under LGPL v3.0. Please review the terms of the license [here](https://github.com/codeHusky/ForgeHealthMonitor/blob/master/LICENSE) if you have any concerns.

Some components of this software are under different licenses. Please review those licenses in the Credits section.

Outside of the license, just be nice and do good.


Credits
---
```
Code for deobfuscating stacktraces originally from StackDeobfuscator (https://github.com/booky10/StackDeobfuscator)
By booky10 and project contributors, licensed under LGPL v3.0.
Adapted for use with FML (and probably NeoForge) by this project and its contributors
```

```
Various patches have been referenced or pulled from the Paper project (https://github.com/PaperMC/Paper)
By various project contributors under various licenses (https://github.com/PaperMC/Paper/blob/master/LICENSE.md)
Adapted for use with FML (and probably NeoForge) by this project and its contributors
```