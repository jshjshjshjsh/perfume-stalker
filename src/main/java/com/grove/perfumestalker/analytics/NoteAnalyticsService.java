package com.grove.perfumestalker.analytics;

import com.grove.perfumestalker.dto.LogAnalyticsDto;
import com.grove.perfumestalker.enums.SensoryClimate;
import com.grove.perfumestalker.notion.NotionLogService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoteAnalyticsService {

    private final NotionLogService notionLogService;

    public Mono<NoteAnalyticsResponse> analyzeUserNotes(String notionUserPageId) {
        return notionLogService.getAllLogsForAnalytics(notionUserPageId)
                .map(logs -> {
                    // 체감 기후 구간별 저장소 초기화
                    Map<SensoryClimate, Map<String, List<Double>>> rawStats = new EnumMap<>(SensoryClimate.class);
                    for (SensoryClimate climate : SensoryClimate.values()) {
                        rawStats.put(climate, new HashMap<>());
                    }

                    // 💡 로그를 순회하며 4계층 통합 노트를 기후별로 적립
                    for (LogAnalyticsDto logDto : logs) {
                        if (logDto.rate() == null || logDto.rate() <= 0) continue;
                        if (logDto.temp() == null) continue;

                        SensoryClimate climate = SensoryClimate.from(logDto.temp(), logDto.humidity());
                        Set<String> uniqueNotes = logDto.getAllUniqueNotes(); // 💡 Top, Middle, Base 중복 카운트 방지
                        if (uniqueNotes.isEmpty()) continue;

                        for (String note : uniqueNotes) {
                            rawStats.get(climate)
                                    .computeIfAbsent(note, k -> new ArrayList<>())
                                    .add(logDto.rate());
                        }
                    }

                    // 평점 평균 계산 및 인생/기피 노트 분류
                    Map<SensoryClimate, ZoneStats> result = new EnumMap<>(SensoryClimate.class);

                    rawStats.forEach((climate, noteRatingsMap) -> {
                        List<NoteScore> golden = new ArrayList<>();
                        List<NoteScore> warning = new ArrayList<>();

                        noteRatingsMap.forEach((noteName, ratings) -> {
                            int count = ratings.size();
                            if (count >= 2) { // 💡 최소 2회 이상 등장한 노트만 검증
                                double avg = ratings.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                                double roundedAvg = Math.round(avg * 10) / 10.0;

                                if (roundedAvg >= 3.5) {
                                    golden.add(new NoteScore(noteName, roundedAvg, count));
                                } else if (roundedAvg <= 2.5) {
                                    warning.add(new NoteScore(noteName, roundedAvg, count));
                                }
                            }
                        });

                        golden.sort(Comparator.comparing(NoteScore::getAverageRating).reversed());
                        warning.sort(Comparator.comparing(NoteScore::getAverageRating));

                        result.put(climate, new ZoneStats(golden, warning));
                    });

                    return new NoteAnalyticsResponse(result);
                });
    }

    @Data
    @AllArgsConstructor
    public static class NoteAnalyticsResponse {
        private Map<SensoryClimate, ZoneStats> climateAnalytics;
    }

    @Data
    @AllArgsConstructor
    public static class ZoneStats {
        private List<NoteScore> goldenNotes;
        private List<NoteScore> warningNotes;
    }

    @Data
    @AllArgsConstructor
    public static class NoteScore {
        private String noteName;
        private double averageRating;
        private int wearingCount;
    }
}