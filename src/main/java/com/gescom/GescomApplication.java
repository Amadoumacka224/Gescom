package com.gescom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class GescomApplication {

    public static void main(String[] args) {
        SpringApplication.run(GescomApplication.class, args);
    }

}
