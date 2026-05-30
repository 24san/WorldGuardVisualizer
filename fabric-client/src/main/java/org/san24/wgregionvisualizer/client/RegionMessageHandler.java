package org.san24.wgregionvisualizer.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

final class RegionMessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("wg_region_visualizer");
    private static final Gson GSON = new Gson();

    private RegionMessageHandler() {
    }

    static void handle(String json) {
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null || !root.has("action")) {
                LOGGER.warn("Ignored malformed WG visualizer payload: {}", json);
                return;
            }

            String action = root.get("action").getAsString();
            if ("clear".equals(action)) {
                RegionStore.clear();
                LOGGER.info("Cleared WG visualizer regions.");
                return;
            }
            if (!"add".equals(action)) {
                LOGGER.warn("Ignored unknown WG visualizer action: {}", action);
                return;
            }

            VisualizedRegion region = parseRegion(root);
            if (region != null) {
                RegionStore.put(region);
                LOGGER.info("Received WG region '{}' for {}.", region.regionId(), region.dimension().isBlank() ? region.world() : region.dimension());
                notifyClient("WG Region Visualizer: " + region.regionId() + " を受信しました。");
            }
        } catch (JsonParseException | IllegalStateException ex) {
            LOGGER.warn("Failed to parse WG visualizer payload.", ex);
        }
    }

    private static VisualizedRegion parseRegion(JsonObject root) {
        String world = string(root, "world", "");
        String dimension = string(root, "dimension", "");
        String regionId = string(root, "regionId", "");
        String shapeName = string(root, "shape", "cuboid");
        if (regionId.isBlank()) {
            return null;
        }

        int minY = integer(root, "minY", -64);
        int maxY = integer(root, "maxY", 319);
        if ("polygon".equals(shapeName)) {
            List<VisualizedRegion.PointXZ> points = new ArrayList<>();
            JsonArray array = root.getAsJsonArray("points");
            if (array != null) {
                for (JsonElement element : array) {
                    if (element instanceof JsonObject point) {
                        points.add(new VisualizedRegion.PointXZ(integer(point, "x", 0), integer(point, "z", 0)));
                    }
                }
            }
            if (points.size() < 3) {
                return null;
            }
            return VisualizedRegion.polygon(world, dimension, regionId, minY, maxY, points);
        }

        int minX = integer(root, "minX", 0);
        int minZ = integer(root, "minZ", 0);
        int maxX = integer(root, "maxX", minX);
        int maxZ = integer(root, "maxZ", minZ);
        return VisualizedRegion.cuboid(world, dimension, regionId, minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) ? object.get(key).getAsString() : fallback;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        return object.has(key) ? object.get(key).getAsInt() : fallback;
    }

    private static void notifyClient(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(message), false);
        }
    }
}
