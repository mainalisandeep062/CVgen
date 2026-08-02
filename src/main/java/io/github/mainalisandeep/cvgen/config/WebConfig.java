package io.github.mainalisandeep.cvgen.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

	private static final String STATIC_LOCATION = "classpath:/static/";

	private final SecurityProperties securityProperties;

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/assets/**")
				.addResourceLocations(STATIC_LOCATION)
				.setCachePeriod(securityProperties.getWeb().getAssetsCachePeriod());
		registry.addResourceHandler("/favicon.ico")
				.addResourceLocations(STATIC_LOCATION);
	}
}
