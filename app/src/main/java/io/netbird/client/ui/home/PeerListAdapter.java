package io.netbird.client.ui.home;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.netbird.client.R;
import io.netbird.gomobile.android.PeerInfo;
import io.netbird.gomobile.android.PeerInfoArray;

/**
 * RecyclerView adapter for the peer list on the home screen.
 * Groups peers by connection status (Connected > Idle > Offline), alpha within group.
 * Each row expands inline on tap to show a detail panel (accordion, single-expand).
 * Phase 3.7i (#5989).
 */
public class PeerListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_PEER   = 1;

    // Badge colors
    private static final int COLOR_CONNECTED = 0xFF4CAF50; // green
    private static final int COLOR_IDLE      = 0xFFFFC107; // amber
    private static final int COLOR_OFFLINE   = 0xFF9E9E9E; // grey

    private final LayoutInflater inflater;
    private boolean showFullDetails = false;

    /** Flat list of display items (section headers interleaved with peer rows). */
    private final List<Object> items = new ArrayList<>();

    /** Index of the currently expanded peer row (-1 = none). */
    private int expandedPosition = -1;

    public PeerListAdapter(Context context) {
        this.inflater = LayoutInflater.from(context);
    }

    public void setShowFullDetails(boolean showFull) {
        this.showFullDetails = showFull;
        notifyDataSetChanged();
    }

    /**
     * Replace the dataset with a new snapshot from the daemon.
     * NOTE: PeerInfo currently exposes IP, FQDN, ConnStatus.
     * Section grouping is based on ConnStatus only until Go-side PeerInfo
     * is enriched with Relayed/ServerOnline booleans (Phase 6.3 blocker).
     */
    public void submit(PeerInfoArray peerInfoArray) {
        // Sort into groups.
        List<PeerInfo> connected  = new ArrayList<>();
        List<PeerInfo> idle       = new ArrayList<>();
        List<PeerInfo> offline    = new ArrayList<>();

        if (peerInfoArray != null) {
            for (long i = 0; i < peerInfoArray.size(); i++) {
                PeerInfo p = peerInfoArray.get(i);
                Status s = Status.fromLong(p.getConnStatus());
                switch (s) {
                    case CONNECTED:  connected.add(p); break;
                    case IDLE:       idle.add(p);      break;
                    default:         offline.add(p);   break;
                }
            }
        }

        Comparator<PeerInfo> byFqdn = (a, b) -> {
            String fa = a.getFQDN(); String fb = b.getFQDN();
            if (fa == null) fa = a.getIP() != null ? a.getIP() : "";
            if (fb == null) fb = b.getIP() != null ? b.getIP() : "";
            return fa.compareToIgnoreCase(fb);
        };
        Collections.sort(connected, byFqdn);
        Collections.sort(idle,      byFqdn);
        Collections.sort(offline,   byFqdn);

        items.clear();
        expandedPosition = -1;

        if (!connected.isEmpty()) {
            items.add(new SectionHeader("Connected (" + connected.size() + ")", COLOR_CONNECTED));
            items.addAll(connected);
        }
        if (!idle.isEmpty()) {
            items.add(new SectionHeader("Idle (" + idle.size() + ")", COLOR_IDLE));
            items.addAll(idle);
        }
        if (!offline.isEmpty()) {
            items.add(new SectionHeader("Offline (" + offline.size() + ")", COLOR_OFFLINE));
            items.addAll(offline);
        }

        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof SectionHeader ? VIEW_TYPE_HEADER : VIEW_TYPE_PEER;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_HEADER) {
            View v = inflater.inflate(R.layout.item_peer_section_header, parent, false);
            return new HeaderViewHolder(v);
        } else {
            View v = inflater.inflate(R.layout.item_peer_row, parent, false);
            return new PeerViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            SectionHeader h = (SectionHeader) items.get(position);
            ((HeaderViewHolder) holder).textSection.setText(h.title);
        } else {
            PeerInfo peer = (PeerInfo) items.get(position);
            PeerViewHolder pvh = (PeerViewHolder) holder;

            String displayName = peer.getFQDN();
            if (displayName == null || displayName.isEmpty()) {
                displayName = peer.getIP() != null ? peer.getIP() : "(unknown)";
            }
            pvh.textFqdn.setText(displayName);
            pvh.textIp.setText(peer.getIP() != null ? peer.getIP() : "");

            // Badge color
            Status s = Status.fromLong(peer.getConnStatus());
            int badgeColor;
            switch (s) {
                case CONNECTED:  badgeColor = COLOR_CONNECTED; break;
                case IDLE:       badgeColor = COLOR_IDLE; break;
                default:         badgeColor = COLOR_OFFLINE; break;
            }
            ((GradientDrawable) pvh.statusBadge.getBackground()).setColor(badgeColor);

            // Accordion expand/collapse
            boolean expanded = (position == expandedPosition);
            pvh.detailPanel.setVisibility(expanded ? View.VISIBLE : View.GONE);

            if (expanded) {
                pvh.textConnType.setText(buildDetailText(peer, showFullDetails));
            }

            pvh.rowHeader.setOnClickListener(v -> {
                int adapterPos = pvh.getAdapterPosition();
                if (adapterPos == RecyclerView.NO_ID) return;
                if (expandedPosition == adapterPos) {
                    // Collapse
                    int old = expandedPosition;
                    expandedPosition = -1;
                    notifyItemChanged(old);
                } else {
                    int old = expandedPosition;
                    expandedPosition = adapterPos;
                    if (old != -1) notifyItemChanged(old);
                    notifyItemChanged(expandedPosition);
                }
            });
        }
    }

    // --- Detail helpers ---

    /**
     * Builds the multi-line detail text shown in the expanded accordion panel.
     * Phase 3.7i (#5989): uses enriched PeerInfo fields from Go-side.
     */
    private String buildDetailText(PeerInfo p, boolean full) {
        StringBuilder sb = new StringBuilder();
        sb.append("IP:                ").append(orDash(p.getIP())).append("\n");
        sb.append("FQDN:              ").append(orDash(p.getFQDN())).append("\n");
        String connType = p.getConnStatus() == 1 ? "Connected" : p.getConnStatus() == 2 ? "Connecting" : "Idle";
        if (p.getRelayed()) connType += " (relayed)";
        sb.append("Connection type:   ").append(connType).append("\n");
        sb.append("Effective mode:    ").append(orDash(p.getEffectiveConnectionMode())).append("\n");
        if (!isEqual(p.getEffectiveConnectionMode(), p.getConfiguredConnectionMode())
                && p.getConfiguredConnectionMode() != null && !p.getConfiguredConnectionMode().isEmpty()) {
            sb.append("Configured mode:   ").append(orDash(p.getConfiguredConnectionMode())).append("\n");
        }
        sb.append("Last handshake:    ").append(orDash(p.getLastWireguardHandshake())).append("\n");
        long latencyMs = p.getLatencyMs();
        sb.append("Latency:           ").append(latencyMs > 0 ? latencyMs + " ms" : "-").append("\n");
        if (p.getConnStatus() == 1) { // Connected
            if (p.getRelayed()) {
                sb.append("Relay server:      ").append(orDash(p.getRelayServerAddress())).append("\n");
            } else {
                sb.append("Local endpoint:    ").append(orDash(p.getLocalIceCandidateEndpoint())).append("\n");
                sb.append("Remote endpoint:   ").append(orDash(p.getRemoteIceCandidateEndpoint())).append("\n");
            }
        }
        sb.append("Last seen at srv:  ").append(orDash(p.getLastSeenAtServer())).append("\n");
        sb.append("Groups:            ").append(orDash(p.getGroups())).append("\n");
        return sb.toString();
    }

    private String orDash(String s) {
        return (s == null || s.isEmpty()) ? "-" : s;
    }

    private boolean isEqual(String a, String b) {
        return (a == null ? "" : a).equals(b == null ? "" : b);
    }

    // --- Inner types ---

    private static class SectionHeader {
        final String title;
        final int badgeColor;
        SectionHeader(String title, int badgeColor) {
            this.title = title;
            this.badgeColor = badgeColor;
        }
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        final TextView textSection;
        HeaderViewHolder(@NonNull View v) {
            super(v);
            textSection = (TextView) v;
        }
    }

    static class PeerViewHolder extends RecyclerView.ViewHolder {
        final View rowHeader;
        final View statusBadge;
        final TextView textFqdn;
        final TextView textIp;
        final View detailPanel;
        final TextView textConnType;

        PeerViewHolder(@NonNull View v) {
            super(v);
            rowHeader   = v.findViewById(R.id.peer_row_header);
            statusBadge = v.findViewById(R.id.peer_status_badge);
            textFqdn    = v.findViewById(R.id.text_peer_fqdn);
            textIp      = v.findViewById(R.id.text_peer_ip);
            detailPanel = v.findViewById(R.id.peer_row_detail);
            textConnType= v.findViewById(R.id.text_peer_conn_type);
        }
    }
}
