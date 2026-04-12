repositories {
    maven("https://maven.shedaniel.me/")
    maven("https://maven.tterrag.com/")
    maven("https://raw.githubusercontent.com/Fuzss/modresources/main/maven/")
    maven("https://squiddev.cc/maven/")
    maven("https://maven.createmod.net")
    maven("https://mvn.devos.one/snapshots") // Registrate
}

architectury {
    common(rootProject.property("enabled_platforms").toString().split(","))
}

loom {
    accessWidenerPath = file("src/main/resources/wingscontracts.accesswidener")
}

//tasks.named("remapJar") {
//    enabled = false
//}

dependencies {
    // We depend on fabric loader here to use the fabric @Environment annotations and get the mixin dependencies
    // Do NOT use other classes from fabric loader
    modImplementation("net.fabricmc:fabric-loader:${rootProject.property("fabric_loader_version")}")
    // Remove the next line if you don't want to depend on the API
    modApi("dev.architectury:architectury:${rootProject.property("architectury_version")}")
    modApi("fuzs.forgeconfigapiport:forgeconfigapiport-common-neoforgeapi:${rootProject.property("forgeconfigapiport_version")}")
    val minecraftVersion = rootProject.property("minecraft_version")
    val ccTweakedMinecraftVersion = rootProject.property("cc_tweaked_minecraft_version")
    val ccTweakedVersion = rootProject.property("cc_tweaked_version")
    modCompileOnly("cc.tweaked:cc-tweaked-$ccTweakedMinecraftVersion-core-api:$ccTweakedVersion")
    modCompileOnly("cc.tweaked:cc-tweaked-$ccTweakedMinecraftVersion-common-api:$ccTweakedVersion")

    val createVersion = rootProject.property("create_version")
    val ponderVersion = rootProject.property("ponder_version")
    val registrateVersion = rootProject.property("registrate_version")
    modCompileOnly("com.simibubi.create:create-$minecraftVersion:$createVersion:slim") { isTransitive = false }
    modCompileOnly("net.createmod.ponder:Ponder-NeoForge-$minecraftVersion:$ponderVersion") { isTransitive = false }
    compileOnly("com.tterrag.registrate:Registrate:${registrateVersion}")

    implementation(kotlin("reflect"))
}