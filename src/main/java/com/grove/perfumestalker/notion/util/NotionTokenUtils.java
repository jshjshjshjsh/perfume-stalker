package com.grove.perfumestalker.notion.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class NotionTokenUtils {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration:259200000}") // 기본값 72시간
    private long expiration;

    private SecretKey key;

    @PostConstruct
    protected void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // 토큰 발급 (페이로드에 노션 유저 Page ID를 담는다!)
    public String generateToken(String userId, String userPageId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .setSubject(userId)
                .claim("userPageId", userPageId) // 노션 Relation에 쓸 고유 ID
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // 토큰 검증
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 토큰에서 유저 Page ID 뽑아오기
    public String getUserPageIdFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.get("userPageId", String.class);
    }

    // 2026 스펙 방어용: 하이픈 없는 ID가 들어와도 무조건 표준 UUID(8-4-4-4-12)로 변환
    public String formatUuid(String id) {
        String cleanId = id.trim().replace("-", "");
        if (cleanId.length() != 32) return id;
        return cleanId.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                "$1-$2-$3-$4-$5"
        );
    }

    // 토큰에서 로그인 아이디(Subject) 추출
    public String getUserIdFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }
}
