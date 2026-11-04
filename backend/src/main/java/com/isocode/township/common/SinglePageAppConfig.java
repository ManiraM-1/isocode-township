package com.isocode.township.common;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

@Configuration
public class SinglePageAppConfig implements WebMvcConfigurer {

    private static final String APP_ENTRY = "index.html";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new ClientRouteFallbackResolver());
    }

    static final class ClientRouteFallbackResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
                return requested;
            }
            if (!isClientRoute(resourcePath)) {
                return null;
            }
            Resource entry = location.createRelative(APP_ENTRY);
            return entry.exists() && entry.isReadable() ? entry : null;
        }

        static boolean isClientRoute(String resourcePath) {
            String lastSegment = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
            return !resourcePath.startsWith("api/") && !resourcePath.equals("api")
                    && !resourcePath.startsWith("actuator") && !lastSegment.contains(".");
        }
    }
}
