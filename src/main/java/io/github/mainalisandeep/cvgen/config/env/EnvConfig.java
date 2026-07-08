package io.github.mainalisandeep.cvgen.config.env;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(
        value= "file:.env",
        factory = EnvPropertySourceFactory.class,
        ignoreResourceNotFound = true)
public class EnvConfig {
}