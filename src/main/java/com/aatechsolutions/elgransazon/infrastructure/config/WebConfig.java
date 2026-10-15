package com.aatechsolutions.elgransazon.infrastructure.config;

import com.aatechsolutions.elgransazon.infrastructure.web.ReturnToLastListInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
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
    
    /**
     * Devuelve al usuario a la lista donde estaba (con su página y sus filtros) después de guardar,
     * activar o borrar. Se registra para todo el panel, así que cubre a todos los roles por igual.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ReturnToLastListInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/api/**", "/uploads/**", "/css/**", "/js/**",
                        "/images/**", "/favicon.ico", "/ws/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Permitir servir imágenes subidas desde /uploads/**
        String absolutePath = Paths.get(basePath).toAbsolutePath().toString().replace("\\", "/");
        
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + absolutePath + "/uploads/");
    }
}
