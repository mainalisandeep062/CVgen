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
	public Executor applicationTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(Math.max(2, Runtime.getRuntime().availableProcessors()));
		executor.setMaxPoolSize(Math.max(4, Runtime.getRuntime().availableProcessors() * 2));
		executor.setQueueCapacity(250);
		executor.setThreadNamePrefix("cvgen-async-");
		executor.initialize();
		return executor;
	}
}
