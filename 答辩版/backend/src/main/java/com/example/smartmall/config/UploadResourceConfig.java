package com.example.smartmall.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class UploadResourceConfig implements WebMvcConfigurer {
  private final String resourceLocation;

  public UploadResourceConfig(@Value("${app.upload-dir:uploads}") String uploadDir) {
    this.resourceLocation = Path.of(uploadDir).toAbsolutePath().normalize().toUri().toString();
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/uploads/**").addResourceLocations(resourceLocation);
  }
}
