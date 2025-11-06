package com.github.idimabr.models;

import lombok.Getter;
import org.json.JSONArray;

@Getter
public class GameShiftItem {
        private final String gameshiftId;
        private final String name;
        private final String itemType;
        private final String itemId;
        private final JSONArray attributes;

        public GameShiftItem(String gameshiftId, String name, String itemType, String itemId, JSONArray attributes) {
            this.gameshiftId = gameshiftId;
            this.name = name;
            this.itemType = itemType;
            this.itemId = itemId;
            this.attributes = attributes;
        }
    }