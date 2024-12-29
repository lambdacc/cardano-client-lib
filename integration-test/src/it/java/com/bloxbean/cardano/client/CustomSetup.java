package com.bloxbean.cardano.client;

import org.jetbrains.annotations.NotNull;

public class CustomSetup {
    @NotNull
    public static String getBfProjectId(String bfProjectId) {
        if (bfProjectId == null || bfProjectId.isEmpty()) {
            bfProjectId = "preprodXYmgUpJzQ9wz3FXfzYINIRNOPYVTdgfv";
        }
        return bfProjectId;
    }
}
