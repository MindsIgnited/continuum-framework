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

import io.vertx.core.Promise;
import io.vertx.core.spi.cluster.RegistrationInfo;
import io.vertx.spi.cluster.ignite.impl.IgniteRegistrationInfo;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.calcite.CalciteQueryEngineConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.configuration.SqlConfiguration;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.sharedfs.TcpDiscoverySharedFsIpFinder;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.kinotic.continuum.api.config.IgniteClusterProperties;
import org.kinotic.continuum.core.api.event.EventBusService;
import org.kinotic.continuum.core.api.event.ListenerStatus;
import org.kinotic.continuum.internal.ContinuumIgniteClusterManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.Disposable;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that a registration counts only while its node is a member of the cluster. vertx-ignite leaves a departed
 * node's registrations in its subscription cache until the node cleaning up after it removes them, and the update
 * that removal triggers can be read before the removal reaches this node, so these tests leave such a registration
 * in the cache, as a node that has not cleaned up yet would, and assert what routing and the monitors see.
 */
@SpringBootTest
@ActiveProfiles({"test"})
public class DepartedNodeRegistrationsTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final String VERTX_SUBSCRIPTION_CACHE = "__vertx.subs";

    @Autowired
    private EventBusService eventBusService;

    @Autowired
    private ContinuumIgniteClusterManager clusterManager;

    @Autowired
    private Ignite ignite;

    @Autowired
    private IgniteClusterProperties igniteClusterProperties;

    /**
     * A registration left behind by a node that is no longer a member is not routed to and does not keep the
     * address active, so the address goes inactive when its last live consumer goes away.
     */
    @Test
    public void registrationOfANonMemberIsIgnored() throws Exception {
        String address = "srv://org.kinotic.continuum.tests.LeftBehindService";
        IgniteCache<String, Set<IgniteRegistrationInfo>> subscriptions = ignite.cache(VERTX_SUBSCRIPTION_CACHE);
        subscriptions.put(address, registrationSet(address, UUID.randomUUID().toString()));

        List<ListenerStatus> statuses = new CopyOnWriteArrayList<>();
        Disposable listener = eventBusService.listen(address).subscribe();
        Disposable monitor = null;
        try {
            Awaitility.await().atMost(TIMEOUT).until(() -> registrationsOf(address).size() == 1);
            monitor = eventBusService.monitorListenerStatus(address).subscribe(statuses::add);
            Awaitility.await().atMost(TIMEOUT).until(() -> statuses.contains(ListenerStatus.ACTIVE));

            // the live consumer goes away, leaving only the registration of the node that is not a member
            listener.dispose();
            listener = null;

            Awaitility.await()
                      .atMost(TIMEOUT)
                      .until(() -> statuses.get(statuses.size() - 1) == ListenerStatus.INACTIVE);
            assertThat(registrationsOf(address)).isEmpty();
            assertThat(eventBusService.isAnybodyListening(address).block()).isFalse();
            assertThat(subscriptions.get(address))
                    .as("the registration of the node that is not a member is still in the cache")
                    .hasSize(1);
        } finally {
            if(listener != null){
                listener.dispose();
            }
            if(monitor != null){
                monitor.dispose();
            }
            subscriptions.remove(address);
        }
    }

    /**
     * When a node leaves, an address it was registered on is announced without it, so a monitor sees the address go
     * inactive even though nothing has removed the node's registration from the cache.
     */
    @Test
    public void departureIsAnnouncedBeforeTheRegistrationIsRemoved() throws Exception {
        String address = "srv://org.kinotic.continuum.tests.DepartingService";
        IgniteCache<String, Set<IgniteRegistrationInfo>> subscriptions = ignite.cache(VERTX_SUBSCRIPTION_CACHE);

        List<ListenerStatus> statuses = new CopyOnWriteArrayList<>();
        Disposable monitor = null;
        Ignite departing = Ignition.start(departingNodeConfiguration());
        try {
            String departingNodeId = departing.cluster().localNode().id().toString();
            Awaitility.await().atMost(TIMEOUT).until(() -> clusterManager.getNodes().contains(departingNodeId));

            // a plain Ignite node holds no vertx node info, so no node removes its registrations when it leaves
            subscriptions.put(address, registrationSet(address, departingNodeId));
            monitor = eventBusService.monitorListenerStatus(address).subscribe(statuses::add);
            Awaitility.await().atMost(TIMEOUT).until(() -> statuses.contains(ListenerStatus.ACTIVE));
            assertThat(eventBusService.isAnybodyListening(address).block()).isTrue();

            departing.close();

            Awaitility.await()
                      .atMost(TIMEOUT)
                      .until(() -> statuses.get(statuses.size() - 1) == ListenerStatus.INACTIVE);
            assertThat(eventBusService.isAnybodyListening(address).block()).isFalse();
            assertThat(subscriptions.get(address))
                    .as("the departed node's registration is still in the cache")
                    .hasSize(1);
        } finally {
            departing.close();
            if(monitor != null){
                monitor.dispose();
            }
            subscriptions.remove(address);
        }
    }

    private List<RegistrationInfo> registrationsOf(String address) throws Exception {
        Promise<List<RegistrationInfo>> promise = Promise.promise();
        clusterManager.getRegistrations(address, promise);
        return promise.future().toCompletionStage().toCompletableFuture().get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    }

    private static Set<IgniteRegistrationInfo> registrationSet(String address, String nodeId) {
        Set<IgniteRegistrationInfo> registrations = new HashSet<>();
        registrations.add(new IgniteRegistrationInfo(address, new RegistrationInfo(nodeId, 0, false)));
        return registrations;
    }

    // A client node that joins the test cluster through the same shared filesystem discovery
    private IgniteConfiguration departingNodeConfiguration() {
        TcpDiscoverySharedFsIpFinder ipFinder = new TcpDiscoverySharedFsIpFinder();
        ipFinder.setPath(igniteClusterProperties.getSharedFsPath());
        return new IgniteConfiguration()
                .setIgniteInstanceName("departing-node-" + UUID.randomUUID())
                .setClientMode(true)
                .setGridLogger(new Slf4jLogger())
                .setMetricsLogFrequency(0)
                .setDiscoverySpi(new TcpDiscoverySpi().setIpFinder(ipFinder))
                .setSqlConfiguration(new SqlConfiguration().setQueryEnginesConfiguration(
                        new CalciteQueryEngineConfiguration().setDefault(true)));
    }
}
