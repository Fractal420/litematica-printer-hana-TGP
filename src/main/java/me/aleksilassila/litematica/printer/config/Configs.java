package me.aleksilassila.litematica.printer.config;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fi.dy.masa.malilib.config.*;
import fi.dy.masa.malilib.config.options.*;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.hotkeys.KeybindSettings;
import fi.dy.masa.malilib.util.data.json.JsonUtils;
import fi.dy.masa.malilib.util.restrictions.UsageRestriction;
import fi.dy.masa.malilib.config.ConfigManager;
import me.aleksilassila.litematica.printer.Reference;
import me.aleksilassila.litematica.printer.enums.*;
import me.aleksilassila.litematica.printer.gui.ConfigUi;
import me.aleksilassila.litematica.printer.utils.mods.ModLoadUtils;
import net.minecraft.world.level.block.Blocks;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class Configs extends ConfigBuilders implements IConfigHandler {
    private static final Configs INSTANCE = new Configs();

    private static final String FILE_PATH = "./config/" + Reference.MOD_ID + ".json";
    private static final File CONFIG_DIR = new File("./config");
    private static final int CONFIG_SCHEMA_VERSION = 3;
    private static final List<String> DEFAULT_COVER_BLOCK_FILTERS = List.of(
            "#minecraft:carpets",
            "#minecraft:slabs",
            "#minecraft:rails"
    );

    private static final KeybindSettings GUI_NO_ORDER = KeybindSettings.create(KeybindSettings.Context.GUI, KeyAction.PRESS, false, false, false, true);

    public static final ImmutableList<IConfigBase> OPTIONS;
    public static final ImmutableList<IHotkey> HOTKEYS;

    static {
        LinkedHashSet<IConfigBase> optionSet = new LinkedHashSet<>();
        optionSet.addAll(Core.OPTIONS);
        optionSet.addAll(Special.OPTIONS);
        optionSet.addAll(Placement.OPTIONS);
        optionSet.addAll(Break.OPTIONS);
        optionSet.addAll(Hotkeys.OPTIONS);
        optionSet.addAll(Print.OPTIONS);
        optionSet.addAll(Mine.OPTIONS);
        optionSet.addAll(Fill.OPTIONS);
        optionSet.addAll(Cover.OPTIONS);
        optionSet.addAll(Fluid.OPTIONS);
        optionSet.addAll(Bedrock.OPTIONS);
        OPTIONS = ImmutableList.copyOf(optionSet);

        List<IHotkey> hotkeys = new ArrayList<>();
        for (IConfigBase option : optionSet) {
            if (option instanceof IHotkey hokey) {
                hotkeys.add(hokey);
            }
        }
        HOTKEYS = ImmutableList.copyOf(hotkeys);
    }

    public static class Core {
        private static boolean isSingleMode() {
            return WORK_MODE.getOptionListValue().equals(WorkingModeType.SINGLE);
        }

        private static boolean isMultiMode() {
            return WORK_MODE.getOptionListValue().equals(WorkingModeType.MULTI);
        }

        public static final ConfigBooleanHotkeyed WORK_SWITCH = booleanHotkey("workingSwitch")
                .defaultValue(false)
                .defaultHotkey("CAPS_LOCK")
                .keybindSettings(KeybindSettings.PRESS_ALLOWEXTRA_EMPTY)
                .build();

        public static final ConfigOptionList WORK_MODE = optionList("modeSwitch")
                .defaultValue(WorkingModeType.SINGLE)
                .build();

        public static final ConfigBooleanHotkeyed PRINT = booleanHotkey("print")
                .defaultValue(false)
                .setVisible(Core::isMultiMode)
                .build();

        public static final ConfigBooleanHotkeyed MINE = booleanHotkey("mine")
                .defaultValue(false)
                .setVisible(Core::isMultiMode)
                .build();

        public static final ConfigBooleanHotkeyed FILL = booleanHotkey("fill")
                .defaultValue(false)
                .setVisible(Core::isMultiMode)
                .build();

        public static final ConfigBooleanHotkeyed FLUID = booleanHotkey("fluid")
                .defaultValue(false)
                .setVisible(Core::isMultiMode)
                .build();

        public static final ConfigBooleanHotkeyed COVER = booleanHotkey("cover")
                .defaultValue(false)
                .setVisible(Core::isMultiMode)
                .build();

        public static final ConfigOptionList WORK_MODE_TYPE = optionList("printerMode")
                .defaultValue(PrintModeType.PRINTER)
                .setVisible(Core::isSingleMode)
                .build();

        public static final ConfigInteger WORK_RANGE = integer("workRange")
                .defaultValue(6)
                .range(1, 256)
                .build();

        public static final ConfigInteger SCAN_TIME_BUDGET_MS = integer("scanTimeBudgetMs")
                .defaultValue(2)
                .range(1, 10)
                .build();

        public static final ConfigInteger LAZY_ENTER_TICKS = integer("lazyEnterTicks")
                .defaultValue(10)
                .range(0, 40)
                .build();

        public static final ConfigBoolean CHECK_PLAYER_INTERACTION_RANGE = bool("checkPlayerInteractionRange")
                .defaultValue(true)
                .build();

        public static final ConfigBoolean LAG_CHECK = bool("printerLagCheck")
                .defaultValue(false)
                .build();

        public static final ConfigInteger LAG_CHECK_MAX = integer("printerLagCheckMax")
                .defaultValue(20)
                .setVisible(LAG_CHECK::getBooleanValue)
                .range(20, 1200)
                .build();

        public static final ConfigOptionList ITERATOR_SHAPE = optionList("printerIteratorShape")
                .defaultValue(RadiusShapeType.SPHERE)
                .build();

        public static final ConfigBoolean RENDER_HUD = bool("renderHud")
                .defaultValue(false)
                .build();

        public static final ConfigBoolean MISSING_MATERIAL_HUD = bool("missingMaterialHud")
                .defaultValue(true)
                .build();

        public static final ConfigInteger RENDER_HUD_X = integer("renderHudX")
                .defaultValue(10)
                .range(0, 4096)
                .setVisible(() -> RENDER_HUD.getBooleanValue() || MISSING_MATERIAL_HUD.getBooleanValue())
                .build();

        public static final ConfigInteger RENDER_HUD_Y = integer("renderHudY")
                .defaultValue(10)
                .range(0, 4096)
                .setVisible(() -> RENDER_HUD.getBooleanValue() || MISSING_MATERIAL_HUD.getBooleanValue())
                .build();

        public static final ConfigInteger RENDER_HUD_SCALE = integer("renderHudScale")
                .defaultValue(100)
                .range(50, 200)
                .setVisible(() -> RENDER_HUD.getBooleanValue() || MISSING_MATERIAL_HUD.getBooleanValue())
                .build();

        public static final ConfigBoolean AUTO_DISABLE_PRINTER = bool("printerAutoDisable")
                .defaultValue(true)
                .build();

        public static final ConfigBoolean RENDER_ONLY_HOLDING_ITEMS = bool("printerRenderOnlyHoldingItems")
                .defaultValue(false)
                .build();

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                WORK_SWITCH,
                WORK_MODE,
                WORK_MODE_TYPE,
                PRINT,
                MINE,
                FILL,
                FLUID,
                COVER,
                WORK_RANGE,
                SCAN_TIME_BUDGET_MS,
                LAZY_ENTER_TICKS,
                RENDER_HUD,
                MISSING_MATERIAL_HUD,
                RENDER_HUD_X,
                RENDER_HUD_Y,
                RENDER_HUD_SCALE,
                LAG_CHECK,
                LAG_CHECK_MAX,
                CHECK_PLAYER_INTERACTION_RANGE,
                ITERATOR_SHAPE,
                AUTO_DISABLE_PRINTER,
                RENDER_ONLY_HOLDING_ITEMS
        );
    }

    public static class Special {

        public static final ConfigBoolean UNLOCK_BEACON_EFFECTS = bool("unlockBeaconEffects")
                .defaultValue(false)
                .build();

        public static final ConfigBoolean TWEAKEROO_ANGEL_BLOCK_MAY_BUILD = bool("tweakerooAngelBlockMayBuild")
                .defaultValue(false)
                .setVisible(ModLoadUtils::isTweakerooLoaded)
                .build();

        public static final ConfigBooleanHotkeyed REMOTE_TAKE = booleanHotkey("remoteTake")
                .defaultValue(false)
                .setVisible(ModLoadUtils::isChestTrackerLoaded)
                .build();

        public static final ConfigBoolean MANUAL_VANILLA_REFILL = bool("manualVanillaRefill")
                .defaultValue(false)
                .build();

        public static final ConfigBoolean DROP_EMPTY_SHULKERS = bool("dropEmptyShulkers")
                .defaultValue(false)
                .build();

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                //#if MC < 260200
                UNLOCK_BEACON_EFFECTS,
                //#endif
                TWEAKEROO_ANGEL_BLOCK_MAY_BUILD,
                Placement.QUICK_SHULKER,
                Placement.QUICK_SHULKER_MODE,
                Placement.QUICK_SHULKER_COOLDOWN,
                Placement.STORE_ORDERLY,
                REMOTE_TAKE,
                MANUAL_VANILLA_REFILL,
                DROP_EMPTY_SHULKERS
        );
    }

    public static class Placement {

        public static final ConfigInteger PLACE_INTERVAL = integer("placeInterval")
                .defaultValue(0)
                .range(0, 20)
                .build();

        public static final ConfigInteger PLACE_BLOCKS_PER_TICK = integer("placeBlocksPerTick")
                .defaultValue(1)
                .range(0, 256)
                .build();

        public static final ConfigInteger PLACE_COOLDOWN = integer("placeCooldown")
                .defaultValue(8)
                .range(0, 64)
                .build();

        public static final ConfigBoolean RTT_ADAPTIVE_INTERVAL = bool("placeRttAdaptiveInterval")
                .defaultValue(false)
                .build();

        public static final ConfigInteger RTT_SAFETY_PERCENT = integer("placeRttSafetyPercent")
                .defaultValue(100)
                .range(25, 300)
                .setVisible(RTT_ADAPTIVE_INTERVAL::getBooleanValue)
                .build();

        public static final ConfigBoolean FALLING_CHECK = bool("printFallingBlockCheck")
                .defaultValue(true)
                .build();

        public static final ConfigBoolean QUICK_SHULKER = bool("quickShulker")
                .defaultValue(false)
                .build();

        public static final ConfigOptionList QUICK_SHULKER_MODE = optionList("quickShulkerMode")
                .defaultValue(QuickShulkerModeType.INVOKE)
                .build();

        public static final ConfigInteger QUICK_SHULKER_COOLDOWN = integer("quickShulkerCooldown")
                .defaultValue(1)
                .range(0, 20)
                .build();

        public static final ConfigBoolean STORE_ORDERLY = bool("storeOrderly")
                .defaultValue(false)
                .build();

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                PLACE_INTERVAL,
                PLACE_BLOCKS_PER_TICK,
                PLACE_COOLDOWN,
                RTT_ADAPTIVE_INTERVAL,
                RTT_SAFETY_PERCENT,
                FALLING_CHECK
        );
    }

    public static class Break {
        private static boolean isCustom() {
            return BREAK_LIMITER.getOptionListValue().equals(ExcavateListMode.CUSTOM);
        }

        private static boolean isWhitelist() {
            return isCustom() && BREAK_LIMIT.getOptionListValue().equals(UsageRestriction.ListType.WHITELIST);
        }

        private static boolean isBlacklist() {
            return isCustom() && BREAK_LIMIT.getOptionListValue().equals(UsageRestriction.ListType.BLACKLIST);
        }

        public static final ConfigBoolean BREAK_USE_DELAYED_DESTROY = bool("breakUseDelayedDestroy")
                .defaultValue(false)
                .build();

        public static final ConfigInteger BREAK_BLOCKS_PER_TICK = integer("breakBlocksPerTick")
                .defaultValue(0)
                .range(0, 1000)
                .build();

        public static final ConfigInteger BREAK_PROGRESS_THRESHOLD = integer("breakProgressThreshold")
                .defaultValue(100)
                .range(70, 100)
                .build();

        public static final ConfigInteger BREAK_INTERVAL = integer("breakInterval")
                .defaultValue(0)
                .range(0, 20)
                .build();

        public static final ConfigInteger BREAK_COOLDOWN = integer("breakCooldown")
                .defaultValue(8)
                .range(0, 64)
                .build();

        public static final ConfigBoolean BREAK_CHECK_HARDNESS = bool("breakCheckHardness")
                .defaultValue(true)
                .build();

        public static final ConfigBoolean BREAK_AUTO_TOOL = bool("breakAutoTool")
                .defaultValue(false)
                .build();

        public static final ConfigOptionList BREAK_LIMITER = optionList("breakLimiter")
                .defaultValue(ExcavateListMode.CUSTOM)
                .build();

        public static final ConfigOptionList BREAK_LIMIT = optionList("breakLimit")
                .defaultValue(UsageRestriction.ListType.NONE)
                .setVisible(Break::isCustom)
                .build();

        public static final ConfigStringList BREAK_WHITELIST = stringList("breakWhitelist")
                .setVisible(Break::isWhitelist)
                .build();

        public static final ConfigStringList BREAK_BLACKLIST = stringList("breakBlacklist")
                .setVisible(Break::isBlacklist)
                .build();

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                BREAK_INTERVAL,
                BREAK_BLOCKS_PER_TICK,
                BREAK_USE_DELAYED_DESTROY,
                BREAK_PROGRESS_THRESHOLD,
                BREAK_COOLDOWN,
                BREAK_CHECK_HARDNESS,
                BREAK_AUTO_TOOL,

                BREAK_LIMITER,
                BREAK_LIMIT,
                BREAK_WHITELIST,
                BREAK_BLACKLIST
        );
    }

    public static class Bedrock {
        public static final ConfigInteger BEDROCK_INTERVAL = integer("bedrockInterval")
                .defaultValue(2)
                .range(1, 20)
                .build();

        public static final ConfigInteger BEDROCK_BLOCKS_PER_TICK = integer("bedrockBlocksPerTick")
                .defaultValue(6)
                .range(1, 8)
                .build();

        public static final ConfigBoolean BEDROCK_ALLOW_SIDE = bool("bedrockAllowSide")
                .defaultValue(false)
                .build();

        public static final ConfigBoolean BEDROCK_MULTIPLAYER_ADAPTIVE = bool("bedrockMultiplayerAdaptive")
                .defaultValue(true)
                .build();

        public static final ConfigStringList BEDROCK_WHITELIST = stringList("bedrockWhitelist")
                .build();

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                BEDROCK_INTERVAL,
                BEDROCK_BLOCKS_PER_TICK,
                BEDROCK_ALLOW_SIDE,
                BEDROCK_MULTIPLAYER_ADAPTIVE,
                BEDROCK_WHITELIST
        );
    }

    public static class Print {

        public static final ConfigOptionList PRINT_SELECTION_TYPE = optionList("printSelectionType")
                .defaultValue(SelectionType.LITEMATICA_RENDER_LAYER)
                .build();

        public static final ConfigBoolean EASY_PLACE_PROTOCOL = bool("easyPlaceProtocol")
                .defaultValue(false)
                .build();

        public static final ConfigBoolean PLACE_IN_AIR = bool("placeInAir")
                .defaultValue(true)
                .build();

        public static final ConfigBoolean PRINT_SORT_TARGETS = bool("printSortTargets")
                .defaultValue(false)
                .build();

        public static final ConfigBoolean PRINT_SORT_SIDES = bool("printSortSides")
                .defaultValue(false)
                .build();

        public static final ConfigBoolean REPAIR_RAIL_SHAPE = bool("printRepairRailShape")
                .defaultValue(false)
                .build();

        public static final ConfigBoolean PRINT_SKIP = bool("printSkip")
                .defaultValue(false)
                .build();

        public static final ConfigStringList PRINT_SKIP_LIST = stringList("printSkipList")
                .build();

        public static final ConfigBoolean PRINT_FORCED_SNEAK = bool("printForcedSneak")
                .defaultValue(false)
                .build();

        public static final ConfigBoolean PRINT_RESERVE_ITEMS = bool("printReserveItems")
                .defaultValue(false)
                .build();

        public static final ConfigInteger PRINT_RESERVE_ITEM_COUNT = integer("printReserveItemCount")
                .defaultValue(1)
                .range(1, 64)
                .setVisible(PRINT_RESERVE_ITEMS::getBooleanValue)
                .build();

        public static final ConfigBoolean PRINT_REPLACE = bool("printReplace")
                .defaultValue(true)
                .build();

        public static final ConfigStringList REPLACEABLE_LIST = stringList("printReplaceableList")
                .defaultValue(Blocks.SNOW, Blocks.LAVA, Blocks.WATER, Blocks.BUBBLE_COLUMN, Blocks.SHORT_GRASS)
                .build();

        public static final ConfigBoolean SKIP_WATERLOGGED_BLOCK = bool("printSkipWaterlogged")
                .defaultValue(false)
                .build();

        public static final ConfigBoolean REPLACE_CORAL = bool("printReplaceCoral")
                .defaultValue(false)
                .build();

        public static final ConfigBooleanHotkeyed PRINT_ICE_FOR_WATER = booleanHotkey("printIceForWater")
                .defaultValue(false)
                .build();

        public static final ConfigBoolean STRIP_LOGS = bool("printAutoStripLogs")
                .defaultValue(false)
                .build();

        public static final ConfigBoolean NOTE_BLOCK_TUNING = bool("printAutoTuning")
                .defaultValue(true)
                .build();

        public static final ConfigBoolean SAFELY_OBSERVER = bool("printSafelyObserver")
                .defaultValue(true)
                .build();

        public static final ConfigBoolean FILL_COMPOSTER = bool("printAutoFillComposter")
                .defaultValue(false)
                .build();

        public static final ConfigStringList FILL_COMPOSTER_WHITELIST = stringList("printAutoFillComposterWhitelist")
                .setVisible(FILL_COMPOSTER::getBooleanValue)
                .build();

        public static final ConfigBoolean BONEMEAL_CROPS = bool("printBonemealCrops")
                .defaultValue(false)
                .build();

        public static final ConfigInteger BONEMEAL_CROPS_CLICKS = integer("printBonemealCropsClicks")
                .defaultValue(10)
                .range(1, 32)
                .setVisible(BONEMEAL_CROPS::getBooleanValue)
                .build();

        public static final ConfigBoolean BREAK_WRONG_BLOCK = bool("printBreakWrongBlock")
                .defaultValue(false)
                .build();

        public static final ConfigBoolean BREAK_EXTRA_BLOCK = bool("printBreakExtraBlock")
                .defaultValue(false)
                .build();

        public static final ConfigBoolean BREAK_WRONG_STATE_BLOCK = bool("printBreakWrongStateBlock")
                .defaultValue(false)
                .build();

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                PRINT_SELECTION_TYPE,
                EASY_PLACE_PROTOCOL,
                PLACE_IN_AIR,
                PRINT_SORT_TARGETS,
                PRINT_SORT_SIDES,
                REPAIR_RAIL_SHAPE,
                PRINT_FORCED_SNEAK,
                PRINT_RESERVE_ITEMS,
                PRINT_RESERVE_ITEM_COUNT,
                BREAK_WRONG_BLOCK,
                BREAK_EXTRA_BLOCK,
                BREAK_WRONG_STATE_BLOCK,
                PRINT_SKIP,
                PRINT_SKIP_LIST,
                PRINT_REPLACE,
                REPLACEABLE_LIST,
                SKIP_WATERLOGGED_BLOCK,
                PRINT_ICE_FOR_WATER,
                SAFELY_OBSERVER,
                STRIP_LOGS,
                NOTE_BLOCK_TUNING,
                REPLACE_CORAL,
                FILL_COMPOSTER,
                FILL_COMPOSTER_WHITELIST,
                BONEMEAL_CROPS
                , BONEMEAL_CROPS_CLICKS
        );
    }

    public static class Mine {
        private static boolean isCustom() {
            return EXCAVATE_LIMITER.getOptionListValue().equals(ExcavateListMode.CUSTOM);
        }

        private static boolean isWhitelist() {
            return isCustom() && EXCAVATE_LIMIT.getOptionListValue().equals(UsageRestriction.ListType.WHITELIST);
        }

        private static boolean isBlacklist() {
            return isCustom() && EXCAVATE_LIMIT.getOptionListValue().equals(UsageRestriction.ListType.BLACKLIST);
        }

        public static final ConfigOptionList MINE_SELECTION_TYPE = optionList("mineSelectionType")
                .defaultValue(SelectionType.LITEMATICA_SELECTION)
                .build();

        public static final ConfigBoolean MINE_TRENCH_MODE = bool("mineTrenchMode")
                .defaultValue(false)
                .build();

        public static final ConfigOptionList EXCAVATE_LIMITER = optionList("excavateLimiter")
                .defaultValue(ExcavateListMode.CUSTOM)
                .build();

        public static final ConfigOptionList EXCAVATE_LIMIT = optionList("excavateLimit")
                .defaultValue(UsageRestriction.ListType.NONE)
                .setVisible(Mine::isCustom)
                .build();

        public static final ConfigStringList EXCAVATE_WHITELIST = stringList("excavateWhitelist")
                .setVisible(Mine::isWhitelist)
                .build();

        public static final ConfigStringList EXCAVATE_BLACKLIST = stringList("excavateBlacklist")
                .setVisible(Mine::isBlacklist)
                .build();

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                MINE_SELECTION_TYPE,
                MINE_TRENCH_MODE,
                EXCAVATE_LIMITER,
                EXCAVATE_LIMIT,
                EXCAVATE_WHITELIST,
                EXCAVATE_BLACKLIST
        );
    }

    public static class Fill {
        private static boolean isBlocklist() {
            return FILL_BLOCK_MODE.getOptionListValue().equals(FillBlockModeType.BLOCKLIST);
        }

        public static final ConfigOptionList FILL_SELECTION_TYPE = optionList("fillSelectionType")
                .defaultValue(SelectionType.LITEMATICA_SELECTION)
                .build();

        public static final ConfigOptionList FILL_BLOCK_MODE = optionList("fillBlockMode")
                .defaultValue(FillBlockModeType.BLOCKLIST)
                .build();

        public static final ConfigStringList FILL_BLOCK_LIST = stringList("fillBlockList")
                .defaultValue(Blocks.COBBLESTONE)
                .setVisible(Fill::isBlocklist)
                .build();

        public static final ConfigOptionList FILL_BLOCK_FACING = optionList("fillModeFacing")
                .defaultValue(FillModeFacingType.NONE)
                .build();

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                FILL_SELECTION_TYPE,
                FILL_BLOCK_MODE,
                FILL_BLOCK_LIST,
                FILL_BLOCK_FACING
        );
    }

    public static class Cover {
        private static boolean isBlocklist() {
            return COVER_BLOCK_MODE.getOptionListValue().equals(FillBlockModeType.BLOCKLIST);
        }

        public static final ConfigOptionList COVER_SELECTION_TYPE = optionList("coverSelectionType")
                .defaultValue(SelectionType.LITEMATICA_SELECTION)
                .build();

        public static final ConfigOptionList COVER_BLOCK_MODE = optionList("coverBlockMode")
                .defaultValue(FillBlockModeType.BLOCKLIST)
                .build();

        public static final ConfigStringList COVER_BLOCK_LIST = stringList("coverBlockList")

                .defaultValue("#minecraft:carpets", "#minecraft:slabs", "#minecraft:rails")
                .setVisible(Cover::isBlocklist)
                .build();

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                COVER_SELECTION_TYPE,
                COVER_BLOCK_MODE,
                COVER_BLOCK_LIST
        );
    }

    public static class Fluid {

        public static final ConfigOptionList FLUID_SELECTION_TYPE = optionList("fluidSelectionType")
                .defaultValue(SelectionType.LITEMATICA_SELECTION)
                .build();

        public static final ConfigBoolean FILL_FLOWING_FLUID = bool("fluidModeFillFlowing")
                .defaultValue(true)
                .build();

        public static final ConfigStringList FLUID_REPLACE_BLOCK_LIST = stringList("fluidReplaceBlockList")
                .defaultValue(Blocks.SAND)
                .build();

        public static final ConfigStringList FLUID_LIST = stringList("fluidList")
                .defaultValue(Blocks.WATER, Blocks.LAVA)
                .build();

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                FLUID_SELECTION_TYPE,
                FILL_FLOWING_FLUID,
                FLUID_REPLACE_BLOCK_LIST,
                FLUID_LIST
        );
    }

    public static class Hotkeys {

        public static final ConfigHotkey OPEN_SCREEN = hotkey("openScreen")
                .defaultStorageString("Z,Y")
                .build();

        public static final ConfigHotkey CLOSE_ALL_MODE = hotkey("closeAllMode")
                .defaultStorageString("LEFT_CONTROL,G")
                .build();

        public static final ConfigHotkey SWITCH_PRINTER_MODE = hotkey("switchPrinterMode")
                .bindConfig(Core.WORK_MODE_TYPE)
                .setVisible(Core::isSingleMode)
                .build();

        public static final ConfigBooleanHotkeyed BEDROCK = booleanHotkey("bedrock")
                .defaultValue(false)
                .setVisible(Core::isMultiMode)
                .build();

        public static final ConfigHotkey CACHE_SELECTION_CONTAINERS = hotkey("cacheSelectionContainers")
                .defaultStorageString("")
                .setVisible(ModLoadUtils::isChestTrackerLoaded)
                .build();

        public static final ConfigHotkey CLEAR_CONTAINER_CACHE = hotkey("clearContainerCache")
                .defaultStorageString("")
                .setVisible(ModLoadUtils::isChestTrackerLoaded)
                .build();

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                OPEN_SCREEN,
                Core.WORK_SWITCH,
                CLOSE_ALL_MODE,
                SWITCH_PRINTER_MODE,

                Core.PRINT,
                Core.MINE,
                Core.FILL,
                Core.FLUID,
                Core.COVER,
                BEDROCK,
                CACHE_SELECTION_CONTAINERS,
                CLEAR_CONTAINER_CACHE
        );
    }

    @Override
    public void load() {
        File settingFile = new File(FILE_PATH);
        if (settingFile.isFile() && settingFile.exists()) {
            //#if MC >= 12111
            JsonElement jsonElement = JsonUtils.parseJsonFile(settingFile.toPath());
            //#else
            //$$ JsonElement jsonElement = JsonUtils.parseJsonFile(settingFile);
            //#endif
            if (jsonElement != null && jsonElement.isJsonObject()) {
                JsonObject obj = jsonElement.getAsJsonObject();
                ConfigUtils.readConfigBase(obj, Reference.MOD_ID, OPTIONS);
                int schemaVersion = obj.has("configSchemaVersion")
                        && obj.get("configSchemaVersion").isJsonPrimitive()
                        && obj.get("configSchemaVersion").getAsJsonPrimitive().isNumber()
                        ? obj.get("configSchemaVersion").getAsInt() : 0;
                if (schemaVersion < CONFIG_SCHEMA_VERSION) {
                    if (schemaVersion < 3 && isLegacyCoverBlockList(Cover.COVER_BLOCK_LIST.getStrings())) {
                        Cover.COVER_BLOCK_LIST.setStrings(DEFAULT_COVER_BLOCK_FILTERS);
                    }

                    Break.BREAK_USE_DELAYED_DESTROY.setBooleanValue(false);
                    this.save();
                }
            }
        }
    }

    @Override
    public void save() {
        File settingFile = new File(FILE_PATH);
        if ((CONFIG_DIR.exists() && CONFIG_DIR.isDirectory()) || CONFIG_DIR.mkdirs()) {
            JsonObject configRoot = new JsonObject();
            configRoot.addProperty("configSchemaVersion", CONFIG_SCHEMA_VERSION);
            ConfigUtils.writeConfigBase(configRoot, Reference.MOD_ID, OPTIONS);
            //#if MC >= 12111
            JsonUtils.writeJsonToFile(configRoot, settingFile.toPath());
            //#else
            //$$ JsonUtils.writeJsonToFile(configRoot, settingFile);
            //#endif
        }
    }

    public static void init() {
        Configs.INSTANCE.load();
        ConfigManager.getInstance().registerConfigHandler(Reference.MOD_ID, Configs.INSTANCE);
        InputEventHandler.getKeybindManager().registerKeybindProvider(InputHandler.getInstance());
        InputEventHandler.getInputManager().registerKeyboardInputHandler(InputHandler.getInstance());
        //#if MC > 12006
        fi.dy.masa.malilib.registry.Registry.CONFIG_SCREEN.registerConfigScreenFactory(
                new fi.dy.masa.malilib.util.data.ModInfo(Reference.MOD_ID, Reference.MOD_NAME, ConfigUi::new)
        );
        //#endif
    }

    public static void saveToFile() {
        Configs.INSTANCE.save();
    }

    private static boolean isLegacyCoverBlockList(List<String> filters) {
        if (filters == null || filters.size() != 1) {
            return false;
        }
        String filter = filters.get(0);
        return "minecraft:smooth_stone_slab".equals(filter)
                || "minecraft:tuff_slab".equals(filter);
    }
}
