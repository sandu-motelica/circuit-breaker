package io.github.sandumotelica.circuitbreaker.demo.unstable;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(UnstableServiceProperties.class)
public class UnstableServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UnstableServiceApplication.class, args);
    }
}
