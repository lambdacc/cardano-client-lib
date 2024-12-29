package com.bloxbean.cardano.client.quicktx;

import org.jetbrains.annotations.NotNull;

public class CustomSetup {
    @NotNull
    public static String getBfProjectId(String bfProjectId) {
        if (bfProjectId == null || bfProjectId.isEmpty()) {
            //bfProjectId = "preprodXYmgUpJzQ9wz3FXfzYINIRNOPYVTdgfv";
            bfProjectId = "previewoTwbcUU6BoMgdsxswfuWMRBdHm1c1j61";
        }
        return bfProjectId;
    }
}
