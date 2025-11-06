package com.github.idimabr.models;

import lombok.Getter;

import java.util.List;

@Getter
public class SyncResult {
        public final boolean success;
        public final String message;
        public final int itemsRestored;
        public final int totalGameShiftItems;
        public final List<String> itemNames;

        public SyncResult(boolean success, String message, int itemsRestored, int totalGameShiftItems, List<String> itemNames) {
            this.success = success;
            this.message = message;
            this.itemsRestored = itemsRestored;
            this.totalGameShiftItems = totalGameShiftItems;
            this.itemNames = itemNames;
        }
}