package com.example.policyimpact;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            // add the exact origins you use for frontend dev servers (Codespaces URLs / localhost)
            .allowedOrigins(
                "https://urban-space-sniffle-j465r79745q2p647-5500.app.github.dev",
                "http://localhost:5500",
                "http://127.0.0.1:5500"
            )
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            // set to true only if you plan to use cookies / credentials and configure origins accordingly
            .allowCredentials(false)
            .maxAge(3600);
    }
}