package vn.hoidanit.jobhunter.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PermissionInterceptorConfiguration implements WebMvcConfigurer {
    private final PermissionInterceptor interceptor;
    public PermissionInterceptorConfiguration(PermissionInterceptor interceptor) { this.interceptor = interceptor; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/v1/**")
                .excludePathPatterns("/api/v1/auth/**", "/api/v1/files",
                        "/api/v1/resumes/quick-apply", "/api/v1/resumes/by-user",
                        "/api/v1/subscribers", "/api/v1/subscribers/skills",
                        "/api/v1/notifications/**", "/api/v1/shifts/my-shifts",
                        "/api/v1/users/change-password", "/api/v1/users/me");
    }
}
