package io.netbird.client.tool;

import android.content.Context;
import android.content.SharedPreferences;

public class Preferences {

    private final String keyTraceLog = "tracelog";

    private final SharedPreferences sharedPref;

    public Preferences(Context context) {
       sharedPref = context.getSharedPreferences("netbird", Context.MODE_PRIVATE);
    }

    public boolean isTraceLogEnabled() {
       return sharedPref.getBoolean(keyTraceLog, false);
    }
    public void enableTraceLog() {
        sharedPref.edit().putBoolean(keyTraceLog, true).apply();
    }

    public void disableTraceLog() {
        sharedPref.edit().putBoolean(keyTraceLog, false).apply();
    }

    public static String defaultServer() {
        return "https://api.netbird.io";
    }

    // Phase 3.7i (#5989): peer detail level (0 = Standard, 1 = Full).
    private final String keyPeerDetailLevel = "peerDetailLevel";

    public int getPeerDetailLevel() {
        return sharedPref.getInt(keyPeerDetailLevel, 0);
    }

    public void setPeerDetailLevel(int level) {
        sharedPref.edit().putInt(keyPeerDetailLevel, level).apply();
    }
}
