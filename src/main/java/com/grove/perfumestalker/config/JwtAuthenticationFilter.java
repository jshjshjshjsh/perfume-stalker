package com.grove.perfumestalker.config;

import com.grove.perfumestalker.notion.util.NotionTokenUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final NotionTokenUtils tokenUtils;

    // 💡 팩폭 해결: 인증 검사를 '건너뛸' 경로들을 여기에 깔끔하게 정의!
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        return path.equals("/") ||
                path.startsWith("/index.html") ||
                path.startsWith("/favicon.ico") ||
                path.startsWith("/static/") ||
                path.startsWith("/.well-known/") ||
                path.startsWith("/api/v1/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // 💡 2. 헤더 검증 (이제 화면 띄우는 요청은 여기까지 안 오고 무사통과됨)
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("🚨 [인증 실패] 토큰 누락: {}", path);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        // 💡 3. 토큰 유효성 검증
        String token = authHeader.substring(7);
        if (!tokenUtils.validateToken(token)) {
            log.warn("🚨 [인증 실패] 유효하지 않은 토큰: {}", path);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        // 💡 4. 데이터 추출 및 Request Attribute에 주입
        String userId = tokenUtils.getUserIdFromToken(token);
        String userPageId = tokenUtils.getUserPageIdFromToken(token);

        request.setAttribute("userId", userId);
        request.setAttribute("userPageId", userPageId);

        // 💡 5. 다음 필터 또는 컨트롤러로 이동
        filterChain.doFilter(request, response);
    }
}