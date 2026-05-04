package io.netbird.client.ui.home;

import static io.netbird.client.ui.home.PeersAdapter.FilterStatus.ALL;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import io.netbird.client.R;
import io.netbird.client.databinding.ListItemPeerBinding;

public class PeersAdapter extends RecyclerView.Adapter<PeersAdapter.PeerViewHolder> {


    public enum FilterStatus {
        ALL,
        IDLE,
        CONNECTING,
        CONNECTED,
    }

    private final List<Peer> peerList;
    private final List<Peer> filteredPeerList;

    private FilterStatus filterStatus = ALL;
    private String filterQueryString = "";

    public PeersAdapter(List<Peer> peerList) {
        this.peerList = peerList;
        filteredPeerList = new ArrayList<>(peerList);
        sortPeers();
    }

    @NonNull
    @Override
    public PeerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Use ViewBinding to inflate the layout
        io.netbird.client.databinding.ListItemPeerBinding binding = ListItemPeerBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new PeerViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PeerViewHolder holder, int position) {
        Peer peer = filteredPeerList.get(position);
        // Forward the live (unfiltered) list so the detail dialog's
        // Refresh button can look up the latest snapshot by FQDN even
        // if the user changes the filter while the dialog is open.
        holder.adapterPeerList = peerList;
        holder.bind(peer);
    }

    @Override
    public int getItemCount() {
        return filteredPeerList.size();
    }

    public void filterByStatus(FilterStatus status) {
        filterStatus = status;
        applyFilters();
    }


    public void filterBySearchQuery(String query) {
        filterQueryString = query;
        applyFilters();
    }

    private void applyFilters() {
        filteredPeerList.clear();
        doFilterByStatus();
        doFilterBySearchQuery();
        sortPeers();
        notifyDataSetChanged();
    }

    private void doFilterByStatus() {
        Status targetStatus;
        switch (filterStatus) {
            case IDLE:
                targetStatus = Status.IDLE;
                break;
            case CONNECTING:
                targetStatus = Status.CONNECTING;
                break;
            case CONNECTED:
                targetStatus = Status.CONNECTED;
                break;
            default:
                filteredPeerList.addAll(peerList);
                return;
        }

        for (Peer peer : peerList) {
            if (peer.getStatus() == targetStatus) {
                filteredPeerList.add(peer);
            }
        }
    }

    private void doFilterBySearchQuery() {
        if (filterQueryString.isEmpty()) {
            return;
        }

        ArrayList<Peer> temporaryList = new ArrayList<>(filteredPeerList);
        for (Peer peer : temporaryList) {
            if (!peer.getFqdn().toLowerCase().contains(filterQueryString.toLowerCase())){
                filteredPeerList.remove(peer);
            }
        }
    }

    private static void showPopup(View view, Peer peer) {
        PopupMenu popup = new PopupMenu(view.getContext(), view);
        popup.getMenuInflater().inflate(R.menu.peer_clipboard_menu, popup.getMenu());

        popup.setOnMenuItemClickListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == R.id.copy_fqdn) {
                copyToClipboard(view.getContext(), "FQDN", peer.getFqdn());
                return true;
            } else if (id == R.id.copy_ip) {
                copyToClipboard(view.getContext(), "IP Address", peer.getIp());
                return true;
            }
            return false;
        });

        popup.show();
    }

    private static void copyToClipboard(Context context, String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
    }

    private void sortPeers() {
        filteredPeerList.sort((p1, p2) -> {
            int statusCompare = Boolean.compare(
                    p2.getStatus() == Status.CONNECTED,
                    p1.getStatus() == Status.CONNECTED
            );
            if (statusCompare != 0) {
                return statusCompare;
            }
            // Then sort alphabetically by fqdn
            return p1.getFqdn().compareToIgnoreCase(p2.getFqdn());
        });
    }

    public static class PeerViewHolder extends RecyclerView.ViewHolder {
        ListItemPeerBinding binding;
        // Reference to the adapter's live peer list so the detail
        // dialog can look up the latest snapshot for Refresh. Set by
        // the adapter on each onBindViewHolder.
        List<Peer> adapterPeerList;

        public PeerViewHolder(ListItemPeerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Peer peer) {
            // Phase 3.7i: status text shows the user-facing connection-type
            // label (P2P / Relayed / Idle / Offline) with the effective
            // mode in parens when known and meaningful.
            String statusText = peer.getConnTypeLabel();
            String mode = peer.getEffectiveMode();
            if (mode != null && !mode.isEmpty()) {
                statusText = statusText + " · " + mode;
            }
            binding.status.setText(statusText);
            binding.ip.setText(peer.getIp());
            binding.fqdn.setText(peer.getFqdn());

            // Phase 3.7i color coding:
            //   P2P (incl. negotiating)  -> green / dark-green
            //   Relayed permanent        -> dark green
            //   Idle (lazy waiting)      -> grey
            //   Offline (server unreach) -> red
            int swatchRes;
            if (!peer.isServerOnline()) {
                swatchRes = R.drawable.peer_status_offline;
            } else {
                String label = peer.getConnTypeLabel();
                if ("P2P".equals(label)) {
                    swatchRes = R.drawable.peer_status_p2p;
                } else if ("Relayed".equals(label) || "Relayed (negotiating P2P)".equals(label)) {
                    swatchRes = R.drawable.peer_status_relayed;
                } else if (peer.getStatus() == Status.CONNECTED) {
                    // Daemon pre-3.7i fallback: Connected without extended label.
                    swatchRes = peer.isRelayed() ? R.drawable.peer_status_relayed
                            : R.drawable.peer_status_p2p;
                } else {
                    swatchRes = R.drawable.peer_status_idle;
                }
            }
            binding.verticalLine.setBackgroundResource(swatchRes);

            // Phase 3.7i: tap opens detail dialog with all available info.
            // PeerViewHolder is static; receive the adapter's live peer
            // list via setLiveList and forward to the lookup-by-FQDN
            // overload of showDetailDialog (Refresh button).
            final List<Peer> liveList = this.adapterPeerList;
            binding.getRoot().setOnClickListener(v -> showDetailDialog(v.getContext(), liveList, peer.getFqdn()));

            // Long press still opens the existing copy-IP/copy-FQDN popup.
            binding.getRoot().setOnLongClickListener(v -> {
                showPopup(v, peer);
                return true;
            });
        }
    }

    /**
     * Phase 3.7i (#5989): per-peer detail dialog opened on row tap.
     * Custom layout: section headers + label/value rows; all values are
     * selectable so users can long-press to copy any field.
     *
     * Note: AlertDialog uses Material's light dialog background even in
     * a dark-mode app, so we hardcode dark text colors instead of using
     * the theme-aware nb_txt (which is white in dark mode → invisible).
     */
    // Look up the latest snapshot for this peer in the supplied
    // adapter-backing list. The list is a live reference held by the
    // ViewModel; the engine pushes updates into it via the status
    // change callback, so by the time the user re-renders the dialog
    // it usually has fresher numbers (handshake, latency, transfer
    // bytes, etc). Falls back to a "no longer present" placeholder if
    // the peer was removed from the mesh while the dialog was open.
    private static Peer lookupPeer(List<Peer> liveList, String fqdn) {
        if (liveList == null) return null;
        for (Peer p : liveList) {
            if (p.getFqdn().equals(fqdn)) {
                return p;
            }
        }
        return null;
    }

    private static void showDetailDialog(Context ctx, List<Peer> liveList, String fqdn) {
        Peer peer = lookupPeer(liveList, fqdn);
        if (peer == null) {
            new AlertDialog.Builder(ctx)
                    .setTitle(fqdn)
                    .setMessage("Peer no longer in the mesh.")
                    .setPositiveButton("Close", null)
                    .show();
            return;
        }
        showDetailDialog(ctx, liveList, peer);
    }

    private static void showDetailDialog(Context ctx, List<Peer> liveList, Peer peer) {
        // Read Show-Full preference here so the dialog reflects the
        // user's setting from AdvancedFragment.
        boolean full = false;
        try {
            full = new io.netbird.client.tool.Preferences(ctx).getPeerDetailLevel() == 1;
        } catch (Throwable ignored) {}

        ScrollView scroll = new ScrollView(ctx);
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(ctx, 16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        // Identity
        addSectionHeader(ctx, root, "Identity");
        addRow(ctx, root, "IP", peer.getIp());
        addRow(ctx, root, "FQDN", peer.getFqdn());
        if (!peer.getGroups().isEmpty()) {
            addRow(ctx, root, "Groups", peer.getGroups());
        }

        // Connection
        addSectionHeader(ctx, root, "Connection");
        addRow(ctx, root, "Type", peer.getConnTypeLabel());
        if (peer.getStatus() == Status.CONNECTED) {
            if (peer.isRelayed()) {
                addRow(ctx, root, "Relay server", peer.getRelayServer());
            } else {
                addRow(ctx, root, "Local endpoint", peer.getLocalEndpoint());
                addRow(ctx, root, "Remote endpoint", peer.getRemoteEndpoint());
            }
        }
        addRow(ctx, root, "Last handshake", peer.getLastHandshake());
        long latency = peer.getLatencyMs();
        addRow(ctx, root, "Latency", latency > 0 ? latency + " ms" : "-");

        // Mode
        addSectionHeader(ctx, root, "Mode");
        addRow(ctx, root, "Effective", peer.getEffectiveMode());
        if (!peer.getEffectiveMode().equals(peer.getConfiguredMode())
                && !peer.getConfiguredMode().isEmpty()) {
            addRow(ctx, root, "Configured", peer.getConfiguredMode());
        }

        // Server
        addSectionHeader(ctx, root, "Server");
        addRow(ctx, root, "Last seen", peer.getLastSeenAtServer());

        // Transfer (basic in Standard, exact bytes in Full)
        addSectionHeader(ctx, root, "Transfer");
        if (full) {
            addRow(ctx, root, "Rx", peer.getRxBytes() + " B (" + formatBytes(peer.getRxBytes()) + ")");
            addRow(ctx, root, "Tx", peer.getTxBytes() + " B (" + formatBytes(peer.getTxBytes()) + ")");
        } else {
            addRow(ctx, root, "Rx", formatBytes(peer.getRxBytes()));
            addRow(ctx, root, "Tx", formatBytes(peer.getTxBytes()));
        }

        // Full-only debug section
        if (full) {
            addSectionHeader(ctx, root, "Debug (full)");
            addRow(ctx, root, "ServerOnline", String.valueOf(peer.isServerOnline()));
            addRow(ctx, root, "Relayed flag", String.valueOf(peer.isRelayed()));
            addRow(ctx, root, "Local endpoint", peer.getLocalEndpoint());
            addRow(ctx, root, "Remote endpoint", peer.getRemoteEndpoint());
            addRow(ctx, root, "Relay server", peer.getRelayServer());
            // ICE-backoff: explains why a peer stays Relayed.
            // Codex finding 4: the daemon snapshot only refreshes on
            // ICE state-change events, so isIceBackoffSuspended() can
            // stay true long after the cool-down has expired. Mirror
            // the CLI's wall-clock check (status.go:797): only show
            // "suspended" while nextRetry is still in the future,
            // otherwise show the next-retry timestamp, otherwise hide.
            if (peer.getIceBackoffFailures() > 0) {
                addRow(ctx, root, "ICE backoff fails", String.valueOf(peer.getIceBackoffFailures()));
            }
            String nrStr = peer.getIceBackoffNextRetry();
            if (nrStr != null && !nrStr.isEmpty()) {
                long nextMs = parseRfc3339Ms(nrStr);
                long nowMs = System.currentTimeMillis();
                long remaining = nextMs - nowMs;
                if (peer.isIceBackoffSuspended() && remaining > 0) {
                    addRow(ctx, root, "ICE backoff",
                        "suspended for " + (remaining / 1000) + "s (retry at " + nrStr + ")");
                } else if (remaining > 0) {
                    addRow(ctx, root, "ICE next retry",
                        nrStr + " (in " + (remaining / 1000) + "s)");
                }
                // remaining <= 0: cool-down expired by wall-clock — hide.
            }
        }

        // Snapshot-age footer: lets the user judge whether Refresh would
        // help. Shows wall-clock seconds since the dialog was built --
        // the underlying Peer object is from the adapter's last LiveData
        // emission, so its data is at least this stale.
        addSectionHeader(ctx, root, "Snapshot");
        long openedAtMs = System.currentTimeMillis();
        addRow(ctx, root, "Built at", new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                .format(new java.util.Date(openedAtMs)));

        // Refresh button: re-renders the dialog with the latest snapshot
        // for this peer-key from the adapter's current backing list. The
        // adapter is updated by the engine's status-change callback path,
        // so by the time the user taps Refresh the list usually already
        // has fresher data. Closes the current dialog, opens a fresh one.
        new AlertDialog.Builder(ctx)
                .setTitle(peer.getFqdn())
                .setView(scroll)
                .setPositiveButton("Close", null)
                .setNeutralButton("Refresh", (d, which) -> showDetailDialog(ctx, liveList, peer.getFqdn()))
                .show();
    }

    // Theme-aware text colours. Earlier code hard-coded
    // 0xFF1A1A1A for the value text under the (incorrect) assumption
    // that AlertDialog content always renders on a light surface; on
    // Android 13/14+ in dark mode the dialog background is dark grey,
    // making nearly-black text invisible. Resolve from the current
    // theme via android.R.attr.textColorPrimary / textColorSecondary
    // so the dialog reads correctly under both light and dark themes.
    private static int resolveThemeColor(Context ctx, int attr, int fallback) {
        TypedValue tv = new TypedValue();
        if (!ctx.getTheme().resolveAttribute(attr, tv, true)) {
            return fallback;
        }
        if (tv.resourceId != 0) {
            return ContextCompat.getColor(ctx, tv.resourceId);
        }
        return tv.data;
    }

    private static int dialogValueColor(Context ctx) {
        return resolveThemeColor(ctx, android.R.attr.textColorPrimary, 0xFF1A1A1A);
    }

    private static int dialogLabelColor(Context ctx) {
        return resolveThemeColor(ctx, android.R.attr.textColorSecondary, 0xFF6E6E6E);
    }

    private static void addSectionHeader(Context ctx, LinearLayout parent, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text.toUpperCase());
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setTextColor(dialogLabelColor(ctx));
        // Phase 3.7i (#5989): make section headers + labels selectable too
        // so the user can select an entire row (label + value) and copy it.
        tv.setTextIsSelectable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(ctx, 12);
        lp.bottomMargin = dp(ctx, 4);
        tv.setLayoutParams(lp);
        parent.addView(tv);
    }

    private static void addRow(Context ctx, LinearLayout parent, String label, String value) {
        if (value == null || value.isEmpty()) value = "-";

        // Label (small, gray)
        TextView lbl = new TextView(ctx);
        lbl.setText(label);
        lbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        lbl.setTextColor(dialogLabelColor(ctx));
        lbl.setTextIsSelectable(true);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        llp.topMargin = dp(ctx, 6);
        lbl.setLayoutParams(llp);

        // Value (regular, selectable for copy)
        TextView val = new TextView(ctx);
        val.setText(value);
        val.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        val.setTextColor(dialogValueColor(ctx));
        val.setTextIsSelectable(true);
        val.setGravity(Gravity.START);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        val.setLayoutParams(vlp);

        parent.addView(lbl);
        parent.addView(val);
    }

    private static int dp(Context ctx, int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                ctx.getResources().getDisplayMetrics());
    }

    /** Human-readable byte formatting. */
    private static String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        final long kib = 1024;
        if (bytes < kib) return bytes + " B";
        if (bytes < kib * kib) return String.format("%.1f KB", bytes / (double) kib);
        if (bytes < kib * kib * kib) return String.format("%.1f MB", bytes / (double) (kib * kib));
        return String.format("%.2f GB", bytes / (double) (kib * kib * kib));
    }

    private static String orDash(String s) { return (s == null || s.isEmpty()) ? "-" : s; }

    /**
     * Parses an RFC3339 timestamp ("2026-05-03T22:30:00Z" or with offset)
     * into epoch-ms. Returns 0 on parse failure (caller treats 0 as
     * "no timestamp" / never-due). Used for the wall-clock check on
     * IceBackoffNextRetry — see Codex finding 4 / status.go:797.
     */
    private static long parseRfc3339Ms(String s) {
        if (s == null || s.isEmpty()) return 0L;
        try {
            return java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli();
        } catch (Throwable e1) {
            try {
                return java.time.Instant.parse(s).toEpochMilli();
            } catch (Throwable e2) {
                return 0L;
            }
        }
    }
}
