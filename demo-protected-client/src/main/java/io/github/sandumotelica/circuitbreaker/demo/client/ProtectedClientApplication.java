package io.github.sandumotelica.circuitbreaker.demo.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ProtectedClientProperties.class)
public class ProtectedClientApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProtectedClientApplication.class, args);
    }
}
