package com.grove.perfumestalker.notion;

import org.springframework.stereotype.Component;

@Component
public class NotionModule {

    // 2026 스펙 방어용: 하이픈 없는 ID가 들어와도 무조건 표준 UUID(8-4-4-4-12)로 변환
    public String formatUuid(String id) {
        String cleanId = id.trim().replace("-", "");
        if (cleanId.length() != 32) return id;
        return cleanId.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                "$1-$2-$3-$4-$5"
        );
    }
}
