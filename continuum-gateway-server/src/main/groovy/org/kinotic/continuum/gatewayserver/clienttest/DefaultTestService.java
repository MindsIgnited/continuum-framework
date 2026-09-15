package org.kinotic.continuum.gatewayserver.clienttest;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Created by Navíd Mitchell 🤪 on 7/12/23.
 */
@Component
@Profile("clienttest")
public class DefaultTestService implements ITestService{

    private static final UUID TEST_UUID = UUID.randomUUID();

    @WithSpan
    @Override
    public String testMethodWithString(String value) {
        return "Hello "+ value;
    }

    @WithSpan
    @Override
    public CompletableFuture<String> testMethodWithDelay(String value, long delayMs) {
        long delay = Math.max(0, Math.min(delayMs, MAX_DELAY_MS));
        // Off the calling thread: the delay must not tie up a Vert.x event loop
        return CompletableFuture.supplyAsync(() -> "Hello " + value,
                                             CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS));
    }

    @WithSpan
    @Override
    public UUID getTestUUID(){
        return TEST_UUID;
    }

}
