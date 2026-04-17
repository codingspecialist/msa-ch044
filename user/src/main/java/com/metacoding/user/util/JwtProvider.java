package com.metacoding.user.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtProvider {

    private final String secret;
    private final Long expirationTime;

    public JwtProvider(@Value("${jwt.secret}") String secret,
                       @Value("${jwt.expiration}") Long expirationTime) {
        this.secret = secret;
        this.expirationTime = expirationTime;
    }

    // JWT 생성: subject=userId, claim=username
    public String create(int userId, String username) {
        return JWT.create()
                .withSubject(String.valueOf(userId))
                .withClaim("username", username)
                .withExpiresAt(new Date(System.currentTimeMillis() + expirationTime))
                .sign(Algorithm.HMAC512(secret));
    }
}
