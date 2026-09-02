package com.somil.jobportal.config;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

//this configuration class will map requests for /photos to serve files from a directory
//on our file system
@Configuration
public class MvcConfig implements WebMvcConfigurer{

	private static final String UPLOAD_DIR = "photos";

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		// Override the default implementation to set up a custom resource handler.
		exposeDirectory(UPLOAD_DIR, registry);
	}

	private void exposeDirectory(String uploadDir, ResourceHandlerRegistry registry) {
		// Convert the uploadDir string to a Path 
		//Maps request starting with "/photos/**" to a file system location
		// file: <absolute path to photos directory>
		// The ** will match on all sub-directories
		Path path = Paths.get(uploadDir);
		registry.addResourceHandler("/"+uploadDir + "/**").addResourceLocations("file:"+path.toAbsolutePath()+"/");
		
	}
	
	
}
