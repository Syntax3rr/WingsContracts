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

    // Celestial Contracts
    // General
    val celestialCurrencyAnchor: ModConfigSpec.ConfigValue<String>
    val celestialContractGrowthFunction: ModConfigSpec.EnumValue<GrowthFunctionOptions>

    // Random shape rolls
    val celestialRandomBaseUnitsDemandedMin: ModConfigSpec.IntValue
    val celestialRandomBaseUnitsDemandedMax: ModConfigSpec.IntValue
    val celestialRandomMaxLifetimeUnitsMin: ModConfigSpec.IntValue
    val celestialRandomMaxLifetimeUnitsMax: ModConfigSpec.IntValue
    val celestialRandomUnlimitedLifetimeUnitsChance: ModConfigSpec.DoubleValue
    val celestialRandomMaxLevelMin: ModConfigSpec.IntValue
    val celestialRandomMaxLevelMax: ModConfigSpec.IntValue
    val celestialRandomQuantityGrowthFactorMin: ModConfigSpec.DoubleValue
    val celestialRandomQuantityGrowthFactorMax: ModConfigSpec.DoubleValue
    val celestialRandomCycleDurationOptionsMs: ModConfigSpec.ConfigValue<String>
    val celestialRandomCycleDurationWeights: ModConfigSpec.ConfigValue<String>
    val celestialRandomExpiresInMin: ModConfigSpec.IntValue
    val celestialRandomExpiresInMax: ModConfigSpec.IntValue
    val celestialRandomNeverExpiresChance: ModConfigSpec.DoubleValue
    val celestialFiniteLifetimeUnitsCycleSuppressionFactor: ModConfigSpec.DoubleValue

    // Cost formula
    val celestialVariance: ModConfigSpec.DoubleValue
    val celestialBaseCostMultiplier: ModConfigSpec.DoubleValue
    val celestialCostPerExtraLifetimeUnitFactor: ModConfigSpec.DoubleValue
    val celestialCostPerExtraLevelFactor: ModConfigSpec.DoubleValue
    val celestialCostPerExtraExpiryFactor: ModConfigSpec.DoubleValue
    val celestialUnlimitedLifetimeUnitsMultiplier: ModConfigSpec.DoubleValue
    val celestialUnlimitedNoCycleMultiplier: ModConfigSpec.DoubleValue
    val celestialNeverExpiresMultiplier: ModConfigSpec.DoubleValue
    val celestialCycleDurationCostFactors: ModConfigSpec.ConfigValue<String>
    val celestialUnlimitedLifetimeUnitsRarityCap: ModConfigSpec.IntValue
    val celestialRarityThresholdsString: ModConfigSpec.ConfigValue<String>

    // Reroll
    val celestialRerollCostPenaltyFactor: ModConfigSpec.DoubleValue
    val celestialRerollLightningStatWeights: ModConfigSpec.ConfigValue<String>
    val celestialRerollFireStatWeights: ModConfigSpec.ConfigValue<String>
    val celestialEnableLightningReroll: ModConfigSpec.BooleanValue
    val celestialEnableFireReroll: ModConfigSpec.BooleanValue
    val celestialDefaultMaxRerolls: ModConfigSpec.IntValue

    // Target replacement
    val celestialReplaceCurrencyTargetRate: ModConfigSpec.DoubleValue
    val celestialReplaceCurrencyTargetFactor: ModConfigSpec.DoubleValue

    // Distribution
    val celestialRarityBias: ModConfigSpec.DoubleValue
    val disableDefaultCelestialContractOptions: ModConfigSpec.BooleanValue
    val allowBlankCelestialContractUse: ModConfigSpec.BooleanValue
    val celestialDefinedContractChance: ModConfigSpec.DoubleValue
    val celestialContractInAbyssalPoolChance: ModConfigSpec.DoubleValue
    val celestialWanderingTraderChance: ModConfigSpec.DoubleValue
    val celestialWanderingTraderPriceMultiplier: ModConfigSpec.DoubleValue
    val celestialLootInjectionTables: ModConfigSpec.ConfigValue<String>
    val celestialLootInjectionWeight: ModConfigSpec.IntValue

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
        builder.push("Celestial Contracts")

        // General
        celestialCurrencyAnchor = builder.comment(
            """
            The currency item every random Celestial Contract demands as payment.
            Should be the smallest unit of a denomination group from `denominations` (e.g. minecraft:emerald, with emerald_block as a higher denomination).
            Fully-defined celestial contracts ignore this and use their own targets.
            """.trimIndent()
        ).define("celestialCurrencyAnchor", "minecraft:emerald")

        celestialContractGrowthFunction = builder.comment(
            "The function used by Celestial Contracts that opt into leveling. Mirrors abyssalContractGrowthFunction."
        ).defineEnum("celestialContractGrowthFunction", GrowthFunctionOptions.EXPONENTIAL)

        // Random shape rolls
        celestialRandomBaseUnitsDemandedMin =
            builder.comment("Lower bound on randomly-rolled baseUnitsDemanded (units to fulfill per cycle, at level 1) for celestial contracts. Pool entries can override via `shape.baseUnitsDemanded`.")
                .defineInRange("celestialRandomBaseUnitsDemandedMin", 1, 1, Int.MAX_VALUE)
        celestialRandomBaseUnitsDemandedMax =
            builder.comment("Upper bound on randomly-rolled baseUnitsDemanded for celestial contracts.")
                .defineInRange("celestialRandomBaseUnitsDemandedMax", 1, 1, Int.MAX_VALUE)

        celestialRandomMaxLifetimeUnitsMin =
            builder.comment("Lower bound on randomly-rolled maxLifetimeUnits for celestial contracts.")
                .defineInRange("celestialRandomMaxLifetimeUnitsMin", 1, 1, Int.MAX_VALUE)
        celestialRandomMaxLifetimeUnitsMax =
            builder.comment("Upper bound on randomly-rolled maxLifetimeUnits for celestial contracts.")
                .defineInRange("celestialRandomMaxLifetimeUnitsMax", 20, 1, Int.MAX_VALUE)
        celestialRandomUnlimitedLifetimeUnitsChance =
            builder.comment("Probability that a celestial contract rolls maxLifetimeUnits=0 (unlimited). Multiplied into the cost via celestialUnlimitedLifetimeUnitsMultiplier.")
                .defineInRange("celestialRandomUnlimitedLifetimeUnitsChance", 0.02, 0.0, 1.0)

        celestialRandomMaxLevelMin =
            builder.comment("Lower bound on randomly-rolled maxLevel for celestial contracts.")
                .defineInRange("celestialRandomMaxLevelMin", 1, 1, Int.MAX_VALUE)
        celestialRandomMaxLevelMax =
            builder.comment("Upper bound on randomly-rolled maxLevel for celestial contracts. Default = 1, so random celestials don't level by default.")
                .defineInRange("celestialRandomMaxLevelMax", 1, 1, Int.MAX_VALUE)
        celestialRandomQuantityGrowthFactorMin =
            builder.comment("Lower bound on quantityGrowthFactor when celestialRandomMaxLevelMax > 1.")
                .defineInRange("celestialRandomQuantityGrowthFactorMin", 1.5, 1.0, 100.0)
        celestialRandomQuantityGrowthFactorMax =
            builder.comment("Upper bound on quantityGrowthFactor when celestialRandomMaxLevelMax > 1.")
                .defineInRange("celestialRandomQuantityGrowthFactorMax", 2.5, 1.0, 100.0)

        celestialRandomCycleDurationOptionsMs = builder.comment(
            """
            Comma-separated list of discrete cycle-duration options (in milliseconds) for celestial contracts.
            0 means no cycle (one-shot until maxLifetimeUnits).
            Defaults: 0 (none), 1800000 (30m), 3600000 (1h), 10800000 (3h), 21600000 (6h), 43200000 (12h), 86400000 (1d), 259200000 (3d), 604800000 (7d).
            """.trimIndent()
        ).define(
            "celestialRandomCycleDurationOptionsMs",
            "0,1800000,3600000,10800000,21600000,43200000,86400000,259200000,604800000"
        )
        celestialRandomCycleDurationWeights = builder.comment(
            "Comma-separated weights for the cycle-duration options. Must have the same length as celestialRandomCycleDurationOptionsMs."
        ).define("celestialRandomCycleDurationWeights", "3,1,1,1,2,2,3,1,1")

        celestialRandomExpiresInMin =
            builder.comment("Lower bound on randomly-rolled expiresIn (in cycles) for cycled celestial contracts.")
                .defineInRange("celestialRandomExpiresInMin", 3, 0, Int.MAX_VALUE)
        celestialRandomExpiresInMax =
            builder.comment("Upper bound on randomly-rolled expiresIn (in cycles) for cycled celestial contracts.")
                .defineInRange("celestialRandomExpiresInMax", 10, 0, Int.MAX_VALUE)
        celestialRandomNeverExpiresChance =
            builder.comment("Probability of rolling expiresIn=-1 (never expires) when the contract is cycled. Multiplied into the cost via celestialNeverExpiresMultiplier.")
                .defineInRange("celestialRandomNeverExpiresChance", 0.1, 0.0, 1.0)

        celestialFiniteLifetimeUnitsCycleSuppressionFactor = builder.comment(
            """
            When a celestial rolls a finite maxLifetimeUnits (>0), non-zero cycle-duration options have their weights multiplied by this factor before the cycle is picked.
            Set to 1.0 to disable the suppression; 0.0 forbids cycles for finite-lifetime contracts entirely.
            """.trimIndent()
        ).defineInRange("celestialFiniteLifetimeUnitsCycleSuppressionFactor", 0.05, 0.0, 1.0)

        // Cost formula
        celestialVariance =
            builder.comment("Symmetric variance applied to the final celestial countPerUnit (mirrors `variance` for abyssal). 0 disables.")
                .defineInRange("celestialVariance", 0.1, 0.0, Double.MAX_VALUE)

        celestialBaseCostMultiplier =
            builder.comment("Global scalar on every randomly-generated celestial contract's per-unit price. 1.0 = each unit costs roughly the reward's fair value.")
                .defineInRange("celestialBaseCostMultiplier", 1.0, 0.0, Double.MAX_VALUE)
        celestialCostPerExtraLifetimeUnitFactor =
            builder.comment("Per-unit price gradient with lifetime: ×= 1 + this × (effectiveLifetimeUnits - 1). Unlimited contracts use celestialUnlimitedLifetimeUnitsRarityCap as their effective lifetime.")
                .defineInRange("celestialCostPerExtraLifetimeUnitFactor", 0.04, 0.0, Double.MAX_VALUE)
        celestialCostPerExtraLevelFactor =
            builder.comment("Per-unit price gradient with max-level: ×= 1 + this × (maxLevel - 1).")
                .defineInRange("celestialCostPerExtraLevelFactor", 0.15, 0.0, Double.MAX_VALUE)
        celestialCostPerExtraExpiryFactor =
            builder.comment("Per-unit price gradient with expiresIn: ×= 1 + this × expiresIn. Cycled finite-expiry contracts only. Keep small enough that finite-expiry never out-prices celestialNeverExpiresMultiplier.")
                .defineInRange("celestialCostPerExtraExpiryFactor", 0.005, 0.0, Double.MAX_VALUE)
        celestialUnlimitedLifetimeUnitsMultiplier =
            builder.comment("Per-unit price premium for cycled unlimited contracts (throttled by the cycle, so the premium is modest).")
                .defineInRange("celestialUnlimitedLifetimeUnitsMultiplier", 1.5, 1.0, Double.MAX_VALUE)
        celestialUnlimitedNoCycleMultiplier =
            builder.comment("Per-unit price premium for no-cycle unlimited contracts (no cap, no throttle: the strongest shape).")
                .defineInRange("celestialUnlimitedNoCycleMultiplier", 1.5, 1.0, Double.MAX_VALUE)
        celestialNeverExpiresMultiplier =
            builder.comment("Per-unit price premium when expiresIn=-1 and the contract is cycled. Must exceed any reachable finite-expiry premium so relaxing to never-expires never makes the contract cheaper.")
                .defineInRange("celestialNeverExpiresMultiplier", 1.5, 1.0, Double.MAX_VALUE)
        celestialCycleDurationCostFactors = builder.comment(
            """
            Comma-separated per-bucket cost factors aligned to celestialRandomCycleDurationOptionsMs.
            Defaults: no-cycle 4.0×, 30m 3.0×, 1h 2.5×, 3h 2.0×, 6h 1.5×, 12h 1.2×, 1d 1.0×, 3d 0.75×, 7d 0.6×.
            No-cycle is the highest factor so relaxing into no-cycle is always more expensive than the next-faster cycle.
            """.trimIndent()
        ).define("celestialCycleDurationCostFactors", "4.0,3.0,2.5,2.0,1.5,1.2,1.0,0.75,0.6")
        celestialUnlimitedLifetimeUnitsRarityCap =
            builder.comment("Sentinel effective lifetime units used by the rarity calc and lifetime price gradient when maxLifetimeUnits=0.")
                .defineInRange("celestialUnlimitedLifetimeUnitsRarityCap", 100, 1, Int.MAX_VALUE)
        celestialRarityThresholdsString = builder.comment(
            """
            Total emerald cost (countPerUnit × effectiveLifetimeUnits) needed to reach Uncommon, Rare, and Epic.
            Separate from rarityThresholds since celestial rarity tracks spend, abyssal tracks reward.
            Set to "" to keep all celestial contracts at Common.
            """.trimIndent()
        ).define("celestialRarityThresholds", "2000,10000,30000")

        // Reroll
        celestialRerollCostPenaltyFactor =
            builder.comment("Per-unit price markup added per RELAX reroll: ×= 1 + this × relaxCount. Restrict rerolls don't add markup, so they always reduce price proportionally to the shape change.")
                .defineInRange("celestialRerollCostPenaltyFactor", 0.25, 0.0, Double.MAX_VALUE)
        celestialRerollLightningStatWeights = builder.comment(
            """
            Weights for which stat lightning targets when rerolling, in order: maxLifetimeUnits, expiresIn, cycleDurationMs, maxLevel.
            """.trimIndent()
        ).define("celestialRerollLightningStatWeights", "1,1,1,1")
        celestialRerollFireStatWeights = builder.comment(
            "Weights for which stat fire targets when rerolling, same order as celestialRerollLightningStatWeights."
        ).define("celestialRerollFireStatWeights", "1,1,1,1")
        celestialEnableLightningReroll =
            builder.comment("If false, lightning strikes on celestial contract item entities never reroll. Useful for packs that drive rerolling through recipes or commands instead.")
                .define("celestialEnableLightningReroll", true)
        celestialEnableFireReroll =
            builder.comment("If false, dropping a celestial contract into fire/lava never rerolls. Useful for packs that drive rerolling through recipes or commands instead.")
                .define("celestialEnableFireReroll", true)
        celestialDefaultMaxRerolls = builder.comment(
            """
            Cap on the number of times a celestial contract can be rerolled. -1 means no cap.
            Per-pool entries can override this with the "maxRerolls" field in their shape block.
            """.trimIndent()
        ).defineInRange("celestialDefaultMaxRerolls", 2, -1, Int.MAX_VALUE)

        // Target replacement
        celestialReplaceCurrencyTargetRate =
            builder.comment(
                """
                Probability that a randomly-generated celestial contract demands a non-currency item (drawn from abyssal targets) instead of the celestial currency anchor.
                Mirrors abyssal's replaceRewardWithRandomRate but in reverse: abyssal swaps its currency *reward* for a random target item; celestial swaps its currency *target* for a random non-currency item, scaled to equivalent value via abyssal exchange rates.
                """.trimIndent()
            ).defineInRange("celestialReplaceCurrencyTargetRate", 0.05, 0.0, 1.0)
        celestialReplaceCurrencyTargetFactor =
            builder.comment("Multiplier applied to the swapped target count. Values < 1 make item-targeted celestials cheaper to compensate for items being less liquid than currency.")
                .defineInRange("celestialReplaceCurrencyTargetFactor", 0.8, 0.0, Double.MAX_VALUE)

        // Distribution
        celestialRarityBias = builder.comment(
            """
            Bias the random celestial generator toward higher (positive) or lower (negative) rarity contracts.
            Tournament: |bias|·5 extra candidates are rolled and the highest- or lowest-rarity one is kept.
            """.trimIndent()
        ).defineInRange("celestialRarityBias", 0.0, -1.0, 1.0)

        disableDefaultCelestialContractOptions = builder.comment(
            """
            If true, skip all built-in celestialContracts and celestialRewardPool entries in datapack files ending in "_default.json".
            Use this if you want to replace the default celestial options with a custom data pack.
            """.trimIndent()
        ).define("disableDefaultCelestialContractOptions", false)

        allowBlankCelestialContractUse = builder.comment(
            "If true, Blank Celestial Contracts can be right-clicked to roll a random Celestial Contract."
        ).define("allowBlankCelestialContractUse", true)

        celestialDefinedContractChance = builder.comment(
            """
            Per-roll probability that the celestial generator picks a fully-defined contract from `celestialContracts` instead of rolling a random one from `celestialRewardPool`.
            Lets curated set-pieces (Harvest Festival, Dragon's Promise, etc.) surface naturally through blank-celestial right-clicks, loot drops, and abyssal-pool drop-ins. 0 disables; 1.0 makes the generator always emit a defined contract when any are loaded.
            """.trimIndent()
        ).defineInRange("celestialDefinedContractChance", 0.01, 0.0, 1.0)

        celestialContractInAbyssalPoolChance = builder.comment(
            "Per-slot probability a refreshed Abyssal Contracts pool slot rolls a random celestial instead. 0 disables."
        ).defineInRange("celestialContractInAbyssalPoolChance", 0.001, 0.0, 1.0)

        celestialWanderingTraderChance = builder.comment(
            "Probability the wandering trader includes a celestial contract trade in its rotation. 0 disables."
        ).defineInRange("celestialWanderingTraderChance", 0.05, 0.0, 1.0)
        celestialWanderingTraderPriceMultiplier = builder.comment(
            "Multiplier on the trade-emerald price for wandering-trader celestial trades."
        ).defineInRange("celestialWanderingTraderPriceMultiplier", 1.0, 0.0, Double.MAX_VALUE)

        celestialLootInjectionTables = builder.comment(
            "Comma-separated loot table IDs to inject random celestial drops into."
        ).define("celestialLootInjectionTables", "minecraft:chests/simple_dungeon,minecraft:chests/stronghold_corridor")
        celestialLootInjectionWeight = builder.comment(
            "Loot weight for the injected celestial entry."
        ).defineInRange("celestialLootInjectionWeight", 1, 0, Int.MAX_VALUE)

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