package com.els.backend;

import com.els.backend.service.NewtonService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
        double res = NewtonService.getExpectedReturn("VFIAX");
        System.out.println("Expected Value for VFIAX: " + res);
    }
}
