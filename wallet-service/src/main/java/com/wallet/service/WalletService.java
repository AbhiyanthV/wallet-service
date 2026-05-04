package com.wallet.service;

import com.wallet.dto.OperationType;
import com.wallet.exception.InsufficientFundsException;
import com.wallet.exception.WalletNotFoundException;
import com.wallet.model.Wallet;
import com.wallet.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class WalletService {

    private final WalletRepository walletRepository;

    public WalletService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Transactional
    public void performOperation(UUID walletId, OperationType operationType, BigDecimal amount) {
        Wallet wallet = walletRepository.findByIdWithLock(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));

        if (operationType == OperationType.DEPOSIT) {
            wallet.setBalance(wallet.getBalance().add(amount));
        } else {
            if (wallet.getBalance().compareTo(amount) < 0) {
                throw new InsufficientFundsException();
            }
            wallet.setBalance(wallet.getBalance().subtract(amount));
        }
    }

    @Transactional(readOnly = true)
    public Wallet getWallet(UUID walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
    }
}
