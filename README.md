# Ren Script Loader - a krs fork (i cant develop anymore)
Im not gonna add a full explnation to a dead client. Sorry (but i will tell you basic detail)



# IDE & Build System
IDE Used: IntelliJ
Build system: Gradle (JDK-25+ MAX)

For people with Minecraft Development installed: Click `Run Client`

For people using raw gradle:
In the folder, type in `./gradlew genSources`. Once that is done, you can iterate on the client, or run it directly via
`./gradlew runClient`. Building the client is simple, you can do `./gradlew build`, then look in `/builds`. a jar named `RenScriptLoader` should be there.

# Folders

### Client related

`/src/client` - The client source
 - `/configs` - Config system
 - `/events` - All mapped Minecraft events (like onWorld, onTick, onRender, etc)
 - `/hacks` - All module catagories + methods + logic
 - `/screen` - All visual things (not related to render in hacks)
 - `/mixin` - Where all mixins are and their injectors.

### Other
 - `/builds` - The directory where all RenScriptLoader builds get put
 - `/discord-rpc` - The discord RPC implmention i used.