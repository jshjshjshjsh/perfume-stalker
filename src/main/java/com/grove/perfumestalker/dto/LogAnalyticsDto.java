package com.grove.perfumestalker.dto;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

public record LogAnalyticsDto(
        double temp,
        double humidity,
        double rate,
        List<String> top,
        List<String> middle,
        List<String> base,
        List<String> general
) {
    // 💡 4계층 노트를 중복 없이 하나로 합쳐주는 헬퍼 메서드
    public Set<String> getAllUniqueNotes() {
        Set<String> allNotes = new HashSet<>();
        if (top != null) allNotes.addAll(top);
        if (middle != null) allNotes.addAll(middle);
        if (base != null) allNotes.addAll(base);
        if (general != null) allNotes.addAll(general);
        return allNotes;
    }
}