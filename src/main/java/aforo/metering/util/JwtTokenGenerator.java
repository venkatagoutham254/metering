package aforo.metering.util;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;

/**
 * Utility to generate service-level JWT tokens for internal service-to-service communication.
 * Used by scheduled jobs that need to call external services without user context.
 */
@Component
@Slf4j
public class JwtTokenGenerator {

    private final String jwtSecret;
    private final String jwtIssuer;
    private final JWSSigner signer;

    public JwtTokenGenerator(
            @Value("${aforo.jwt.secret:change-me-please-change-me-32-bytes-min}") String jwtSecret,
            @Value("${aforo.jwt.issuer:aforo-metering}") String jwtIssuer) {
        this.jwtSecret = jwtSecret;
        this.jwtIssuer = jwtIssuer;
        try {
            this.signer = new MACSigner(jwtSecret.getBytes());
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize JWT signer", e);
        }
    }

    /**
     * Generate a service JWT token for internal API calls.
     * This token includes the organization ID and is valid for 2 hours.
     *
     * @param organizationId Organization ID to include in token
     * @return JWT token string
     */
    public String generateServiceToken(Long organizationId) {
        try {
            Instant now = Instant.now();
            Instant expiry = now.plusSeconds(7200); // 2 hours

            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .subject("metering-service")
                    .issuer(jwtIssuer)
                    .claim("organizationId", organizationId)
                    .claim("orgId", organizationId)
                    .claim("type", "service")
                    .claim("service", "metering")
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiry))
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256),
                    claimsSet
            );

            signedJWT.sign(signer);

            String token = signedJWT.serialize();
            log.debug("Generated service JWT token for organization {}", organizationId);
            return token;

        } catch (Exception e) {
            log.error("Failed to generate JWT token for organization {}: {}", organizationId, e.getMessage());
            throw new RuntimeException("Failed to generate service JWT token", e);
        }
    }
}
