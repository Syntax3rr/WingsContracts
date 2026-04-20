plugins {
    id("com.github.johnrengelman.shadow")
}

architectury {
    platformSetupLoomIde()
    neoForge()
}

loom {
    accessWidenerPath.set(project(":common").loom.accessWidenerPath)
    noIntermediateMappings()
}

val common: Configuration by configurations.creating
val shadowCommon: Configuration by configurations.creating
val developmentNeoForge: Configuration by configurations.getting

configurations {
    compileOnly.configure { extendsFrom(common) }
    runtimeOnly.configure { extendsFrom(common) }
    developmentNeoForge.extendsFrom(common)
}

base.archivesName = "wingscontracts-neoforge"

repositories {
    // KFF
    maven {
        name = "Kotlin for Forge"
        setUrl("https://thedarkcolour.github.io/KotlinForForge/")
    }
    maven("https://squiddev.cc/maven/")
    maven("https://maven.createmod.net")
    maven("https://maven.tterrag.com/")
    maven("https://mvn.devos.one/snapshots") // Registrate
    maven("https://maven.neoforged.net/releases/")
}

dependencies {
    neoForge("net.neoforged:neoforge:${rootProject.property("neoforge_version")}")
    // Remove the next line if you don't want to depend on the API
    modApi("dev.architectury:architectury-neoforge:${rootProject.property("architectury_version")}")

    common(project(":common", "namedElements")) { isTransitive = false }
    shadowCommon(project(":common", "transformProductionNeoForge")) { isTransitive = false }

    // Kotlin For Forge
    implementation("thedarkcolour:kotlinforforge-neoforge:${rootProject.property("kotlin_for_forge_version")}")

    val minecraftVersion = rootProject.property("minecraft_version")
    val ccTweakedMinecraftVersion = rootProject.property("cc_tweaked_minecraft_version")
    val ccTweakedVersion = rootProject.property("cc_tweaked_version")
    compileOnly("cc.tweaked:cc-tweaked-$ccTweakedMinecraftVersion-forge-api:$ccTweakedVersion")

    val createVersion = rootProject.property("create_version")
    val ponderVersion = rootProject.property("ponder_version")
    val registrateVersion = rootProject.property("registrate_version")
    compileOnly("com.simibubi.create:create-$minecraftVersion:$createVersion:slim") { isTransitive = false }
    compileOnly("net.createmod.ponder:Ponder-NeoForge-$minecraftVersion:$ponderVersion") { isTransitive = false }
    compileOnly("com.tterrag.registrate:Registrate:${registrateVersion}")
}

tasks.processResources {
    inputs.property("group", rootProject.property("maven_group"))
    inputs.property("version", project.version)

    filesMatching("META-INF/neoforge.mods.toml") {
        expand(mapOf(
            "group" to rootProject.property("maven_group"),
            "version" to project.version,

            "mod_id" to rootProject.property("mod_id"),
            "minecraft_version" to rootProject.property("minecraft_version"),
            "architectury_version" to rootProject.property("architectury_version"),
            "kotlin_for_forge_version" to rootProject.property("kotlin_for_forge_version"),
            "create_version_range" to rootProject.property("create_version_range"),
            "cc_tweaked_version" to rootProject.property("cc_tweaked_version")
        ))
    }
}

tasks.shadowJar {
    exclude("fabric.mod.json")
    exclude("architectury.common.json")
    configurations = listOf(shadowCommon)
    archiveClassifier.set("dev-shadow")
}

tasks.remapJar {
    injectAccessWidener.set(true)
    inputFile.set(tasks.shadowJar.get().archiveFile)
    dependsOn(tasks.shadowJar)
    archiveClassifier.set(null as String?)
    atAccessWideners.add(loom.accessWidenerPath.get().asFile.name)
}

tasks.jar {
    archiveClassifier.set("dev")
}

tasks.sourcesJar {
    val commonSources = project(":common").tasks.getByName<Jar>("sourcesJar")
    dependsOn(commonSources)
    from(commonSources.archiveFile.map { zipTree(it) })
}

components.getByName("java") {
    this as AdhocComponentWithVariants
    this.withVariantsFromConfiguration(project.configurations["shadowRuntimeElements"]) {
        skip()
    }
}