package io.github.mainalisandeep.cvgen.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

	@Bean(name = "applicationTaskExecutor")
	public Executor applicationTaskExecutor(SecurityProperties securityProperties) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(securityProperties.getAsync().getCorePoolSize());
		executor.setMaxPoolSize(securityProperties.getAsync().getMaxPoolSize());
		executor.setQueueCapacity(securityProperties.getAsync().getQueueCapacity());
		executor.setThreadNamePrefix(securityProperties.getAsync().getThreadNamePrefix());
		executor.initialize();
		return executor;
	}
}
