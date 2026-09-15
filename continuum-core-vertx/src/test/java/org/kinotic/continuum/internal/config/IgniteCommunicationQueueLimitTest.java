package org.kinotic.continuum.internal.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.junit.jupiter.api.Test;
import org.kinotic.continuum.api.config.IgniteClusterDiscoveryType;
import org.kinotic.continuum.api.config.IgniteClusterProperties;
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

    @Test
    void implementersWrittenBeforeTheLimitExistedLeaveIgnitesDefault() {
        IgniteClusterProperties properties = new PropertiesWithoutTheLimit();

        assertNull(properties.getCommunicationMessageQueueLimit(), "the interface supplies null, not a value");
        assertEquals(TcpCommunicationSpi.DFLT_MSG_QUEUE_LIMIT, communicationSpiFor(properties).getMessageQueueLimit());
        assertEquals(47101, communicationSpiFor(properties).getLocalPort(), "the port still comes through");
    }

    private static TcpCommunicationSpi communicationSpiFor(int limit) {
        return communicationSpiFor(new DefaultIgniteClusterProperties()
                                           .setCommunicationPort(47101)
                                           .setCommunicationMessageQueueLimit(limit));
    }

    private static TcpCommunicationSpi communicationSpiFor(IgniteClusterProperties properties) {
        ContinuumIgniteConfig config = new ContinuumIgniteConfig();
        ReflectionTestUtils.setField(config, "igniteClusterProperties", properties);
        return config.tcpCommunicationSpi();
    }

    /**
     * An IgniteClusterProperties written against continuum 3.0, which had no queue limit: it implements
     * every accessor that existed then and nothing else, so it only compiles while the new accessor
     * has a default.
     */
    private static final class PropertiesWithoutTheLimit implements IgniteClusterProperties {
        @Override public IgniteClusterDiscoveryType getDiscoveryType() { return IgniteClusterDiscoveryType.LOCAL; }
        @Override public Long getJoinTimeoutMs() { return 0L; }
        @Override public String getLocalAddress() { return null; }
        @Override public String getSharedFsPath() { return null; }
        @Override public String getKubernetesNamespace() { return null; }
        @Override public String getKubernetesServiceName() { return null; }
        @Override public Boolean getKubernetesIncludeNotReadyAddresses() { return false; }
        @Override public String getKubernetesMasterUrl() { return null; }
        @Override public String getKubernetesAccountToken() { return null; }
        @Override public Integer getDiscoveryPort() { return 47500; }
        @Override public String getLocalAddresses() { return null; }
        @Override public Integer getCommunicationPort() { return 47101; }
    }
}
