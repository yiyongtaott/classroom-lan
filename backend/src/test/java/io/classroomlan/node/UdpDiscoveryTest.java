package io.classroomlan.node;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;

class UdpDiscoveryTest {

    @BeforeEach
    void setUp() {
        NodeState.resetInstance();
    }

    @Test
    void testStartsAsCandidate() throws Exception {
        UdpDiscovery udp = new UdpDiscovery(NodeState.getInstance());
        udp.start();
        try {
            assertEquals(NodeRole.CANDIDATE, NodeState.getInstance().getCurrentRole());
        } finally {
            udp.close();
        }
    }

    @Test
    void testLeaderWaitsForPorts() throws Exception {
        UdpDiscovery udp = new UdpDiscovery(NodeState.getInstance());
        udp.start();
        try {
            TimeUnit.MILLISECONDS.sleep(700);
            assertTrue(NodeState.getInstance().isCandidate() || 
                       NodeState.getInstance().isLeader());
        } finally {
            udp.close();
        }
    }

    @Test
    void testSetLeaderPortsTriggersBroadcast() throws Exception {
        NodeState state = NodeState.getInstance();
        UdpDiscovery udp = new UdpDiscovery(state);
        udp.start();
        try {
            TimeUnit.MILLISECONDS.sleep(700);
            state.setCurrentRole(NodeRole.LEADER);
            udp.setLeaderPorts(8085, 8086);
            assertTrue(udp.isHeartbeatRunning());
            assertEquals(8085, state.getHttpPort());
            assertEquals(8086, state.getWsPort());
        } finally {
            udp.close();
        }
    }

    @Test
    void testUdpPortConflictEntersSoloMode() throws Exception {
        try (java.net.DatagramSocket sock = new java.net.DatagramSocket(9999)) {
            NodeState state = NodeState.getInstance();
            UdpDiscovery udp = new UdpDiscovery(state);
            udp.start();
            try {
                assertTrue(state.isLeader());
                assertEquals(8080, state.getHttpPort());
                assertEquals(8081, state.getWsPort());
                assertTrue(udp.isHeartbeatRunning());
            } finally {
                udp.close();
            }
        }
    }
}
