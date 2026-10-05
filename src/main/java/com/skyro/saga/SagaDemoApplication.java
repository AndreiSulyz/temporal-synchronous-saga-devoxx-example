package com.skyro.saga;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SagaDemoApplication {

    static void main(String[] args) {
        SpringApplication.run(SagaDemoApplication.class, args);
    }

}
