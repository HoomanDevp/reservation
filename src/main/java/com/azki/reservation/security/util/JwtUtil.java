package com.azki.reservation.security.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Component
public class JwtUtil {

    private static final String secret = "mysecretkey12345678901234567890"; // حداقل ۲۵ کاراکتر
    private static final long expiration = 86400000; // 1 روز

    private Key getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    /**
     * Generates a JWT token with user information
     *
     * @param email User's email
     * @return The generated JWT token
     */
    public String generateToken(String email) {
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getKey())
                .compact();
    }
    /**
     * Extracts the user email from a JWT token
     *
     * @param token JWT token
     * @return User's email
     */
    public String extractEmail(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    /**
     * Gets the expiration date of a JWT token as LocalDateTime
     *
     * @param token JWT token
     * @return Expiration date as LocalDateTime
     */
    public LocalDateTime getExpirationDate(String token) {
        Date expiration = extractAllClaims(token).getExpiration();
        return expiration.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    /**
     * Extracts all claims from a JWT token
     *
     * @param token JWT token
     * @return All claims in the token
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Validates if a JWT token is valid
     *
     * @param token JWT token
     * @return true if valid, false otherwise
     */
    public boolean isTokenValid(String token) {
        try {
            extractEmail(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}