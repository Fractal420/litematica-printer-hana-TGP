package me.aleksilassila.litematica.printer.utils;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class PinYinSearchUtils {

    private static final HanyuPinyinOutputFormat PINYIN_FORMAT;
    private static final int MAX_CACHE_ENTRIES = 512;
    private static final int MAX_COMBINATIONS = 256;
    private static final Map<String, List<String>> CACHE = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<String>> eldest) {
            return this.size() > MAX_CACHE_ENTRIES;
        }
    };

    static {

        PINYIN_FORMAT = new HanyuPinyinOutputFormat();
        PINYIN_FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        PINYIN_FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        PINYIN_FORMAT.setVCharType(HanyuPinyinVCharType.WITH_V);
    }

    public static synchronized ArrayList<String> getPinYin(@Nullable String str) {

        if (str == null || str.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> cached = CACHE.get(str);
        if (cached != null) {
            return new ArrayList<>(cached);
        }

        char[] chars = str.toCharArray();

        List<String[]> charPinyinList = new ArrayList<>();

        try {
            for (char c : chars) {
                if (c < 128) {

                    charPinyinList.add(new String[]{String.valueOf(c)});
                } else {

                    String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(c, PINYIN_FORMAT);
                    if (pinyinArray == null || pinyinArray.length == 0) {

                        charPinyinList.add(new String[]{String.valueOf(c)});
                    } else {
                        charPinyinList.add(new LinkedHashSet<>(List.of(pinyinArray)).toArray(new String[0]));
                    }
                }
            }
        } catch (BadHanyuPinyinOutputFormatCombination e) {

            throw new RuntimeException("拼音格式配置错误，无法转换字符串：" + str, e);
        }

        ArrayList<String> result = generatePinyinCombinations(charPinyinList);
        CACHE.put(str, List.copyOf(result));
        return result;
    }

    public static boolean hasPinYin(@Nullable String zh, @Nullable String py) {

        if (zh == null || zh.isEmpty() || py == null || py.isEmpty()) {
            return false;
        }
        String lowerPy = py.toLowerCase();
        return getPinYin(zh).stream().anyMatch(s -> s.contains(lowerPy));
    }

    @NotNull
    private static ArrayList<String> generatePinyinCombinations(List<String[]> charPinyinList) {
        List<String> fullPinyinList = List.of("");
        List<String> shortPinyinList = List.of("");
        for (String[] currentPinyinArray : charPinyinList) {
            ArrayList<String> nextFull = new ArrayList<>();
            ArrayList<String> nextShort = new ArrayList<>();
            for (String existing : fullPinyinList) {
                for (String pinyin : currentPinyinArray) {
                    if (nextFull.size() >= MAX_COMBINATIONS) break;
                    nextFull.add(existing + pinyin);
                }
                if (nextFull.size() >= MAX_COMBINATIONS) break;
            }
            for (String existing : shortPinyinList) {
                for (String pinyin : currentPinyinArray) {
                    if (nextShort.size() >= MAX_COMBINATIONS) break;
                    nextShort.add(existing + pinyin.charAt(0));
                }
                if (nextShort.size() >= MAX_COMBINATIONS) break;
            }
            fullPinyinList = nextFull;
            shortPinyinList = nextShort;
        }
        LinkedHashSet<String> combined = new LinkedHashSet<>(fullPinyinList);
        combined.addAll(shortPinyinList);
        return new ArrayList<>(combined);
    }
}
