package me.aleksilassila.litematica.printer;

import lombok.Getter;
import me.aleksilassila.litematica.printer.utils.minecraft.StringUtils;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

@Getter
public class I18n {
    public static final I18n MESSAGE_TOGGLED = of("message.toggled");
    public static final I18n MESSAGE_VALUE_OFF = of("message.value.off");
    public static final I18n MESSAGE_VALUE_ON = of("message.value.on");

    public static final I18n AUTO_DISABLE_NOTICE = of("auto_disable_notice");
    public static final I18n FREE_NOTICE = of("free_notice");

    public static final I18n FALLING_BLOCK_NO_SUPPORT = of("message.falling_block.no_support");
    public static final I18n FALLING_BLOCK_MISMATCH = of("message.falling_block.mismatch");

    public static final I18n BEDROCK_CREATIVE_MODE = of("message.bedrock.creative_mode");

    public static final I18n SHULKER_MOD_NOT_LOADED = of("message.shulker.mod_not_loaded");

    public static final I18n CLOSE_ALL_MODE_NOTICE = of("message.close_all_mode");

    public static final I18n INVENTORY_FULL = of("message.inventory.full");
    public static final I18n INVENTORY_RESTORE_FAILED = of("message.inventory.restore_failed");
    public static final I18n INVENTORY_SHULKER_OCCUPIED = of("message.inventory.shulker_occupied");
    public static final I18n RESERVE_ITEM_SKIP = of("message.reserve_item.skip");

    public static final I18n MISSING_MATERIAL_TITLE = of("hud.missing.title");
    public static final I18n MISSING_MATERIAL_OVERFLOW = of("hud.missing.overflow");

    private static final String PREFIX_CONFIG = "config";
    private static final String PREFIX_COMMENT = "desc";

    private final @Nullable String prefix;
    private final String nameKey;
    private final String withPrefixNameKey;
    private final String descKey;
    private final String configNameKey;
    private final String configDescKey;

    private I18n(@Nullable String prefix, String nameKey) {
        this.prefix = prefix;
        this.nameKey = nameKey;
        this.withPrefixNameKey = prefix == null ? nameKey : prefix + "." + nameKey;
        this.descKey = withPrefixNameKey + "." + PREFIX_COMMENT;
        String configNameKey = prefix == null ? PREFIX_CONFIG : prefix + "." + PREFIX_CONFIG;
        this.configNameKey = configNameKey + "." + nameKey;
        this.configDescKey = configNameKey + "." + nameKey + "." + PREFIX_COMMENT;
    }

    public static I18n of(@Nullable String prefix, String key) {
        return new I18n(prefix, key);
    }

    public static I18n of(String key) {
        return new I18n(Reference.MOD_ID, key);
    }

    public MutableComponent getName() {
        return StringUtils.translatable(this.withPrefixNameKey);
    }

    public MutableComponent getName(Object... objects) {
        return StringUtils.translatable(this.withPrefixNameKey, objects);
    }

    public MutableComponent getDesc() {
        return StringUtils.translatable(this.descKey);
    }

    public MutableComponent getDesc(Object... objects) {
        return StringUtils.translatable(this.descKey, objects);
    }

    public MutableComponent getConfigName() {
        return StringUtils.translatable(this.configNameKey);
    }

    public MutableComponent getConfigName(Object... objects) {
        return StringUtils.translatable(this.configNameKey, objects);
    }

    public MutableComponent getConfigDesc() {
        return StringUtils.translatable(this.configDescKey);
    }

    public MutableComponent getConfigDesc(Object... objects) {
        return StringUtils.translatable(this.configDescKey, objects);
    }

    public String getSimpleKey() {
        if (nameKey == null || nameKey.isEmpty()) {
            return nameKey == null ? "" : nameKey;
        }
        int lastDotIndex = nameKey.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return nameKey;
        }
        if (lastDotIndex == nameKey.length() - 1) {
            return "";
        }
        return nameKey.substring(lastDotIndex + 1);
    }
}
