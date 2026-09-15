package org.kinotic.continuum.gatewayserver.clienttest;

import java.util.concurrent.CompletableFuture;

import org.kinotic.continuum.api.annotations.Publish;
import org.kinotic.continuum.api.annotations.Version;

import java.util.UUID;

/**
 * Created by Navíd Mitchell 🤪 on 7/12/23.
 */
@Publish
@Version("1.0.0")
public interface ITestService {

    String testMethodWithString(String value);

    /**
     * Replies after a delay. Lets a client test hold a request open on this instance long enough to take the
     * instance down underneath it, which is how the client's behaviour on losing its server is exercised.
     * The delay is capped so the call cannot be used to tie an instance up.
     *
     * @param value to echo back
     * @param delayMs to wait before replying, capped at {@value #MAX_DELAY_MS}
     * @return the reply, once the delay has elapsed
     */
    CompletableFuture<String> testMethodWithDelay(String value, long delayMs);

    long MAX_DELAY_MS = 60_000L;

    UUID getTestUUID();

}
