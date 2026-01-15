package ru.mifi.booking.bookingservice.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final String secret;
    private final long ttlSeconds;
    private final long serviceTtlSeconds;

    public JwtService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.ttl}") long ttlSeconds,
            @Value("${security.jwt.service-ttl:300}") long serviceTtlSeconds
    ) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("security.jwt.secret is empty");
        }
        if (ttlSeconds <= 0) {
            throw new IllegalStateException("security.jwt.ttl must be > 0");
        }
        if (serviceTtlSeconds <= 0) {
            throw new IllegalStateException("security.jwt.service-ttl must be > 0");
        }

        this.secret = secret;
        this.ttlSeconds = ttlSeconds;
        this.serviceTtlSeconds = serviceTtlSeconds;
    }
    public String generateToken(Long userId, String role) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role is empty");
        }
        return generateTokenInternal(String.valueOf(userId), role, ttlSeconds);
    }

    public String generateServiceToken() {
        return generateTokenInternal("booking-service", "SERVICE", serviceTtlSeconds);
    }

    private String generateTokenInternal(String subject, String role, long ttl) {
        try {
            Instant now = Instant.now();

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)                  // sub
                    .claim("role", role)               // role = USER|ADMIN|SERVICE
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(ttl)))
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256),
                    claims
            );

            signedJWT.sign(new MACSigner(secret));

            return signedJWT.serialize();

        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to generate JWT", e);
        }
    }
}
