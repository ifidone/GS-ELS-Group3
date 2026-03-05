package com.els.backend;

import com.els.backend.service.BetaService;
import com.els.backend.service.FutureValueService;
import com.els.backend.service.NewtonService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
        double res = NewtonService.getExpectedReturn("VFIAX");
        System.out.println("Expected Value for VFIAX: " + res);

        double beta = BetaService.getBeta("VFIAX");
        System.out.println("Beta Value for VFIAX: " + beta);

        double fv = FutureValueService.computeFutureValue("VFIAX", 1000.0, 5.0);
        System.out.println("Future Value for VFIAX: " + fv);
    }
}
