package me.aleksilassila.litematica.printer.config;

import fi.dy.masa.malilib.config.IConfigBase;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import me.aleksilassila.litematica.printer.utils.minecraft.StringUtils;

public final class ConfigMetadata {
    private static final BooleanSupplier ALWAYS_VISIBLE = () -> true;
    private static final Map<IConfigBase, Entry> ENTRIES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ConfigMetadata() {
    }

    public static void register(
            IConfigBase config,
            String nameKey,
            String commentKey,
            @Nullable BooleanSupplier visible
    ) {
        ENTRIES.put(config, new Entry(nameKey, commentKey, visible == null ? ALWAYS_VISIBLE : visible));
    }

    public static boolean isVisible(IConfigBase config) {
        Entry entry = ENTRIES.get(config);
        return entry == null || entry.visible().getAsBoolean();
    }

    public static @Nullable Entry get(IConfigBase config) {
        return ENTRIES.get(config);
    }

    public static String translatedName(IConfigBase config, String fallback) {
        Entry entry = ENTRIES.get(config);
        return entry == null ? fallback : StringUtils.getTranslatedOrFallback(entry.nameKey(), fallback);
    }

    public static String translatedComment(IConfigBase config, String fallback) {
        Entry entry = ENTRIES.get(config);
        return entry == null ? fallback : StringUtils.getTranslatedOrFallback(entry.commentKey(), fallback);
    }

    public record Entry(String nameKey, String commentKey, BooleanSupplier visible) {
    }
}
