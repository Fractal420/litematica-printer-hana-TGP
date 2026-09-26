package me.aleksilassila.litematica.printer.utils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class FilterUtils {

    private static final char TAG_PREFIX = '#';
    private static final String SPLIT_SEPARATOR = ",";
    private static final String CONTAINS_FLAG = "c";
    private static final char STATE_START = '[';
    private static final char STATE_END = ']';
    private static final char PROPERTY_EQUALS = '=';
    private static final int MAX_PARSED_FILTER_CACHE_SIZE = 512;
    private static final Map<String, ParsedFilter> PARSED_FILTER_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ParsedFilter> eldest) {
                    return this.size() > MAX_PARSED_FILTER_CACHE_SIZE;
                }
            }
    );

    public static boolean matchString(String targetStr, String matchStr, String[] matchRules) {

        if (targetStr == null || matchStr == null) {
            return false;
        }

        boolean enableContainsMatch = false;
        for (String rule : matchRules) {
            if (CONTAINS_FLAG.equals(rule)) {
                enableContainsMatch = true;
                break;
            }
        }
        boolean containsMatchResult = enableContainsMatch && targetStr.contains(matchStr);

        boolean exactMatchResult = targetStr.equals(matchStr);

        return containsMatchResult || exactMatchResult;
    }

    public static boolean matchBlockName(String expectedName, BlockState blockState) {
        return matchName(expectedName, blockState);
    }

    public static boolean matchItemName(String expectedName, ItemStack itemStack) {
        return matchName(expectedName, itemStack);
    }

    public static boolean matchName(String expectedName, Object targetObj) {

        if (expectedName == null || targetObj == null) {
            return false;
        }

        ParsedFilter parsedFilter = parseExpectedName(expectedName);
        String coreName = parsedFilter.coreName();
        String[] matchRules = parsedFilter.matchRules();

        String targetRegistryName = getTargetRegistryName(targetObj);
        if (targetRegistryName == null) {
            return false;
        }
        if (coreName.isEmpty()) {
            return false;
        }

        if (targetObj instanceof BlockState blockState) {
            ParsedBlockStateFilter stateFilter = parsedFilter.stateFilter();
            if (stateFilter != null) {
                return matchBlockStateFilter(blockState, stateFilter, matchRules);
            }
        }

        if (coreName.startsWith(String.valueOf(TAG_PREFIX))) {
            String tagName = coreName.substring(1);

            if (targetObj instanceof BlockState blockState) {
                return matchBlockTag(blockState, tagName, matchRules);
            } else if (targetObj instanceof ItemStack itemStack) {
                return matchItemTag(itemStack, tagName, matchRules);
            }
            return false;
        }

        String targetDisplayName = getTargetDisplayName(targetObj);
        if (targetDisplayName == null) {
            return false;
        }

        if (matchString(targetDisplayName, coreName, matchRules)
                || matchString(targetRegistryName, coreName, matchRules)) {
            return true;
        }
        return PinYinSearchUtils.getPinYin(targetDisplayName)
                .stream()
                .anyMatch(pinyin -> matchString(pinyin, coreName, matchRules));
    }

    private static ParsedFilter parseExpectedName(String expectedName) {
        ParsedFilter cached = PARSED_FILTER_CACHE.get(expectedName);
        if (cached != null) {
            return cached;
        }
        List<String> parts = splitTopLevel(expectedName, SPLIT_SEPARATOR.charAt(0));
        String coreName = parts.isEmpty() ? "" : parts.get(0).trim();
        String[] matchRules = parts.size() > 1
                ? parts.subList(1, parts.size()).stream()
                .map(String::trim)
                .toArray(String[]::new)
                : new String[0];
        ParsedFilter parsed = new ParsedFilter(coreName, matchRules, parseBlockStateFilter(coreName));
        PARSED_FILTER_CACHE.put(expectedName, parsed);
        return parsed;
    }

    static int parsedFilterCacheSize() {
        return PARSED_FILTER_CACHE.size();
    }

    private static List<String> splitTopLevel(String input, char separator) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int stateDepth = 0;

        for (int i = 0; i < input.length(); i++) {
            char currentChar = input.charAt(i);
            if (currentChar == STATE_START) {
                stateDepth++;
            } else if (currentChar == STATE_END && stateDepth > 0) {
                stateDepth--;
            }

            if (currentChar == separator && stateDepth == 0) {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }

            current.append(currentChar);
        }

        parts.add(current.toString());
        return parts;
    }

    private static ParsedBlockStateFilter parseBlockStateFilter(String coreName) {
        int stateStartIndex = coreName.indexOf(STATE_START);
        int stateEndIndex = coreName.lastIndexOf(STATE_END);

        if (stateStartIndex < 0 && stateEndIndex < 0) {
            return null;
        }
        if (stateStartIndex <= 0 || stateEndIndex != coreName.length() - 1 || stateStartIndex > stateEndIndex) {
            return ParsedBlockStateFilter.invalid();
        }

        String blockName = coreName.substring(0, stateStartIndex).trim();
        String stateExpression = coreName.substring(stateStartIndex + 1, stateEndIndex).trim();
        if (blockName.isEmpty()) {
            return ParsedBlockStateFilter.invalid();
        }
        if (stateExpression.isEmpty()) {
            return new ParsedBlockStateFilter(blockName, Map.of(), true);
        }

        Map<String, String> propertyFilters = new LinkedHashMap<>();
        for (String propertyExpression : splitTopLevel(stateExpression, SPLIT_SEPARATOR.charAt(0))) {
            String trimmedPropertyExpression = propertyExpression.trim();
            int equalsIndex = trimmedPropertyExpression.indexOf(PROPERTY_EQUALS);
            if (equalsIndex <= 0 || equalsIndex == trimmedPropertyExpression.length() - 1) {
                return ParsedBlockStateFilter.invalid();
            }

            String propertyName = trimmedPropertyExpression.substring(0, equalsIndex).trim();
            String propertyValue = trimmedPropertyExpression.substring(equalsIndex + 1).trim();
            if (propertyName.isEmpty() || propertyValue.isEmpty()) {
                return ParsedBlockStateFilter.invalid();
            }

            propertyFilters.put(propertyName, propertyValue);
        }

        return new ParsedBlockStateFilter(blockName, propertyFilters, true);
    }

    private static boolean matchBlockStateFilter(BlockState blockState, ParsedBlockStateFilter stateFilter, String[] matchRules) {
        if (!stateFilter.valid()) {
            return false;
        }
        if (!matchPlainName(stateFilter.blockName(), blockState, matchRules)) {
            return false;
        }

        for (Map.Entry<String, String> entry : stateFilter.propertyFilters().entrySet()) {
            Property<?> property = findPropertyByName(blockState, entry.getKey());
            if (property == null || !matchPropertyValue(blockState, property, entry.getValue())) {
                return false;
            }
        }

        return true;
    }

    private static boolean matchPlainName(String coreName, Object targetObj, String[] matchRules) {
        String targetDisplayName = getTargetDisplayName(targetObj);
        String targetRegistryName = getTargetRegistryName(targetObj);
        if (targetDisplayName == null || targetRegistryName == null) {
            return false;
        }

        if (matchString(targetDisplayName, coreName, matchRules)
                || matchString(targetRegistryName, coreName, matchRules)) {
            return true;
        }
        return PinYinSearchUtils.getPinYin(targetDisplayName)
                .stream()
                .anyMatch(pinyin -> matchString(pinyin, coreName, matchRules));
    }

    private static Property<?> findPropertyByName(BlockState blockState, String propertyName) {
        for (Property<?> property : blockState.getProperties()) {
            if (property.getName().equalsIgnoreCase(propertyName)) {
                return property;
            }
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean matchPropertyValue(BlockState blockState, Property<?> property, String expectedValue) {
        return getPropertyValueName(blockState, (Property) property).equalsIgnoreCase(expectedValue);
    }

    private static <T extends Comparable<T>> String getPropertyValueName(BlockState blockState, Property<T> property) {
        return property.getName(blockState.getValue(property));
    }

    private static String getTargetRegistryName(Object targetObj) {
        if (targetObj instanceof BlockState blockState) {
            return BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString();
        } else if (targetObj instanceof ItemStack itemStack) {
            return BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString();
        }
        return null;
    }

    private static String getTargetDisplayName(Object targetObj) {
        if (targetObj instanceof BlockState blockState) {
            return blockState.getBlock().getName().getString();
        } else if (targetObj instanceof ItemStack itemStack) {
            return itemStack.getHoverName().getString();
        }
        return null;
    }

    private static boolean matchBlockTag(BlockState blockState, String tagName, String[] matchRules) {
        if (tagName.isEmpty()) {
            return false;
        }

        Stream<TagKey<Block>> blockTagStream = blockState.tags();
        return blockTagStream
                .map(tag -> tag.location().toString())
                .anyMatch(tagFullName -> matchTagName(tagFullName, tagName, matchRules));
    }

    private static boolean matchItemTag(ItemStack itemStack, String tagName, String[] matchRules) {
        if (tagName.isEmpty()) {
            return false;
        }

        boolean itemTagMatched = itemStack.tags()
                .map(tag -> tag.location().toString())
                .anyMatch(tagFullName -> matchTagName(tagFullName, tagName, matchRules));
        if (itemTagMatched) {
            return true;
        }
        if (!(itemStack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        return blockItem.getBlock().defaultBlockState().tags()
                .map(tag -> tag.location().toString())
                .anyMatch(tagFullName -> matchTagName(tagFullName, tagName, matchRules));
    }

    private static boolean matchTagName(String actual, String requested, String[] matchRules) {

        return matchString(actual, requested, matchRules)
                || "minecraft:carpets".equals(requested) && "minecraft:wool_carpets".equals(actual);
    }

    private record ParsedFilter(String coreName, String[] matchRules, ParsedBlockStateFilter stateFilter) {
    }

    private record ParsedBlockStateFilter(String blockName, Map<String, String> propertyFilters, boolean valid) {
        private static ParsedBlockStateFilter invalid() {
            return new ParsedBlockStateFilter("", Map.of(), false);
        }
    }
}
