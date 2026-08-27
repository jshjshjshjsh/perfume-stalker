package com.grove.perfumestalker.notion;

import java.util.List;
import java.util.Map;

public final class NotionParserUtils {

    // 유틸리티 클래스이므로 인스턴스화 방지
    private NotionParserUtils() {}

    private static Map<String, Object> findPropertyIgnoreCase(Map<String, Object> props, String key) {
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return (Map<String, Object>) entry.getValue();
            }
        }
        return null;
    }

    public static String extractDate(Map<String, Object> props, String key) {
        try {
            Map<String, Object> prop = findPropertyIgnoreCase(props, key);
            if (prop == null || !"date".equals(prop.get("type"))) return "";
            Map<String, Object> dateObj = (Map<String, Object>) prop.get("date");
            return dateObj != null ? (String) dateObj.get("start") : "";
        } catch (Exception e) { return ""; }
    }

    @SuppressWarnings("unchecked")
    public static String extractSelect(Map<String, Object> props, String key) {
        try {
            Map<String, Object> prop = findPropertyIgnoreCase(props, key);
            if (prop == null || !"select".equals(prop.get("type"))) return "";
            Map<String, Object> selectObj = (Map<String, Object>) prop.get("select");
            return selectObj != null ? (String) selectObj.get("name") : "";
        } catch (Exception e) { return ""; }
    }

    @SuppressWarnings("unchecked")
    public static String extractPerfumeName(Map<String, Object> props, String key) {
        try {
            Map<String, Object> prop = findPropertyIgnoreCase(props, key);
            if (prop == null) return "";

            String type = (String) prop.get("type");
            if ("rollup".equals(type)) {
                Map<String, Object> rollup = (Map<String, Object>) prop.get("rollup");
                List<Map<String, Object>> array = (List<Map<String, Object>>) rollup.get("array");
                if (array == null || array.isEmpty()) return "";
                List<Map<String, Object>> titleList = (List<Map<String, Object>>) array.get(0).get("title");
                return titleList.isEmpty() ? "" : (String) ((Map<String, Object>) titleList.get(0).get("text")).get("content");
            } else if ("title".equals(type) || "rich_text".equals(type)) {
                List<Map<String, Object>> textList = (List<Map<String, Object>>) prop.get(type);
                return textList.isEmpty() ? "" : (String) ((Map<String, Object>) textList.get(0).get("text")).get("content");
            }
            return "";
        } catch (Exception e) { return ""; }
    }

    @SuppressWarnings("unchecked")
    public static String extractRollupImage(Map<String, Object> props, String key) {
        try {
            Map<String, Object> prop = findPropertyIgnoreCase(props, key);
            if (prop == null || !"rollup".equals(prop.get("type"))) return "";

            Map<String, Object> rollup = (Map<String, Object>) prop.get("rollup");
            List<Map<String, Object>> array = (List<Map<String, Object>>) rollup.get("array");
            if (array == null || array.isEmpty()) return "";

            Map<String, Object> firstItem = array.get(0);
            if ("url".equals(firstItem.get("type"))) {
                String url = (String) firstItem.get("url");
                return url != null ? url : "";
            }
            return "";
        } catch (Exception e) { return ""; }
    }

    public static String extractNumber(Map<String, Object> props, String key) {
        try {
            Map<String, Object> prop = findPropertyIgnoreCase(props, key);
            if (prop == null || !"number".equals(prop.get("type"))) return "";
            Object num = prop.get("number");
            return num != null ? String.valueOf(num) : "";
        } catch (Exception e) { return ""; }
    }
}