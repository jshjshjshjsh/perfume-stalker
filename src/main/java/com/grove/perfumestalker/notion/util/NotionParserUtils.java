package com.grove.perfumestalker.notion.util;

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

    @SuppressWarnings("unchecked")
    public static String extractDefaultTitle(Map<String, Object> props) {
        try {
            for (Object value : props.values()) {
                Map<String, Object> prop = (Map<String, Object>) value;
                if ("title".equals(prop.get("type"))) {
                    List<Map<String, Object>> titleList = (List<Map<String, Object>>) prop.get("title");
                    return titleList.isEmpty() ? "Unknown" : (String) ((Map<String, Object>) titleList.get(0).get("text")).get("content");
                }
            }
            return "Unknown";
        } catch (Exception e) { return "Unknown"; }
    }

    @SuppressWarnings("unchecked")
    public static String extractRichText(Map<String, Object> props, String key) {
        try {
            Map<String, Object> prop = findPropertyIgnoreCase(props, key);
            if (prop == null) return "";

            String type = (String) prop.get("type");
            if (!"rich_text".equals(type) && !"title".equals(type)) return "";

            List<Map<String, Object>> textList = (List<Map<String, Object>>) prop.get(type);
            return textList.isEmpty() ? "" : (String) ((Map<String, Object>) textList.get(0).get("text")).get("content");
        } catch (Exception e) { return ""; }
    }

    // 노션 Relation 컬럼에서 연결된 Page ID(유저 ID)를 빼오는 유틸리티
    @SuppressWarnings("unchecked")
    public static String extractRelationId(Map<String, Object> props, String key) {
        try {
            Map<String, Object> prop = findPropertyIgnoreCase(props, key);
            if (prop == null) return "";

            List<Map<String, Object>> relations = (List<Map<String, Object>>) prop.get("relation");
            if (relations == null || relations.isEmpty()) return "";

            return (String) relations.get(0).get("id");
        } catch (Exception e) { return ""; }
    }

    // 노션 URL 속성 안전 추출기
    public static String extractUrl(Map<String, Object> props, String key) {
        try {
            Map<String, Object> prop = findPropertyIgnoreCase(props, key);
            if (prop == null) return "";

            String type = (String) prop.get("type");
            if ("url".equals(type)) {
                Object urlObj = prop.get("url");
                return urlObj != null ? String.valueOf(urlObj) : "";
            }
            // 만약 rich_text로 저장했다면 기존 메서드로 우회
            return extractRichText(props, key);
        } catch (Exception e) { return ""; }
    }

    // 다중 선택(multi_select) 타입 전용 추출 (TOP, MIDDLE, BASE, NOTES 용)
    @SuppressWarnings("unchecked")
    public static List<String> extractMultiSelect(Map<String, Object> props, String key) {
        try {
            Map<String, Object> prop = findPropertyIgnoreCase(props, key);
            if (prop == null || !"multi_select".equals(prop.get("type"))) return List.of();

            List<Map<String, Object>> multiSelect = (List<Map<String, Object>>) prop.get("multi_select");
            if (multiSelect == null) return List.of();

            return multiSelect.stream()
                    .map(m -> (String) m.get("name"))
                    .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) { return List.of(); }
    }
}