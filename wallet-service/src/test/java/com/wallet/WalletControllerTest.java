package com.wallet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.dto.OperationType;
import com.wallet.dto.WalletOperationRequest;
import com.wallet.model.Wallet;
import com.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class WalletControllerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired WalletRepository walletRepository;

    private UUID walletId;

    @BeforeEach
    void setUp() {
        walletRepository.deleteAll();
        var wallet = new Wallet();
        wallet.setId(UUID.randomUUID());
        wallet.setBalance(new BigDecimal("1000.00"));
        walletRepository.save(wallet);
        walletId = wallet.getId();
    }

    // --- POST /api/v1/wallet ---

    @Test
    void deposit_success_returns200() throws Exception {
        var request = new WalletOperationRequest(walletId, OperationType.DEPOSIT, new BigDecimal("500.00"));

        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/wallets/" + walletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1500.00));
    }

    @Test
    void withdraw_success_returns200() throws Exception {
        var request = new WalletOperationRequest(walletId, OperationType.WITHDRAW, new BigDecimal("300.00"));

        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/wallets/" + walletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(700.00));
    }

    @Test
    void withdraw_insufficientFunds_returns422() throws Exception {
        var request = new WalletOperationRequest(walletId, OperationType.WITHDRAW, new BigDecimal("9999.00"));

        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Insufficient funds"));
    }

    @Test
    void post_walletNotFound_returns404() throws Exception {
        var request = new WalletOperationRequest(UUID.randomUUID(), OperationType.DEPOSIT, new BigDecimal("100.00"));

        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void post_invalidJson_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void post_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"walletId\":\"" + walletId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void post_negativeAmount_returns400() throws Exception {
        var request = new WalletOperationRequest(walletId, OperationType.DEPOSIT, new BigDecimal("-100.00"));

        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_zeroAmount_returns400() throws Exception {
        var request = new WalletOperationRequest(walletId, OperationType.DEPOSIT, BigDecimal.ZERO);

        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // --- GET /api/v1/wallets/{walletId} ---

    @Test
    void getBalance_success_returnsWalletAndBalance() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/" + walletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(walletId.toString()))
                .andExpect(jsonPath("$.balance").value(1000.00));
    }

    @Test
    void getBalance_walletNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void getBalance_invalidUuid_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    // --- Concurrency: 50 simultaneous deposits must all succeed ---

    @Test
    void concurrentDeposits_allSucceed_balanceIsCorrect() throws Exception {
        int threads = 50;
        BigDecimal depositAmount = new BigDecimal("10.00");
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                var request = new WalletOperationRequest(walletId, OperationType.DEPOSIT, depositAmount);
                try {
                    var result = mockMvc.perform(post("/api/v1/wallet")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                            .andReturn();
                    return result.getResponse().getStatus();
                } catch (Exception e) {
                    return 500;
                }
            }));
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        for (Future<Integer> future : futures) {
            assertThat(future.get()).isEqualTo(200);
        }

        // 1000 + (50 * 10) = 1500
        mockMvc.perform(get("/api/v1/wallets/" + walletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1500.00));
    }
}
