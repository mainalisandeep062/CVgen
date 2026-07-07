package io.github.mainalisandeep.cvgen.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final SecurityProperties securityProperties;

	public WebConfig(SecurityProperties securityProperties) {
		this.securityProperties = securityProperties;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
				.allowedOrigins(securityProperties.getCors().getAllowedOrigins().toArray(String[]::new))
				.allowedMethods(securityProperties.getCors().getAllowedMethods().toArray(String[]::new))
				.allowedHeaders(securityProperties.getCors().getAllowedHeaders().toArray(String[]::new))
				.exposedHeaders(securityProperties.getCors().getExposedHeaders().toArray(String[]::new))
				.allowCredentials(securityProperties.getCors().isAllowCredentials());
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/assets/**")
				.addResourceLocations("classpath:/static/")
				.setCachePeriod(3600);
		registry.addResourceHandler("/favicon.ico")
				.addResourceLocations("classpath:/static/");
	}
}
