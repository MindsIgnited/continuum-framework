/*
 *
 * Copyright 2008-2021 Kinotic and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kinotic.continuum.internal.core.api.event;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.kinotic.continuum.core.api.event.Event;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.Disposable;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the {@code localOnly} delivery predicate of {@link DefaultEventBusService}: when this node hosts a
 * local handler for a service address, sends to that address should prefer local delivery, otherwise they should
 * fall back to normal cluster routing.
 *
 * Uses a real (non clustered) {@link Vertx} so the consumer registration / unregistration lifecycle that drives
 * the predicate is exercised end to end rather than mocked.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DefaultEventBusServiceLocalDeliveryTest {

    private Vertx vertx;
    private DefaultEventBusService eventBusService;

    @BeforeAll
    public void startVertx() {
        vertx = Vertx.vertx();
    }

    @AfterAll
    public void stopVertx() {
        if (vertx != null) {
            vertx.close();
        }
    }

    @BeforeEach
    public void setUp() {
        // a fresh service per test so the tracked local listener state starts empty
        eventBusService = new DefaultEventBusService();
        ReflectionTestUtils.setField(eventBusService, "vertx", vertx);
        eventBusService.init();
    }

    @AfterEach
    public void tearDown() {
        eventBusService = null;
    }

    private static Event<byte[]> event(String cri) {
        return Event.create(cri, "data".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void prefersLocalDeliveryForLocallyHostedServiceAddress() {
        String baseResource = "srv://org.kinotic.continuum.tests.LocalDeliveryService";
        Disposable subscription = eventBusService.listen(baseResource).subscribe();
        try {
            // a send targets the base resource of the request cri (scheme + optional scope + resource name)
            Event<byte[]> request = event(baseResource + "/someMethod");
            assertThat(request.cri().baseResource())
                    .as("the send address must equal the listened base resource")
                    .isEqualTo(baseResource);

            assertThat(eventBusService.shouldPreferLocalDelivery(request, request.cri().baseResource()))
                    .as("a service address hosted by a local handler should prefer local delivery")
                    .isTrue();
        } finally {
            subscription.dispose();
        }
    }

    @Test
    public void doesNotPreferLocalDeliveryForAddressNotHostedLocally() {
        String hosted = "srv://org.kinotic.continuum.tests.HostedService";
        Disposable subscription = eventBusService.listen(hosted).subscribe();
        try {
            Event<byte[]> request = event("srv://org.kinotic.continuum.tests.OtherService/someMethod");
            assertThat(eventBusService.shouldPreferLocalDelivery(request, request.cri().baseResource()))
                    .as("a service address not hosted locally should fall back to cluster routing")
                    .isFalse();
        } finally {
            subscription.dispose();
        }
    }

    @Test
    public void doesNotPreferLocalDeliveryForNonServiceScheme() {
        // This node hosts a local handler at this exact address, but the non service scheme must still
        // suppress the optimization so reply / stream / other sends are left untouched.
        String streamAddress = "stream://org.kinotic.continuum.tests.SomeStream";
        Disposable subscription = eventBusService.listen(streamAddress).subscribe();
        try {
            Event<byte[]> request = event(streamAddress + "/someMethod");
            assertThat(eventBusService.shouldPreferLocalDelivery(request, request.cri().baseResource()))
                    .as("a non service scheme should not prefer local delivery even when hosted locally")
                    .isFalse();
        } finally {
            subscription.dispose();
        }
    }

    @Test
    public void clearsAfterConsumerUnregistered() {
        String baseResource = "srv://org.kinotic.continuum.tests.EphemeralService";
        Disposable subscription = eventBusService.listen(baseResource).subscribe();
        Event<byte[]> request = event(baseResource + "/someMethod");

        assertThat(eventBusService.shouldPreferLocalDelivery(request, request.cri().baseResource()))
                .as("local delivery should be preferred while the consumer is registered")
                .isTrue();

        // unregistering the only local handler must clear the preference so a later send falls back to the cluster
        subscription.dispose();

        assertThat(eventBusService.shouldPreferLocalDelivery(request, request.cri().baseResource()))
                .as("local delivery preference should clear once the consumer is unregistered")
                .isFalse();
    }
}
