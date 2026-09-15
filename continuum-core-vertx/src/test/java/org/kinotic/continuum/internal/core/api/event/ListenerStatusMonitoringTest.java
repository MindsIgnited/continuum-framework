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

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.kinotic.continuum.core.api.event.EventBusService;
import org.kinotic.continuum.core.api.event.ListenerStatus;
import org.kinotic.continuum.internal.ContinuumIgniteClusterManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.Disposable;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies listener status monitoring, which is fed by the registration updates the cluster manager already
 * receives for message routing rather than by a per monitor continuous query over vertx-ignite's internal
 * subscription cache.
 *
 * Created by Navíd Mitchell 🤪 on 7/25/26
 */
@SpringBootTest
@ActiveProfiles({"test"})
public class ListenerStatusMonitoringTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    private EventBusService eventBusService;

    @Autowired
    private ContinuumIgniteClusterManager clusterManager;

    private void awaitListening(String address, boolean listening) {
        Awaitility.await()
                  .atMost(TIMEOUT)
                  .until(() -> Boolean.TRUE.equals(eventBusService.isAnybodyListening(address).block()) == listening);
    }

    /**
     * A monitor must only see registration changes for the address it monitors. Previously every monitor was
     * notified of every subscription change anywhere in the cluster, so unrelated churn cancelled active
     * streaming results.
     */
    @Test
    public void monitorIsNotAffectedByChangesOnUnrelatedAddresses() {
        String monitored = "srv://org.kinotic.continuum.tests.MonitoredService";
        String unrelated = "srv://org.kinotic.continuum.tests.UnrelatedService";

        List<ListenerStatus> statuses = new CopyOnWriteArrayList<>();
        Disposable monitoredListener = eventBusService.listen(monitored).subscribe();
        awaitListening(monitored, true);

        Disposable monitor = eventBusService.monitorListenerStatus(monitored).subscribe(statuses::add);
        try {
            Awaitility.await().atMost(TIMEOUT).until(() -> statuses.contains(ListenerStatus.ACTIVE));

            // churn a consumer on an unrelated address, the monitored address is untouched throughout
            Disposable unrelatedListener = eventBusService.listen(unrelated).subscribe();
            awaitListening(unrelated, true);
            unrelatedListener.dispose();
            awaitListening(unrelated, false);

            assertThat(statuses)
                    .as("a monitor must not receive INACTIVE because an unrelated address lost its last consumer")
                    .doesNotContain(ListenerStatus.INACTIVE);
        } finally {
            monitor.dispose();
            monitoredListener.dispose();
        }
    }

    /**
     * The monitor emits the current status on subscribe and the resulting status of every registration change
     * after that, and it leaves no state behind once cancelled.
     */
    @Test
    public void emitsCurrentStatusThenTracksRegistrationChanges() {
        String address = "srv://org.kinotic.continuum.tests.LifecycleService";

        List<ListenerStatus> statuses = new CopyOnWriteArrayList<>();
        Disposable monitor = eventBusService.monitorListenerStatus(address).subscribe(statuses::add);
        Disposable listener = null;
        try {
            // nobody is listening yet, so the status on subscribe is INACTIVE
            Awaitility.await().atMost(TIMEOUT).until(() -> !statuses.isEmpty());
            assertThat(statuses.get(0))
                    .as("the first emission should be the current status of the address")
                    .isEqualTo(ListenerStatus.INACTIVE);

            // a consumer registers
            listener = eventBusService.listen(address).subscribe();
            Awaitility.await().atMost(TIMEOUT).until(() -> statuses.contains(ListenerStatus.ACTIVE));

            // the only consumer goes away
            listener.dispose();
            listener = null;
            Awaitility.await()
                      .atMost(TIMEOUT)
                      .until(() -> statuses.get(statuses.size() - 1) == ListenerStatus.INACTIVE);
        } finally {
            if(listener != null){
                listener.dispose();
            }
            monitor.dispose();
        }

        // cancelling the monitor must not leave the address behind
        @SuppressWarnings("unchecked")
        Map<String, ?> monitors = (Map<String, ?>) ReflectionTestUtils.getField(clusterManager, "monitors");
        assertThat(monitors)
                .as("the monitor should be removed once its last subscriber cancels")
                .doesNotContainKey(address);

        // and monitoring the same address again must still work
        List<ListenerStatus> resubscribed = new CopyOnWriteArrayList<>();
        Disposable monitorAgain = eventBusService.monitorListenerStatus(address).subscribe(resubscribed::add);
        try {
            Awaitility.await().atMost(TIMEOUT).until(() -> !resubscribed.isEmpty());
            assertThat(resubscribed.get(0))
                    .as("re-subscribing should emit the current status of the address")
                    .isEqualTo(ListenerStatus.INACTIVE);
        } finally {
            monitorAgain.dispose();
        }
    }
}
