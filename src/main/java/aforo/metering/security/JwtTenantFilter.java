package aforo.metering.security;

import aforo.metering.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Filter to extract organization ID from JWT claims or X-Organization-Id header
 * and store JWT token for propagation to external services.
 */
@Component
public class JwtTenantFilter extends OncePerRequestFilter {
    
    private static final Logger logger = LoggerFactory.getLogger(JwtTenantFilter.class);
    private static final List<String> CLAIM_KEYS = Arrays.asList(
            "organizationId", "orgId", "tenantId", "organization_id", "org_id", "tenant");
    
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        // First check X-Organization-Id header (for testing)
        String headerOrgId = request.getHeader("X-Organization-Id");
        if (headerOrgId != null && !headerOrgId.isBlank()) {
            try {
                TenantContext.setOrganizationId(Long.parseLong(headerOrgId.trim()));
                logger.debug("Tenant set from X-Organization-Id header: {}", headerOrgId);
            } catch (NumberFormatException e) {
                logger.warn("Invalid X-Organization-Id header value: {}", headerOrgId);
            }
        }
        
        // Extract from JWT
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            // Store the JWT token for propagation
            String tokenValue = jwt.getTokenValue();
            TenantContext.setJwtToken(tokenValue);
            
            // Extract organization ID from claims
            Map<String, Object> claims = jwt.getClaims();
            if (TenantContext.getOrganizationId() == null) {
                for (String key : CLAIM_KEYS) {
                    Object value = claims.get(key);
                    if (value != null) {
                        try {
                            Long orgId = Long.parseLong(value.toString());
                            TenantContext.setOrganizationId(orgId);
                            logger.debug("Tenant set from JWT claim '{}': {}", key, orgId);
                            break;
                        } catch (NumberFormatException e) {
                            logger.warn("Invalid numeric value for JWT claim '{}': {}", key, value);
                        }
                    }
                }
            }
            
            if (TenantContext.getOrganizationId() == null) {
                logger.debug("No tenant claim found in JWT");
            }
        }
        
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
