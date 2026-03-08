package com.els.backend;

import com.els.backend.service.FutureValueService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
        FutureValueService.FutureValueComputation projection =
                FutureValueService.computeFutureValue("VFIAX", 1000.0, 5.0);
        System.out.println("Beta Value for VFIAX: " + projection.beta());
        System.out.println("Expected Value for VFIAX: " + projection.expectedReturn());
        System.out.println("Future Value for VFIAX: " + projection.futureValue());
    }
}
