package com.mockinterview.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${app.security.jwt.secret}")
    private String secretKey;

    @Value("${app.security.jwt.refresh-secret}")
    private String refreshSecretKey;

    @Value("${app.security.jwt.expiration-ms}")
    private long jwtExpiration;

    @Value("${app.security.jwt.refresh-expiration-ms}")
    private long refreshExpiration;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return extractClaim(token, false, claimsResolver);
    }

    public <T> T extractClaim(String token, boolean isRefresh, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token, isRefresh);
        return claimsResolver.apply(claims);
    }

    public String extractUsernameFromRefresh(String token) {
        return extractClaim(token, true, Claims::getSubject);
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails, jwtExpiration, false);
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails, refreshExpiration, true);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return generateToken(extraClaims, userDetails, jwtExpiration, false);
    }

    private String generateToken(Map<String, Object> extraClaims, UserDetails userDetails, long expiration, boolean isRefresh) {
        String key = isRefresh ? refreshSecretKey : secretKey;
        return Jwts
                .builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey(key))
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token, false);
    }

    public boolean isRefreshTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsernameFromRefresh(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token, true);
    }

    private boolean isTokenExpired(String token, boolean isRefresh) {
        return extractExpiration(token, isRefresh).before(new Date());
    }

    private Date extractExpiration(String token, boolean isRefresh) {
        return extractClaim(token, isRefresh, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token, boolean isRefresh) {
        String key = isRefresh ? refreshSecretKey : secretKey;
        return Jwts
                .parser()
                .verifyWith(getSignInKey(key))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignInKey(String key) {
        byte[] keyBytes = Decoders.BASE64.decode(key);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public long getJwtExpiration() {
        return jwtExpiration;
    }

    public long getRefreshExpiration() {
        return refreshExpiration;
    }
}
