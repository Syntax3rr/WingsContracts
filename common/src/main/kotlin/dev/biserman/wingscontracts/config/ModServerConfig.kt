package dev.biserman.wingscontracts.config

import dev.biserman.wingscontracts.WingsContractsMod
import net.neoforged.neoforge.common.ModConfigSpec

class ModServerConfig(builder: ModConfigSpec.Builder) {
    val denominations: ModConfigSpec.ConfigValue<String>
    val abyssalContractGrowthFunction: ModConfigSpec.EnumValue<GrowthFunctionOptions>
    val abyssalContractDecayFunction: ModConfigSpec.EnumValue<DecayFunctionOptions>
    val abyssalContractsPoolRefreshPeriodMs: ModConfigSpec.LongValue
    val abyssalContractsPoolOptions: ModConfigSpec.IntValue
    val abyssalContractsPoolPicks: ModConfigSpec.IntValue
    val abyssalContractsPoolPicksCap: ModConfigSpec.IntValue
    val allowBlankAbyssalContractUse: ModConfigSpec.BooleanValue
    val disableDefaultContractOptions: ModConfigSpec.BooleanValue
    val variance: ModConfigSpec.DoubleValue
    val replaceRewardWithRandomRate: ModConfigSpec.DoubleValue
    val replaceRewardWithRandomFactor: ModConfigSpec.DoubleValue
    val rarityThresholdsString: ModConfigSpec.ConfigValue<String>
    val contractPortalInputSlots: ModConfigSpec.IntValue
    val boundContractLossRate: ModConfigSpec.DoubleValue
    val boundContractRequiresTwoPlayers: ModConfigSpec.BooleanValue
    val announceCycleLeaderboard: ModConfigSpec.IntValue

    // Contract Defaults
    // Generation
    val defaultRewardMultiplier: ModConfigSpec.DoubleValue
    val defaultUnitsDemandedMultiplier: ModConfigSpec.DoubleValue
    val defaultCountPerUnitMultiplier: ModConfigSpec.DoubleValue
    val defaultAuthor: ModConfigSpec.ConfigValue<String>

    // Lifetime
    val defaultCycleDurationMs: ModConfigSpec.LongValue
    val defaultExpiresIn: ModConfigSpec.ConfigValue<Int>
    val defaultMaxLifetimeUnits: ModConfigSpec.ConfigValue<Int>

    // Leveling
    val defaultMaxLevel: ModConfigSpec.ConfigValue<Int>
    val defaultQuantityGrowthFactor: ModConfigSpec.DoubleValue

    // Decay
    val defaultDecayEnabled: ModConfigSpec.BooleanValue
    val defaultDecayCyclesPerEvent: ModConfigSpec.IntValue
    val defaultDecayLevelsPerEvent: ModConfigSpec.IntValue
    val defaultDecayPercentPerEvent: ModConfigSpec.DoubleValue
    val defaultDecayMinLevel: ModConfigSpec.IntValue

    init {
        builder.push("General")
        denominations =
            builder.comment(
                """
                Comma-separated list of reward items that can be automatically converted by portals into other denominations.
                Multiple lists may be provided, separated by semicolons.
                DO NOT use the same item in multiple denomination lists. The game will crash when a portal attempts to use that reward.
                """.trimIndent()
            ).define("denominations", defaultDenominations)

        abyssalContractGrowthFunction = builder.comment(
            """
            The function that determines how a contract's quantity demanded increases as it levels up.
            LINEAR: unitsDemanded = baseUnitsDemanded + baseUnitsDemanded * (growthFactor - 1) * (level - 1)
            EXPONENTIAL: unitsDemanded = baseUnitsDemanded * growthFactor ** (level - 1)
            """.trimIndent()
        ).defineEnum("abyssalContractGrowthFunction", GrowthFunctionOptions.EXPONENTIAL)

        abyssalContractDecayFunction = builder.comment(
            """
            The function that determines how a contract loses level when its cycle ends without being completed.
            Individual contracts can override this via a "decayFunction" tag in their datapack JSON ("FIXED" or "PERCENTAGE").
            FIXED: each decay event removes defaultDecayLevelsPerEvent levels.
            PERCENTAGE: each decay event removes defaultDecayPercentPerEvent fraction of the current level (floored).
              Always loses at least 1 level when defaultDecayPercentPerEvent > 0; set it to 0 to disable PERCENTAGE decay.
            """.trimIndent()
        ).defineEnum("abyssalContractDecayFunction", DecayFunctionOptions.FIXED)

        abyssalContractsPoolRefreshPeriodMs =
            builder.comment(
                """
                The default time for the Abyssal Contracts pool to refresh, in milliseconds. E.g.: 86400000 for one day, 604800000 for one week.
                If set to zero or negative one, the Abyssal Contracts Pool never refreshes.
                """
            )
                .defineInRange("abyssalContractsPoolRefreshPeriodMs", 86400000L, -1, Long.MAX_VALUE)

        abyssalContractsPoolOptions =
            builder.comment("Determines how many Abyssal Contracts are available in the pool at any one time. Set to zero to disable the Abyssal Contracts pool.")
                .defineInRange("abyssalContractsPoolOptions", 10, 0, 10)

        abyssalContractsPoolPicks =
            builder.comment("Determines how many picks each player gets from the Abyssal Contracts pool per refresh period.")
                .defineInRange("abyssalContractsPoolPicks", 1, 0, Int.MAX_VALUE)

        abyssalContractsPoolPicksCap =
            builder.comment("Determines the maximum number of picks from the Abyssal Contracts pool each player can have saved up.")
                .defineInRange("abyssalContractsPoolPicksCap", 1, 0, Int.MAX_VALUE)

        allowBlankAbyssalContractUse =
            builder.comment("If true, Blank Abyssal Contracts can be right-clicked to become a randomized usable Abyssal Contract.")
                .define("allowBlankAbyssalContractUse", true)

        disableDefaultContractOptions =
            builder.comment(
                """
                If true, skip all contract data files ending in "_default.json".
                Use this if you want to replace the default contract options with a custom data pack.
                """.trimIndent()
            ).define("disableDefaultContractOptions", false)

        variance =
            builder.comment(
                """
                The maximum distance a value of an Abyssal Contract from the pool can generate from its default values for countPerUnit, baseUnitsDemanded, and reward.
                Example: if variance is set to 0.2 and you have a contract in the pool configured to convert 10 iron ingots → 5 emeralds, then you might generate any of the following contracts:
                 - 8 iron ingots → 4 emeralds
                 - 8 iron ingots → 6 emeralds
                 - 12 iron ingots → 4 emeralds
                 - 12 iron ingots → 6 emeralds
                 - 11 iron ingots → 5 emeralds
                 - ... or anything else in-between
                """.trimIndent()
            )
                .defineInRange("variance", 0.33, 0.0, Double.MAX_VALUE)

        replaceRewardWithRandomRate =
            builder.comment("This percentage of Abyssal Contracts generated that are set to the default reward currency will instead have their reward switched to a random input from another contract.")
                .defineInRange("replaceRewardWithRandomRate", 0.8, 0.0, 1.0)

        replaceRewardWithRandomFactor =
            builder.comment("The reward from an Abyssal Contract with its reward randomly replaced will have its count multiplied by this factor.")
                .defineInRange("replaceRewardWithRandomFactor", 0.5, 0.0, Double.MAX_VALUE)

        rarityThresholdsString =
            builder.comment(
                """
                The max-level reward value necessary to reach rarities Uncommon, Rare, and Epic respectively as a comma-separated list of integers.
                You can set this to "" to set all Abyssal Contracts to Common by default.
                """.trimIndent()
            )
                .define("rarityThresholds", "16000,32000,64000")

        contractPortalInputSlots =
            builder.comment("Determines how many unconverted stacks of input items a Contract Portal can hold at once.")
                .defineInRange("contractPortalInputSlots", 54, 1, 1024)

        boundContractLossRate =
            builder.comment("What percentage of the time should bound contract item exchanges fail and destroy the swapped items?")
                .defineInRange("boundContractLossRate", 0.1, 0.0, 1.0)

        boundContractRequiresTwoPlayers =
            builder.comment("If true, a different player must place each end of the bound contract into its respective portal in order for the exchange to work.")
                .define("boundContractRequiresTwoPlayers", false)

        announceCycleLeaderboard =
            builder.comment("If non-zero, this number of players from the top of this cycle's contract score leaderboard will have their scores announced in chat at the end of the cycle.")
                .defineInRange("announceCycleLeaderboard", 0, 0, Int.MAX_VALUE)

        builder.pop()
        builder.push("Contract Defaults")

        // Generation
        defaultRewardMultiplier =
            builder.comment("Datapacked contracts with an unspecified or integer reward will have their reward count multiplied by this factor, then rounded (to a minimum of 1).")
                .defineInRange("defaultRewardMultiplier", 1.0, 0.0, Double.MAX_VALUE)

        defaultUnitsDemandedMultiplier =
            builder.comment("All new Abyssal Contracts pulled from the pool will have their base units demanded multiplied by this factor, then rounded (to a minimum of 1).")
                .defineInRange("defaultUnitsDemandedMultiplier", 0.25, 0.0, Double.MAX_VALUE)

        defaultCountPerUnitMultiplier =
            builder.comment("All new Abyssal Contracts pulled from the pool will have their count demanded per unit multiplied by this factor, then rounded (to a minimum of 1).")
                .defineInRange("defaultCountPerUnitMultiplier", 1.0, 0.0, Double.MAX_VALUE)

        defaultAuthor =
            builder.comment(
                """
                The default author name for Abyssal Contracts.
                While this value is a lang key by default, you can enter the author name here directly if the name is the same in all languages.
                This is purely cosmetic and used for theming—feel free to set it to \"\" to remove the default Abyssal Contract author entirely.
                """.trimIndent()
            )
                .define("defaultAuthor", "${WingsContractsMod.MOD_ID}.default_author")

        // Lifetime
        defaultCycleDurationMs =
            builder.comment("The default length of a cycle period, in milliseconds. E.g.: 86400000 for one day, 604800000 for one week.")
                .defineInRange("defaultCycleDurationMs", 86400000L, 60_000, Long.MAX_VALUE)

        defaultExpiresIn =
            builder.comment(
                """
                The default number of cycles until an Abyssal Contract expires and becomes unusable.
                When set to a negative value, contracts never expire.
                """.trimIndent()
            ).define("defaultExpiresIn", -1)

        defaultMaxLifetimeUnits =
            builder.comment(
                """
                The default cap on a contract's total units fulfilled before it deactivates.
                When set to 0 or a negative value, contracts have no lifetime-units cap.
                """.trimIndent()
            ).define("defaultMaxLifetimeUnits", 0)

        // Leveling
        defaultMaxLevel =
            builder.comment("The default max level for Abyssal Contracts. If negative or zero, the contract will have no max level. If one, Abyssal Contracts will never level up.")
                .define("defaultMaxLevel", 10)

        defaultQuantityGrowthFactor = builder.comment(
            """
            The default quantity growth factor for Abyssal Contracts.
            See abyssalContractGrowthFunction above to see how this is used.
            """.trimIndent()
        ).defineInRange("defaultQuantityGrowthFactor", 2.0, 0.00001, 100.0)

        // Decay
        defaultDecayEnabled =
            builder.comment(
                """
                If true, contracts that finish a cycle without being completed will lose levels over time.
                """.trimIndent()
            ).define("defaultDecayEnabled", false)

        defaultDecayCyclesPerEvent =
            builder.comment(
                """
                How many missed cycles between decay events.
                1 = decay each cycle that ends incomplete; 4 = decay once every 4 missed cycles.
                Set to 0 to disable decay regardless of defaultDecayEnabled.
                """.trimIndent()
            ).defineInRange("defaultDecayCyclesPerEvent", 1, 0, Int.MAX_VALUE)

        defaultDecayLevelsPerEvent =
            builder.comment("if FIXED decay, the number of levels that are removed per decay event. Set to 0 to disable FIXED decay.")
                .defineInRange("defaultDecayLevelsPerEvent", 1, 0, Int.MAX_VALUE)

        defaultDecayPercentPerEvent =
            builder.comment(
                """
                if PERCENTAGE decay, the fraction of the current level removed per decay event (floored).
                Always loses at least 1 level when nonzero; set to 0 to disable PERCENTAGE decay.
                """.trimIndent()
            ).defineInRange("defaultDecayPercentPerEvent", 0.25, 0.0, 1.0)

        defaultDecayMinLevel =
            builder.comment(
                """
                Floor that decay cannot drop the contract below.
                Set to 1 (default) to roll back gains without ever expiring the contract.
                Set to 0 to allow decay to drop the contract to level 0, deactivating it like an expired contract.
                """.trimIndent()
            ).defineInRange("defaultDecayMinLevel", 1, 0, Int.MAX_VALUE)

        builder.pop()
    }

    companion object {
        val defaultDenominations = """
            minecraft:emerald = 1, 
            minecraft:emerald_block = 9;
            
            minecraft:lapis_lazuli = 1,
            minecraft:lapis_block = 9;
            
            minecraft:gold_nugget = 1,
            minecraft:gold_ingot = 9,
            minecraft:gold_block = 81;
            
            minecraft:diamond = 1, 
            minecraft:diamond_block = 9;
            
            numismatics:spur = 1,
            numismatics:bevel = 8,
            numismatics:sprocket = 16,
            numismatics:cog = 64,
            numismatics:crown = 512,
            numismatics:sun = 4096;
        """.replace(Regex("\\s"), "").trimIndent()
    }
}