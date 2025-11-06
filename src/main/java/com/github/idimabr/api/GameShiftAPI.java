package com.github.idimabr.api;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static com.github.idimabr.FinanceObjects.debug;

public class GameShiftAPI {

    public static boolean DEBUG = false;
    private static final String BASE_URL = "https://api.gameshift.dev";
    private static String API_KEY;
    private static String COLLECTION_ID;
    private static String DEFAULT_IMAGE_URL;
    public static String ICON_CONFIRMED;
    private static final Semaphore REQUEST_SEMAPHORE = new Semaphore(20);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static void load(Boolean debug, String key, String collectionId, String imageUrl, String iconConfirmed) {
        if (key == null || key.isEmpty()) {
            System.err.println("[GameShiftAPI] ERROR: API_KEY cannot be null or empty!");
            return;
        }
        if (collectionId == null || collectionId.isEmpty()) {
            System.err.println("[GameShiftAPI] ERROR: COLLECTION_ID cannot be null or empty!");
            return;
        }
        DEBUG = debug;
        API_KEY = key;
        COLLECTION_ID = collectionId;
        DEFAULT_IMAGE_URL = imageUrl != null && !imageUrl.isEmpty()
                ? imageUrl
                : "https://minex.gg/wp-content/uploads/2025/06/MineX_logo.png";
        ICON_CONFIRMED = iconConfirmed;
        if (DEBUG) {
            System.out.println("[GameShiftAPI] Loaded successfully with collection: " + collectionId);
        }
    }

    private static HttpRequest.Builder requestBuilder(String path) {
        if (API_KEY == null || API_KEY.isEmpty()) {
            System.err.println("[GameShiftAPI] ERROR: API not initialized! Call GameShiftAPI.load() first.");
            return null;
        }

        return HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("x-api-key", API_KEY)
                .header("Content-Type", "application/json")
                .timeout(REQUEST_TIMEOUT);
    }

    public static CompletableFuture<JSONObject> createItem(UUID uuid, String foundBy, Location location,
                                                           long discoveredAt, Map<String, String> nbtAttributes,
                                                           String imageUrl, ItemStack item) {

        HttpRequest.Builder builder = requestBuilder("/nx/unique-assets");
        if (builder == null) {
            return CompletableFuture.completedFuture(null);
        }

        String itemName = nbtAttributes.getOrDefault("MMOITEMS_NAME", "Unknown Item");
        String cleanName = itemName.replaceAll("§[0-9a-fk-or]", "").replaceAll("&[0-9a-fk-or]", "");

        JSONArray attributes = new JSONArray();

        attributes.put(new JSONObject()
                .put("traitType", "minex:found_by")
                .put("value", foundBy));

        attributes.put(new JSONObject()
                .put("traitType", "minex:world")
                .put("value", location.getWorld().getName()));

        attributes.put(new JSONObject()
                .put("traitType", "minex:coords")
                .put("value", location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ()));

        attributes.put(new JSONObject()
                .put("traitType", "minex:discovered_at")
                .put("value", String.valueOf(discoveredAt)));

        List<String> itemLore = new ArrayList<>();
        if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
            itemLore = item.getItemMeta().getLore();
        }

        if (!itemLore.isEmpty()) {
            JSONArray loreJsonArray = new JSONArray();
            for (String line : itemLore) {
                loreJsonArray.put(line);
            }

            attributes.put(new JSONObject()
                    .put("traitType", "mmoitems:MMOITEMS_DYNAMIC_LORE")
                    .put("value", loreJsonArray.toString()));
        }

        for (Map.Entry<String, String> entry : nbtAttributes.entrySet()) {
            String key = entry.getKey();
            String rawValue = entry.getValue();

            if (key.equalsIgnoreCase("MMOITEMS_DYNAMIC_LORE")) continue;
            String sanitizedValue = sanitizeToString(rawValue);

            attributes.put(new JSONObject()
                    .put("traitType", "mmoitems:" + key)
                    .put("value", sanitizedValue));
        }

        JSONObject details = new JSONObject()
                .put("collectionId", COLLECTION_ID)
                .put("name", cleanName)
                .put("description", "A unique item discovered in-game.")
                .put("imageUrl", imageUrl == null ? DEFAULT_IMAGE_URL : imageUrl)
                .put("attributes", attributes);

        JSONObject body = new JSONObject()
                .put("details", details)
                .put("destinationUserReferenceId", uuid.toString());

        HttpRequest request = builder
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return executeRequestWithRetry(request, 0);
    }

    private static String sanitizeToString(String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return "";
        }

        String sanitized = rawValue
                .replace("\n", " ")
                .replace("\r", " ")
                .replaceAll("\\s+", " ")
                .trim();

        //if (sanitized.length() > 500) {
        //    sanitized = sanitized.substring(0, 497) + "...";
        //}

        return sanitized;
    }

    /**
     * Get all items owned by a specific user.
     *
     * @param userUuid The UUID of the player
     * @param assetStatus Optional filter by status (e.g., "Committed", "Pending"). Use null for all statuses
     * @return CompletableFuture containing JSONObject with the items data, or null on failure
     */
    public static CompletableFuture<JSONObject> getUserItems(UUID userUuid, String assetStatus) {
        String path = "/nx/users/" + userUuid.toString() + "/items?types=UniqueAsset";

        if (assetStatus != null && !assetStatus.isEmpty()) {
            path += "&assetStatus=" + assetStatus;
        } else {
            path += "&assetStatus=";
        }

        HttpRequest.Builder builder = requestBuilder(path);
        if (builder == null) {
            return CompletableFuture.completedFuture(null);
        }

        HttpRequest request = builder.GET().build();

        return executeRequestWithRetry(request, 0);
    }

    /**
     * Get all items owned by a specific user (without status filter).
     *
     * @param userUuid The UUID of the player
     * @return CompletableFuture containing JSONObject with the items data, or null on failure
     */
    public static CompletableFuture<JSONObject> getUserItems(UUID userUuid) {
        return getUserItems(userUuid, null);
    }


    private static CompletableFuture<JSONObject> executeRequestWithRetry(HttpRequest request, int attempt) {
        final int maxRetries = 3;

        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!REQUEST_SEMAPHORE.tryAcquire(30, TimeUnit.SECONDS)) {
                    debug("[GameShiftAPI] ERROR: Timeout waiting for available request slot (too many concurrent requests)");
                    return null;
                }

                try {
                    if (DEBUG) {
                        printProgress("Sending request to: " + request.uri());
                    }

                    HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                    if (DEBUG) {
                        printProgress("Response status: " + response.statusCode());
                    }

                    if (response.statusCode() >= 400) {
                        String errorMsg = "HTTP " + response.statusCode() + ": " + response.body();

                        if (attempt < maxRetries && isRetryableStatus(response.statusCode())) {
                            debug("[GameShiftAPI] WARNING: " + errorMsg + " - Will retry");
                            return retryWithBackoff(request, attempt, errorMsg);
                        }

                        debug("[GameShiftAPI] ERROR: " + errorMsg);
                        return null;
                    }

                    return new JSONObject(response.body());

                } finally {
                    REQUEST_SEMAPHORE.release();
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                debug("[GameShiftAPI] ERROR: Request interrupted - " + e.getMessage());
                return null;

            } catch (Exception e) {
                if (attempt < maxRetries && isRetryableException(e)) {
                    debug("[GameShiftAPI] WARNING: " + e.getMessage() + " - Will retry");
                    return retryWithBackoff(request, attempt, e.getMessage());
                }

                debug("[GameShiftAPI] ERROR: Failed to execute request after " + (attempt + 1) + " attempts: " + e.getMessage());
                if (DEBUG) {
                    e.printStackTrace();
                }
                return null;
            }
        });
    }

    private static JSONObject retryWithBackoff(HttpRequest request, int attempt, String reason) {
        long delayMs = (long) Math.pow(2, attempt) * 1000;

        if (DEBUG) {
            printProgress("⚠ Retrying after " + delayMs + "ms (attempt " + (attempt + 1) + "/3). Reason: " + reason);
        }

        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            debug("[GameShiftAPI] ERROR: Retry interrupted");
            return null;
        }

        return executeRequestWithRetry(request, attempt + 1).join();
    }


    private static boolean isRetryableStatus(int statusCode) {
        return statusCode == 429 ||  // Too Many Requests
                statusCode == 503 ||  // Service Unavailable
                statusCode == 504;    // Gateway Timeout
    }

    private static boolean isRetryableException(Exception e) {
        String message = e.getMessage().toLowerCase();
        return message.contains("timeout") ||
                message.contains("connection") ||
                message.contains("too many") ||
                message.contains("concurrent streams");
    }

    private static void printProgress(String message) {
        int available = REQUEST_SEMAPHORE.availablePermits();
        int total = 20;
        int used = total - available;
        int queueLength = REQUEST_SEMAPHORE.getQueueLength();

        int percentage = (used * 100) / total;
        int barLength = 20;
        int filledBars = (used * barLength) / total;
        StringBuilder bar = new StringBuilder("[");

        for (int i = 0; i < barLength; i++) {
            if (i < filledBars) {
                bar.append("#");
            } else {
                bar.append(" ");
            }
        }
        bar.append("]");
        String stats = String.format("%s %d%% (%d/%d used)",
                bar,
                percentage,
                used,
                total);

        if (queueLength > 0) {
            stats += " [Queue: " + queueLength + "]";
        }

        debug("[GameShiftAPI] " + message);
        debug("            " + stats);
    }
}