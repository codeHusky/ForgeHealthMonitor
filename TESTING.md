Testing HealthMonitor's Functionality
---

### Server Deadlocks
[MIMI](https://modrinth.com/mod/mimi) is a mod that allows playing music on a server via instruments and MIDI files. 
This mod, particularly in version `4.0.1` has a bug that causes some of its threads to remain running even when the
server has shut down, causing the server to hang and not automatically reboot. This is due to non-daemon threads
remaining running even after the mod shuts down. 

All you need to do to reproduce this situation is a test mod that spawns non-daemon threads and doesn't kill them.

### Main Thread Lag Spikes
Just install a large mod pack and abuse automation systems, spawn a bunch of mods, etc. Get as much stuff interacting 
as possible. It's very easy to cause these too, especially if you're generating terrain. This should trigger logs
almost immediately.

You can simulate this by running `Thread#sleep` on the main thread via some kind of test mod.


### Bad/Junk/Empty NBT added to items
[Create: Stuff & Additions](https://www.curseforge.com/minecraft/mc-mods/create-stuff-additions) is a mod that does what it says.
It also is an MCreator mod and includes some nasty behavior in `v2.0.4a` where any items in your off-hand will gain empty
NBT tags. This will make the blocks fail to stack if placed, broken, and then picked up again. This is due to a call to
`ItemStack#getOrCreateTagElement` or `ItemStack#getOrCreateTag`, which creates empty NBT in exchange for a "safe" call
to check NBT data.

To simulate this, do the aforementioned call on a Torch with no NBT. This will trigger a warning from Health Monitor.

### Rate Limiter
Forge has no built-in rate limiting for player connections. You can see this by just connecting and clicking cancel over and over.
The server will make no attempt to block you from reconnecting, which can cause (as you may imagine) numerous issues with
Denial of Service attacks. So, yeah, simulate it by just spamming connection fon your end and then trying to log back in
