package com.grove.perfumestalker.config;

import com.grove.perfumestalker.notion.util.NotionTokenUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements WebFilter {

    private final NotionTokenUtils tokenUtils;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 1. 인증이 필요 없는 Public API는 그냥 통과 (로그인, 회원가입 등)
        if (path.startsWith("/api/v1/auth/") || path.equals("/api/v1/weather")) {
            return chain.filter(exchange);
        }

        // 2. 헤더에서 토큰 추출
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("🚨 [인증 실패] 토큰이 없습니다: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // 3. 토큰 유효성 검증
        String token = authHeader.substring(7);
        if (!tokenUtils.validateToken(token)) {
            log.warn("🚨 [인증 실패] 유효하지 않은 토큰입니다: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // 4. 토큰에서 유저 고유 ID(Notion Page ID) 추출
        String userPageId = tokenUtils.getUserPageIdFromToken(token);

        // 5. Reactor Context에 userPageId를 담아서 하위 서비스로 흘려보냄
        // 이제 파라미터 주렁주렁 안 달아도, 서비스단에서 Mono.deferContextual()로 꺼내 쓸 수 있음.
        return chain.filter(exchange)
                .contextWrite(ctx -> ctx.put("userPageId", userPageId));
    }
}