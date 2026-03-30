package com.els.backend.controller;

import com.els.backend.database.funds.MutualFundStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/funds")
@CrossOrigin(origins = "${app.frontend-origin}")
public class MutualFundsController {

    private final MutualFundStore mutualFundStore;

    public MutualFundsController(MutualFundStore mutualFundStore) {
        this.mutualFundStore = mutualFundStore;
    }

    @GetMapping
    public ResponseEntity<List<MutualFundStore.MutualFund>> listAll() {
        return ResponseEntity.ok(mutualFundStore.listAll());
    }

    @GetMapping("/{ticker}")
    public ResponseEntity<MutualFundStore.MutualFund> findByTicker(
            @PathVariable("ticker") String ticker
    ) {
        if (ticker == null || ticker.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        return mutualFundStore.findByTicker(ticker.trim().toUpperCase())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
