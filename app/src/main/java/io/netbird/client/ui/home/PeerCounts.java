package io.netbird.client.ui.home;

/**
 * Immutable snapshot of the aggregate peer-status counters exposed by the
 * daemon. Phase 3.7i (#5989).
 */
public class PeerCounts {
    public final long configuredTotal;
    public final long serverOnline;
    public final long p2pConnected;
    public final long relayedConnected;
    public final long idleOnline;
    public final long serverOffline;

    public PeerCounts(long configuredTotal, long serverOnline, long p2pConnected,
                      long relayedConnected, long idleOnline, long serverOffline) {
        this.configuredTotal  = configuredTotal;
        this.serverOnline     = serverOnline;
        this.p2pConnected     = p2pConnected;
        this.relayedConnected = relayedConnected;
        this.idleOnline       = idleOnline;
        this.serverOffline    = serverOffline;
    }

    public static PeerCounts empty() {
        return new PeerCounts(0, 0, 0, 0, 0, 0);
    }
}
