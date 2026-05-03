package io.netbird.client.ui.home;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.airbnb.lottie.LottieAnimationView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.netbird.client.PlatformUtils;
import io.netbird.client.R;
import io.netbird.client.ServiceAccessor;
import io.netbird.gomobile.android.PeerInfoArray;
import io.netbird.client.StateListener;
import io.netbird.client.StateListenerRegistry;
import io.netbird.client.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment implements StateListener {

    private FragmentHomeBinding binding;
    private ServiceAccessor serviceAccessor;
    private StateListenerRegistry stateListenerRegistry;

    private TextView textHostname;
    private TextView textNetworkAddress;

    private LottieAnimationView buttonConnect;
    private ButtonAnimation buttonAnimation;
    private boolean isConnected;
    private PeerListAdapter peerAdapter;

    // serializes peer-list refreshes off the UI thread; serviceAccessor.getPeersList()
    // is a JNI call into Go that can take seconds during engine bootstrap/teardown
    private ExecutorService refreshExecutor;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof ServiceAccessor) {
            serviceAccessor = (ServiceAccessor) context;
        } else {
            throw new RuntimeException(context + " must implement ServiceAccessor");
        }
        if(context instanceof StateListenerRegistry) {
            stateListenerRegistry = (StateListenerRegistry) context;
        } else {
            throw new RuntimeException(context + " must implement StateListenerRegistry");
        }
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        textHostname = binding.textHostname;
        textNetworkAddress = binding.textNetworkAddress;
        TextView textConnStatus = binding.textConnectionStatus;

        updatePeerCounts(PeerCounts.empty());

        buttonConnect = binding.btnConnect;
        // Try to load the correct Lottie file for dark/light mode, fallback to light if dark is missing
        boolean isDarkMode = (requireContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        String lottieFile = isDarkMode ? "button_full_dark.json" : "button_full.json";
        try {
            buttonConnect.setAnimation(lottieFile);
        } catch (Exception e) {
            // fallback to light mode animation if dark mode file is missing or invalid
            buttonConnect.setAnimation("button_full.json");
        }

        if(buttonAnimation == null) {
            buttonAnimation = new ButtonAnimation();
        }
        buttonAnimation.refresh(buttonConnect, textConnStatus);

        buttonConnect.setOnClickListener(v -> {
            if (serviceAccessor == null) {
                return;
            }

            if (isConnected) {
                // We're currently connected, so disconnect
                buttonConnect.setEnabled(false);
                buttonAnimation.disconnecting();
                serviceAccessor.switchConnection(false);
            } else {
                // We're currently disconnected, so connect
                buttonAnimation.connecting();
                serviceAccessor.switchConnection(true);
            }
        });

        // peers button
        FrameLayout openPanelCardView = binding.peersBtn;
        openPanelCardView.setOnClickListener(v -> {
            // Clear focus from the button to remove highlight
            v.clearFocus();
            
            BottomDialogFragment fragment = new BottomDialogFragment();
            fragment.show(getParentFragmentManager(), fragment.getTag());
        });

        // Peer list RecyclerView (Phase 3.7i #5989)
        peerAdapter = new PeerListAdapter(requireContext());
        binding.recyclerPeers.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerPeers.setAdapter(peerAdapter);

        if (PlatformUtils.isAndroidTV(requireContext())) {
            root.postDelayed(() -> {
                if (buttonConnect != null && buttonConnect.isEnabled()) {
                    buttonConnect.requestFocus();
                }
            }, 200);
        }

        refreshExecutor = Executors.newSingleThreadExecutor();
        stateListenerRegistry.registerServiceStateListener(this);
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        buttonAnimation.destroy();
        stateListenerRegistry.unregisterServiceStateListener(this);
        if (refreshExecutor != null) {
            refreshExecutor.shutdown();
            refreshExecutor = null;
        }
        FrameLayout openPanelCardView = binding.peersBtn;
        openPanelCardView.setOnClickListener(null);
        peerAdapter = null;
        binding = null;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        serviceAccessor = null;
    }

    @Override
    public void onEngineStarted() {

    }

    @Override
    public void onEngineStopped() {
        isConnected = false;
        buttonConnect.post(() -> {
            buttonAnimation.disconnected();
            buttonConnect.setEnabled(true);
        });
    }

    @Override
    public void onAddressChanged(String netAddr, String hostname) {
        if(textNetworkAddress == null || textHostname == null) {
            return;
        }

        textNetworkAddress.post(() -> textNetworkAddress.setText(netAddr));
        textHostname.post(() -> textHostname.setText(hostname));
    }

    @Override
    public void onConnected() {
        isConnected = true;

        buttonConnect.post(() -> {
            buttonAnimation.connected();
            buttonConnect.setEnabled(true);
        });
    }

    @Override
    public void onConnecting() {
        buttonConnect.post(() -> buttonAnimation.connecting());
    }

    @Override
    public void onDisconnected() {
        isConnected = false;
        buttonConnect.post(() -> {
            buttonAnimation.disconnected();
            buttonConnect.setEnabled(true);
        });
        updatePeerCounts(PeerCounts.empty());
        if (peerAdapter != null) peerAdapter.submit(null);
    }

    @Override
    public void onDisconnecting() {
        buttonConnect.post(() -> buttonAnimation.disconnecting());
    }

    @Override
    public void onPeersListChanged(long numberOfPeers) {
        ExecutorService executor = refreshExecutor;
        if (executor == null) {
            return;
        }
        executor.execute(() -> {
            PeerCounts c = new PeerCounts(
                    serviceAccessor.getConfiguredPeersTotal(),
                    serviceAccessor.getServerOnlinePeers(),
                    serviceAccessor.getP2pConnectedPeers(),
                    serviceAccessor.getRelayedConnectedPeers(),
                    serviceAccessor.getIdleOnlinePeers(),
                    serviceAccessor.getServerOfflinePeers()
            );
            PeerInfoArray peersList = serviceAccessor.getPeersList();
            updatePeerCounts(c);
            if (peerAdapter != null) peerAdapter.submit(peersList);
        });
    }

    private void updatePeerCounts(PeerCounts c) {
        if (binding == null) return;
        String summary = getString(R.string.peers_count_summary, c.serverOnline, c.configuredTotal);
        String breakdown = getString(R.string.peers_count_breakdown,
                c.p2pConnected, c.relayedConnected, c.idleOnline, c.serverOffline);
        binding.textPeersSummary.post(() ->
                binding.textPeersSummary.setText(android.text.Html.fromHtml(summary, android.text.Html.FROM_HTML_MODE_LEGACY)));
        binding.textPeersBreakdown.post(() ->
                binding.textPeersBreakdown.setText(breakdown));
    }
}