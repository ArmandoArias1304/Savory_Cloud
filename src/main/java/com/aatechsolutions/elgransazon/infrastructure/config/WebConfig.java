package com.aatechsolutions.elgransazon.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Configuración para servir archivos estáticos subidos por usuarios
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Value("${file.upload.base-path:src/main/resources/static}")
    private String basePath;
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Permitir servir imágenes subidas desde /uploads/**
        String absolutePath = Paths.get(basePath).toAbsolutePath().toString().replace("\\", "/");
        
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + absolutePath + "/uploads/");
    }
}
