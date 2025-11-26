package aforo.metering.tenant;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Thread-local storage for tenant context (organization ID and JWT token).
 * Matches the pattern used in newingestion and product_priceplan_service.
 */
public final class TenantContext {
    
    private static final ThreadLocal<Long> ORGANIZATION_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> JWT_TOKEN = new ThreadLocal<>();
    
    private TenantContext() {}
    
    public static void setOrganizationId(Long organizationId) {
        ORGANIZATION_ID.set(organizationId);
    }
    
    public static Long getOrganizationId() {
        return ORGANIZATION_ID.get();
    }
    
    public static Long requireOrganizationId() {
        Long orgId = ORGANIZATION_ID.get();
        if (orgId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing tenant context");
        }
        return orgId;
    }
    
    public static void setJwtToken(String token) {
        JWT_TOKEN.set(token);
    }
    
    public static String getJwtToken() {
        return JWT_TOKEN.get();
    }
    
    public static void clear() {
        ORGANIZATION_ID.remove();
        JWT_TOKEN.remove();
    }
}
