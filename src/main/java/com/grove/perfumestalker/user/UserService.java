package com.grove.perfumestalker.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.grove.perfumestalker.dto.UserAccountCommand;
import com.grove.perfumestalker.enums.NotionUser;
import com.grove.perfumestalker.notion.util.NotionParserUtils;
import com.grove.perfumestalker.notion.util.NotionTokenUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;
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
    private final NotionTokenUtils notionTokenUtils;

    @Value("${notion.db.user-id}")
    private String userDataId;

    @Value("${notion.db.user-data-source-id}")
    private String userDataSourceId;

    public Mono<String> login(UserAccountCommand command) {
        String formattedDbId = notionTokenUtils.formatUuid(userDataSourceId);

        // 노션 DB에서 입력된 USER_ID와 똑같은 행(Row)만 긁어오는 필터 쿼리
        Map<String, Object> queryBody = Map.of(
                "filter", Map.of(
                        "property", "USER_ID",
                        "title", Map.of("equals", command.userId())
                )
        );

        return notionWebClient.post()
                .uri("/data_sources/{dbId}/query", formattedDbId)
                .bodyValue(queryBody)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(response -> {
                    List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
                    if (results == null || results.isEmpty()) {
                        return Mono.error(new RuntimeException("존재하지 않는 아이디입니다."));
                    }

                    Map<String, Object> userPage = results.get(0);
                    Map<String, Object> props = (Map<String, Object>) userPage.get("properties");

                    // 노션의 고유 Page ID (이걸 토큰에 넣어야 나중에 Relation 걸 때 쓸 수 있음)
                    String userPageId = (String) userPage.get("id");

                    // 비밀번호 파싱 (컬럼명이 PASSWORD이고, 타입이 rich_text라고 가정)
                    String savedPassword = NotionParserUtils.extractRichText(props, "PASSWORD");

                    if (!BCrypt.checkpw(command.rawPassword(), savedPassword)) {
                        return Mono.error(new RuntimeException("비밀번호가 일치하지 않습니다."));
                    }

                    // 로그인 성공! 토큰 발급
                    String token = notionTokenUtils.generateToken(command.userId(), userPageId);
                    log.info("✅ 유저 로그인 성공 및 토큰 발급: {}", command.userId());
                    return Mono.just(token);
                });
    }

    public Mono<String> signup(UserAccountCommand command) {
        String formattedUserId = notionTokenUtils.formatUuid(userDataId);
        String formattedDbId = notionTokenUtils.formatUuid(userDataSourceId);

        // 1. 중복 확인 쿼리
        Map<String, Object> queryBody = Map.of(
                "filter", Map.of(
                        "property", "USER_ID",
                        "title", Map.of("equals", command.userId())
                )
        );

        return notionWebClient.post()
                .uri("/data_sources/{dbId}/query", formattedDbId)
                .bodyValue(queryBody)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(response -> {
                    List<?> results = (List<?>) response.get("results");
                    if (results != null && !results.isEmpty()) {
                        return Mono.error(new RuntimeException("🚨 이미 존재하는 아이디입니다."));
                    }

                    String hashedPassword = BCrypt.hashpw(command.rawPassword(), BCrypt.gensalt());

                    // 2. 가입 진행 (새 행 생성)
                    Map<String, Object> properties = Map.of(
                            "USER_ID", Map.of("title", List.of(Map.of("text", Map.of("content", command.userId())))),
                            "PASSWORD", Map.of("rich_text", List.of(Map.of("text", Map.of("content", hashedPassword)))),
                            "NAME", Map.of("rich_text", List.of(Map.of("text", Map.of("content", command.name() != null ? command.name() : "")))),
                            "DEFAULT_LOCATION", Map.of("rich_text", List.of(Map.of("text", Map.of("content", command.defaultLocation() != null ? command.defaultLocation() : "Seoul")))),
                            "NOTI_ENABLED", Map.of("checkbox", command.notiEnabled())
                    );

                    Map<String, Object> body = Map.of(
                            "parent", Map.of("type", "database_id", "database_id", formattedUserId),
                            "properties", properties
                    );

                    return notionWebClient.post()
                            .uri("/pages")
                            .bodyValue(body)
                            .retrieve()
                            .bodyToMono(Void.class)
                            .then(Mono.just("✅ 회원가입 성공!"));
                });
    }

    /**
     * 특정 유저의 DEFAULT_LOCATION을 조회 (없으면 기본값 "Busan" 반환)
     */
    public Mono<String> getDefaultLocation(String userId) {
        String formattedDataSourceId = notionTokenUtils.formatUuid(userDataSourceId);
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