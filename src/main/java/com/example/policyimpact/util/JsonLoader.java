package com.example.policyimpact.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.Map;

public class JsonLoader {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static Map<String, Object> loadJson(String resourceName) {
        try {
            ClassPathResource resource = new ClassPathResource(resourceName);
            InputStream inputStream = resource.getInputStream();
            return mapper.readValue(inputStream, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load JSON: " + resourceName, e);
        }
    }
}