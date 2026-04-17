package com.metacoding.order.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtVerifier {

    private final String secret;

    public JwtVerifier(@Value("${jwt.secret}") String secret) {
        this.secret = secret;
    }

    // 토큰 검증 후 userId 반환, 실패 시 null
    public Integer verifyAndGetUserId(String token) {
        try {
            DecodedJWT decoded = JWT.require(Algorithm.HMAC512(secret)).build().verify(token);
            return Integer.parseInt(decoded.getSubject());
        } catch (Exception e) {
            return null;
        }
    }
}
