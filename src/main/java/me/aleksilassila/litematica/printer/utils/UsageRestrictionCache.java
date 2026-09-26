package me.aleksilassila.litematica.printer.utils;

import fi.dy.masa.malilib.util.restrictions.UsageRestriction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class UsageRestrictionCache {
    private String source = "";
    private UsageRestriction.ListType listType = UsageRestriction.ListType.NONE;
    private List<String> listCache = List.of();
    private String[] filters = new String[0];

    public boolean allows(String source, UsageRestriction.ListType listType, List<String> filters, BlockState blockState) {
        this.update(source, listType, filters);
        if (this.listType == UsageRestriction.ListType.BLACKLIST) {
            return !this.matchesAny(blockState);
        }
        if (this.listType == UsageRestriction.ListType.WHITELIST) {
            return this.matchesAny(blockState);
        }
        return true;
    }

    public boolean allows(String source, UsageRestriction.ListType listType, List<String> blacklist, List<String> whitelist, BlockState blockState) {
        return this.allows(source, listType, selectFilters(listType, blacklist, whitelist), blockState);
    }

    public static List<String> selectFilters(UsageRestriction.ListType listType, List<String> blacklist, List<String> whitelist) {
        if (listType == UsageRestriction.ListType.BLACKLIST) {
            return blacklist;
        }
        if (listType == UsageRestriction.ListType.WHITELIST) {
            return whitelist;
        }
        return List.of();
    }

    private void update(String source, UsageRestriction.ListType listType, List<String> filters) {
        if (source.equals(this.source) && listType == this.listType && filters.equals(this.listCache)) {
            return;
        }
        this.source = source;
        this.listType = listType;
        this.listCache = new ArrayList<>(filters);
        this.filters = this.listCache.toArray(new String[0]);
    }

    private boolean matchesAny(BlockState blockState) {
        for (String filter : this.filters) {
            if (FilterUtils.matchBlockName(filter, blockState)) {
                return true;
            }
        }
        return false;
    }
}
