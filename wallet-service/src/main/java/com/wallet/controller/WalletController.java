package com.wallet.controller;

import com.wallet.dto.WalletBalanceResponse;
import com.wallet.dto.WalletOperationRequest;
import com.wallet.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/wallet")
    public ResponseEntity<Void> performOperation(@Valid @RequestBody WalletOperationRequest request) {
        walletService.performOperation(request.walletId(), request.operationType(), request.amount());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/wallets/{walletId}")
    public ResponseEntity<WalletBalanceResponse> getBalance(@PathVariable UUID walletId) {
        var wallet = walletService.getWallet(walletId);
        return ResponseEntity.ok(new WalletBalanceResponse(wallet.getId(), wallet.getBalance()));
    }
}
