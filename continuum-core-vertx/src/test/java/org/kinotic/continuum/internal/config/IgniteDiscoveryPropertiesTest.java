package org.kinotic.continuum.internal.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetSocketAddress;
import java.util.List;

import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.Test;
import org.kinotic.continuum.api.config.IgniteClusterDiscoveryType;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * continuum.cluster.discoveryPort and continuum.cluster.joinTimeoutMs are documented properties, so they
 * must reach the TcpDiscoverySpi continuum hands Ignite. The discovery port in particular also shapes the
 * LOCAL ip finder's address list, so a node that binds Ignite's default port while its finder points at
 * the configured one can never find itself.
 */
class IgniteDiscoveryPropertiesTest {

    @Test
    void discoveryPortReachesTheDiscoverySpi() {
        TcpDiscoverySpi spi = discoverySpiFor(47600, 0L);

        assertEquals(47600, configuredPort(spi));
    }

    @Test
    void joinTimeoutReachesTheDiscoverySpi() {
        TcpDiscoverySpi spi = discoverySpiFor(47500, 5000L);

        assertEquals(5000L, spi.getJoinTimeout());
    }

    @Test
    void localIpFinderPointsAtThePortTheSpiBinds() {
        TcpDiscoverySpi spi = discoverySpiFor(47600, 0L);

        List<Integer> finderPorts = ((TcpDiscoveryVmIpFinder) spi.getIpFinder()).getRegisteredAddresses()
                                                                                .stream()
                                                                                .map(InetSocketAddress::getPort)
                                                                                .toList();
        assertEquals(List.of(configuredPort(spi)), finderPorts);
    }

    // TcpDiscoverySpi.getLocalPort() reports the port of the started node and 0 before start, so the
    // configured value is only visible through the field setLocalPort writes
    private static int configuredPort(TcpDiscoverySpi spi) {
        return (int) ReflectionTestUtils.getField(spi, "locPort");
    }

    private static TcpDiscoverySpi discoverySpiFor(int discoveryPort, long joinTimeoutMs) {
        ContinuumIgniteConfig config = new ContinuumIgniteConfig();
        ReflectionTestUtils.setField(config, "igniteClusterProperties",
                                     new DefaultIgniteClusterProperties()
                                             .setDiscoveryType(IgniteClusterDiscoveryType.LOCAL)
                                             .setDiscoveryPort(discoveryPort)
                                             .setJoinTimeoutMs(joinTimeoutMs));
        return (TcpDiscoverySpi) config.tcpDiscoverySpi();
    }
}
