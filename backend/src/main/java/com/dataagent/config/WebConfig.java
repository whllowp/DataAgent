package com.dataagent.config;

import com.dataagent.security.ApiTokenFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class WebConfig {

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration(DataAgentProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = properties.getCors().getAllowedOrigins();
        if (origins == null || origins.isEmpty() || origins.contains("*")) {
            config.setAllowedOriginPatterns(List.of("*"));
        } else {
            config.setAllowedOriginPatterns(origins);
        }
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-Request-Id"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("dataAgentCorsFilter");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<ApiTokenFilter> apiTokenFilterRegistration(
            DataAgentProperties properties) {
        FilterRegistrationBean<ApiTokenFilter> registration =
                new FilterRegistrationBean<>(new ApiTokenFilter(properties));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addUrlPatterns("/api/*");
        registration.setName("apiTokenFilter");
        return registration;
    }
}
