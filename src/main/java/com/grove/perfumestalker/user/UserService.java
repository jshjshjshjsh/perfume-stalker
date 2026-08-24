package com.grove.perfumestalker.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.grove.perfumestalker.enums.NotionUser;
import com.grove.perfumestalker.notion.NotionModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final WebClient notionWebClient;
    private final NotionModule notionModule;

    @Value("${notion.db.user-data-source-id}")
    private String userDataSourceId;

    /**
     * 특정 유저의 DEFAULT_LOCATION을 조회 (없으면 기본값 "Busan" 반환)
     */
    public Mono<String> getDefaultLocation(String userId) {
        String formattedDataSourceId = notionModule.formatUuid(userDataSourceId);
        NotionUser userIdCol = NotionUser.USER_ID;

        Map<String, Object> queryBody = Map.of(
                "filter", Map.of(
                        "property", userIdCol.getColumnName(),
                        userIdCol.getPropertyType(), Map.of("equals", userId)
                )
        );

        return notionWebClient.post()
                .uri("/data_sources/{dbId}/query", formattedDataSourceId)
                .bodyValue(queryBody)
                .retrieve()
                .bodyToMono(NotionUserQueryResponse.class)
                .map(response -> {
                    if (response.results().isEmpty()) return "Busan";

                    var properties = response.results().get(0).properties();

                    if (properties == null || properties.defaultLocation() == null || properties.defaultLocation().richText().isEmpty()) {
                        return "Busan";
                    }

                    return properties.defaultLocation().richText().get(0).text().content();
                })
                .doOnError(e -> {
                    if (e instanceof WebClientResponseException ex) {
                        log.error("❌ 노션 계정 DB 조회 40x 에러 상세: {}", ex.getResponseBodyAsString());
                    } else {
                        log.error("❌ 계정 DB 조회 에러", e);
                    }
                })
                .onErrorReturn("Busan"); // 에러 나도 멈추지 않고 기본값 리턴
    }

    // --- 💡 내부 전용 JSON 파싱 DTO ---
    private record NotionUserQueryResponse(List<NotionPage> results) {
        private record NotionPage(Properties properties) {}

        private record Properties(
                @JsonProperty("DEFAULT_LOCATION") DefaultLocation defaultLocation
        ) {}

        private record DefaultLocation(@JsonProperty("rich_text") List<RichText> richText) {}
        private record RichText(Text text) {}
        private record Text(String content) {}
    }
}