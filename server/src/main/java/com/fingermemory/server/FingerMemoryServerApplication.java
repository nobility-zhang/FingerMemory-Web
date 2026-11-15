package com.fingermemory.server;

import com.fingermemory.server.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class FingerMemoryServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(FingerMemoryServerApplication.class, args);
    }
}
