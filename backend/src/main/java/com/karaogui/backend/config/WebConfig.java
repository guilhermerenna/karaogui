package com.karaogui.backend.config;

import com.karaogui.backend.auth.PlayerIdentityResolver;
import com.karaogui.backend.auth.TokenAuthFilter;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class WebConfig implements WebMvcConfigurer {

    private final TokenAuthFilter tokenAuthFilter;
    private final KaraoguiProperties props;

    WebConfig(TokenAuthFilter tokenAuthFilter, KaraoguiProperties props) {
        this.tokenAuthFilter = tokenAuthFilter;
        this.props = props;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(props.allowedOriginPatterns())
                .allowedMethods("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Range", "Accept-Ranges", "Content-Length")
                .allowCredentials(false)
                .maxAge(3600);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new PlayerIdentityResolver());
    }

    @Bean
    FilterRegistrationBean<TokenAuthFilter> tokenFilterRegistration() {
        FilterRegistrationBean<TokenAuthFilter> registration = new FilterRegistrationBean<>(tokenAuthFilter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);
        return registration;
    }
}
