package io.netbird.client.ui.home;

public class Peer {
   private final Status status;
   private final String ip;
   private final String fqdn;
   // Phase 3.7i (#5989): per-peer enrichment for list + detail dialog.
   private final boolean relayed;
   private final boolean serverOnline;
   private final String effectiveMode;
   private final String configuredMode;
   private final String localEndpoint;
   private final String remoteEndpoint;
   private final String relayServer;
   private final String lastHandshake;
   private final String lastSeenAtServer;
   private final long latencyMs;
   private final String groups;
   private final long rxBytes;
   private final long txBytes;
   // Phase 3.7i hybrid display: daemon-derived UI label.
   // Values: "", "P2P", "Relayed", "Relayed (negotiating P2P)".
   private final String connectionTypeExtended;
   // Phase 3.7i lifecycle hardening: ICE-backoff snapshot.
   private final int iceBackoffFailures;
   private final String iceBackoffNextRetry; // RFC3339; "" if zero
   private final boolean iceBackoffSuspended;

   public Peer(Status status, String ip, String fqdn,
               boolean relayed, boolean serverOnline,
               String effectiveMode, String configuredMode,
               String localEndpoint, String remoteEndpoint, String relayServer,
               String lastHandshake, String lastSeenAtServer,
               long latencyMs, String groups, long rxBytes, long txBytes,
               String connectionTypeExtended,
               int iceBackoffFailures, String iceBackoffNextRetry,
               boolean iceBackoffSuspended) {
      this.status = status;
      this.ip = ip;
      this.fqdn = fqdn;
      this.relayed = relayed;
      this.serverOnline = serverOnline;
      this.effectiveMode = effectiveMode == null ? "" : effectiveMode;
      this.configuredMode = configuredMode == null ? "" : configuredMode;
      this.localEndpoint = localEndpoint == null ? "" : localEndpoint;
      this.remoteEndpoint = remoteEndpoint == null ? "" : remoteEndpoint;
      this.relayServer = relayServer == null ? "" : relayServer;
      this.lastHandshake = lastHandshake == null ? "" : lastHandshake;
      this.lastSeenAtServer = lastSeenAtServer == null ? "" : lastSeenAtServer;
      this.latencyMs = latencyMs;
      this.groups = groups == null ? "" : groups;
      this.rxBytes = rxBytes;
      this.txBytes = txBytes;
      this.connectionTypeExtended = connectionTypeExtended == null ? "" : connectionTypeExtended;
      this.iceBackoffFailures = iceBackoffFailures;
      this.iceBackoffNextRetry = iceBackoffNextRetry == null ? "" : iceBackoffNextRetry;
      this.iceBackoffSuspended = iceBackoffSuspended;
   }

   public Status getStatus() { return status; }
   public String getIp() { return ip; }
   public String getFqdn() { return fqdn; }
   public boolean isRelayed() { return relayed; }
   public boolean isServerOnline() { return serverOnline; }
   public String getEffectiveMode() { return effectiveMode; }
   public String getConfiguredMode() { return configuredMode; }
   public String getLocalEndpoint() { return localEndpoint; }
   public String getRemoteEndpoint() { return remoteEndpoint; }
   public String getRelayServer() { return relayServer; }
   public String getLastHandshake() { return lastHandshake; }
   public String getLastSeenAtServer() { return lastSeenAtServer; }
   public long getLatencyMs() { return latencyMs; }
   public String getGroups() { return groups; }
   public long getRxBytes() { return rxBytes; }
   public long getTxBytes() { return txBytes; }
   public String getConnectionTypeExtended() { return connectionTypeExtended; }
   public int getIceBackoffFailures() { return iceBackoffFailures; }
   public String getIceBackoffNextRetry() { return iceBackoffNextRetry; }
   public boolean isIceBackoffSuspended() { return iceBackoffSuspended; }

   /** Short connection-type label for the row.
    *  Prefers the daemon-derived ConnectionTypeExtended so the brief
    *  "Relayed (negotiating P2P)" wakeup window is visible. Falls back
    *  to the legacy (status, relayed, serverOnline) tuple when daemon
    *  pre-dates Phase 3.7i. */
   public String getConnTypeLabel() {
      if (!serverOnline) return "Offline";
      if (!connectionTypeExtended.isEmpty()) return connectionTypeExtended;
      if (status == Status.CONNECTED) return relayed ? "Relayed" : "P2P";
      return "Idle";
   }
}
