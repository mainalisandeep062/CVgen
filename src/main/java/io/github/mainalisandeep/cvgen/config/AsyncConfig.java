package io.github.mainalisandeep.cvgen.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@RequiredArgsConstructor
public class AsyncConfig {

	private final SecurityProperties securityProperties;


	@Bean(name = "applicationTaskExecutor")
	public Executor applicationTaskExecutor() {

		ThreadPoolTaskExecutor executor =
				new ThreadPoolTaskExecutor();

		SecurityProperties.Async async =
				securityProperties.getAsync();

		executor.setCorePoolSize(async.getCorePoolSize());
		executor.setMaxPoolSize(async.getMaxPoolSize());
		executor.setQueueCapacity(async.getQueueCapacity());
		executor.setThreadNamePrefix(async.getThreadNamePrefix());

		executor.initialize();

		return executor;
	}
}