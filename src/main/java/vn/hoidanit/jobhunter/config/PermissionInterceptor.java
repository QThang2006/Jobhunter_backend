package vn.hoidanit.jobhunter.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import vn.hoidanit.jobhunter.service.AccessService;
import java.util.Set;

@Component
public class PermissionInterceptor implements HandlerInterceptor {
    private final AccessService access;
    public PermissionInterceptor(AccessService access) { this.access = access; }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String method = request.getMethod();
        if (path == null || "OPTIONS".equals(method)) return true;
        if ("GET".equals(method) && Set.of("/api/v1/companies", "/api/v1/companies/{id}",
                "/api/v1/jobs", "/api/v1/jobs/{id}", "/api/v1/skills").contains(path)) return true;
        if ("POST".equals(method) && "/api/v1/resumes".equals(path)) {
            access.currentUser();
            return true;
        }
        // These aliases use the permission of the underlying operation.
        if ("/api/v1/jobs/by-company".equals(path)) path = "/api/v1/jobs";
        if ("/api/v1/companies/by-user".equals(path)) path = "/api/v1/companies";
        if ("/api/v1/jobs/{id}/active".equals(path)) { path = "/api/v1/jobs"; method = "PUT"; }
        if ("/api/v1/shifts/employees".equals(path)) path = "/api/v1/shifts";
        if (Set.of("/api/v1/email/approve", "/api/v1/email/schedule-interview").contains(path)) {
            path = "/api/v1/resumes"; method = "PUT";
        }
        access.requirePermission(path, method);
        return true;
    }
}
