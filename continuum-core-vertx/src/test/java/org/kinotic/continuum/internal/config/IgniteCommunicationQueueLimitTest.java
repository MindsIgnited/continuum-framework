package org.kinotic.continuum.internal.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Ignite's TcpCommunicationSpi ships with an unbounded message queue, which is what it recommends
 * against in production. The limit is a continuum.cluster property so a consumer sets it in yaml or
 * the environment and nothing else, and these pin that path end to end: the default, both spellings
 * consumers use, and that the value reaches the SPI continuum hands Ignite.
 */
class IgniteCommunicationQueueLimitTest {

    @Test
    void defaultsToIgnitesUnboundedQueue() {
        assertEquals(TcpCommunicationSpi.DFLT_MSG_QUEUE_LIMIT,
                     new DefaultIgniteClusterProperties().getCommunicationMessageQueueLimit());
    }

    @Test
    void bindsFromYaml() {
        DefaultIgniteClusterProperties properties = new Binder(new MapConfigurationPropertySource(
                Map.of("continuum.cluster.communicationMessageQueueLimit", "1024")))
                .bind("continuum.cluster", Bindable.ofInstance(new DefaultIgniteClusterProperties()))
                .get();

        assertEquals(1024, properties.getCommunicationMessageQueueLimit());
    }

    @Test
    void bindsFromTheEnvironmentSpellingTheOtherClusterPropertiesUse() {
        SystemEnvironmentPropertySource environment = new SystemEnvironmentPropertySource(
                "env", Map.of("CONTINUUM_CLUSTER_COMMUNICATION_MESSAGE_QUEUE_LIMIT", "2048"));
        DefaultIgniteClusterProperties properties = new Binder(ConfigurationPropertySources.from(environment))
                .bind("continuum.cluster", Bindable.ofInstance(new DefaultIgniteClusterProperties()))
                .get();

        assertEquals(2048, properties.getCommunicationMessageQueueLimit());
    }

    @Test
    void limitReachesTheCommunicationSpi() {
        assertEquals(1024, communicationSpiFor(1024).getMessageQueueLimit());
        assertEquals(TcpCommunicationSpi.DFLT_MSG_QUEUE_LIMIT, communicationSpiFor(0).getMessageQueueLimit(), "0 leaves Ignite's unbounded default");
        assertEquals(47101, communicationSpiFor(1024).getLocalPort(), "the port still comes through");
    }

    private static TcpCommunicationSpi communicationSpiFor(int limit) {
        DefaultIgniteClusterProperties properties = new DefaultIgniteClusterProperties()
                .setCommunicationPort(47101)
                .setCommunicationMessageQueueLimit(limit);
        ContinuumIgniteConfig config = new ContinuumIgniteConfig();
        ReflectionTestUtils.setField(config, "igniteClusterProperties", properties);
        return config.tcpCommunicationSpi();
    }
}
