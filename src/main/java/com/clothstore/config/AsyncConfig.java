package com.clothstore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async executor for payment-verification work.
 *
 * <p>The {@code @Async} variant of {@code PaymentService.verifyAndConfirm}
 * runs on this pool so the HTTP request returns 202 Accepted immediately and
 * the customer can be redirected to {@code /orders} in parallel — the
 * {@code /orders} page polls {@code GET /orders/recent-paid/{id}} until the
 * background worker marks the order PAID.</p>
 *
 * <p>Pool sizing: core 2, max 8, queue 100 — small because each verify call
 * is short (one HTTP signature check + one DB write). A burst of concurrent
 * payments at peak is the only case where the queue matters; beyond 100
 * queued tasks, callers see a {@code TaskRejectedException} that the
 * {@code PaymentController} surfaces as a 503 so the client can retry.</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "paymentVerifyExecutor")
    public Executor paymentVerifyExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("paymentVerify-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.initialize();
        return executor;
    }
}