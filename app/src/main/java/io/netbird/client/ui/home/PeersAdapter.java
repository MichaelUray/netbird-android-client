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

            if (peer.getStatus() == Status.CONNECTED) {
                binding.verticalLine.setBackgroundResource(R.drawable.peer_status_connected);
            } else {
                binding.verticalLine.setBackgroundResource(R.drawable.peer_status_disconnected);
            }

            // Phase 3.7i: tap opens detail dialog with all available info.
            binding.getRoot().setOnClickListener(v -> showDetailDialog(v.getContext(), peer));

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
    private static void showDetailDialog(Context ctx, Peer peer) {
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
        }

        new AlertDialog.Builder(ctx)
                .setTitle(peer.getFqdn())
                .setView(scroll)
                .setPositiveButton("Close", null)
                .show();
    }

    // Hardcoded color constants — AlertDialog content lives on a light
    // surface even when the host app is in dark mode, so theme-aware
    // colors (which would be near-white in dark mode) make text
    // invisible. These two ARGB values are visible on light AND dark
    // dialog surfaces.
    private static final int DIALOG_LABEL_COLOR = 0xFF6E6E6E;
    private static final int DIALOG_VALUE_COLOR = 0xFF1A1A1A;

    private static void addSectionHeader(Context ctx, LinearLayout parent, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text.toUpperCase());
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setTextColor(DIALOG_LABEL_COLOR);
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
        lbl.setTextColor(DIALOG_LABEL_COLOR);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        llp.topMargin = dp(ctx, 6);
        lbl.setLayoutParams(llp);

        // Value (regular, selectable for copy)
        TextView val = new TextView(ctx);
        val.setText(value);
        val.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        val.setTextColor(DIALOG_VALUE_COLOR);
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
}
