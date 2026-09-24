package com.knox.galaxy.config;

import com.knox.galaxy.service.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

/**
 * Serves the upload directory at {@code /public/storage/**}.
 *
 * <p>In production nginx has its own location block for this path and answers
 * from disk without the request ever reaching Java — this handler is what makes
 * the same URL work in local dev, and the fallback if nginx's block is ever
 * removed. Both read the same directory, so the stored URL is correct either
 * way.
 *
 * <p>Spring's PathResourceResolver confines every lookup to the registered
 * location, so a {@code ../} in the request path resolves to a 404 rather than
 * to a file elsewhere on the host.
 */
@Configuration
public class StorageWebConfig implements WebMvcConfigurer {

    @Autowired
    private StorageService storageService;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Trailing slash is required: without it Spring treats the location as a
        // file prefix and "/public/storage/products/x.png" would look for
        // "<root>products/x.png" one directory up.
        String location = storageService.getRoot().toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }

        registry.addResourceHandler(StorageService.PUBLIC_PATH + "/**")
                .addResourceLocations(location)
                // Filenames carry a timestamp and a UUID and are never rewritten,
                // so a stored image is immutable and can be cached hard.
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic());
    }
}
