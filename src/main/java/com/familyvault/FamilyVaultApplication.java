package com.familyvault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FamilyVaultApplication {

    public static void main(String[] args) {
        SpringApplication.run(FamilyVaultApplication.class, args);
    }

}
