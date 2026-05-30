package org.san24.wgregionvisualizer.bridge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class WGRegionVisualizerBridgePlugin extends JavaPlugin implements CommandExecutor, TabCompleter {
    private static final String CHANNEL = "san24:wgviz";
    private static final String GLOBAL_REGION_ID = "__global__";

    private final Gson gson = new Gson();
    private int maxRegionsAll;
    private int maxPayloadBytes;
    private boolean worldGuardAvailable;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadBridgeConfig();

        Plugin worldGuard = getServer().getPluginManager().getPlugin("WorldGuard");
        Plugin worldEdit = getServer().getPluginManager().getPlugin("WorldEdit");
        if (worldGuard == null || !worldGuard.isEnabled() || worldEdit == null || !worldEdit.isEnabled()) {
            getLogger().severe("WorldGuard and WorldEdit are required. WGRegionVisualizerBridge will be disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        worldGuardAvailable = true;
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        if (getCommand("wgviz") != null) {
            getCommand("wgviz").setExecutor(this);
            getCommand("wgviz").setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, CHANNEL);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("wgviz.use")) {
            sender.sendMessage("権限がありません。");
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subCommand) {
            case "show" -> handleShow(sender, args);
            case "all" -> handleAll(sender);
            case "hide" -> handleHide(sender);
            case "list" -> handleList(sender);
            case "reload" -> handleReload(sender);
            default -> {
                sendUsage(sender);
                yield true;
            }
        };
    }

    private boolean handleShow(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("このコマンドはプレイヤーのみ実行できます。");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("使い方: /wgviz show <regionId>");
            return true;
        }

        RegionManager manager = getRegionManager(player.getWorld());
        if (manager == null) {
            sender.sendMessage("このワールドのWorldGuardリージョンを取得できませんでした。");
            return true;
        }

        ProtectedRegion region = manager.getRegion(args[1]);
        if (region == null) {
            sender.sendMessage("リージョンが見つかりません: " + args[1]);
            return true;
        }

        if (sendRegion(player, player.getWorld(), region)) {
            sender.sendMessage("リージョンを送信しました。Modが入っていない場合は表示されません。");
        }
        return true;
    }

    private boolean handleAll(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("このコマンドはプレイヤーのみ実行できます。");
            return true;
        }

        RegionManager manager = getRegionManager(player.getWorld());
        if (manager == null) {
            sender.sendMessage("このワールドのWorldGuardリージョンを取得できませんでした。");
            return true;
        }

        List<ProtectedRegion> regions = manager.getRegions().values().stream()
                .filter(region -> !GLOBAL_REGION_ID.equals(region.getId()))
                .sorted(Comparator.comparing(ProtectedRegion::getId, String.CASE_INSENSITIVE_ORDER))
                .limit(maxRegionsAll)
                .toList();

        int sent = 0;
        for (ProtectedRegion region : regions) {
            if (sendRegion(player, player.getWorld(), region)) {
                sent++;
            }
        }

        int total = Math.max(0, manager.getRegions().size() - (manager.hasRegion(GLOBAL_REGION_ID) ? 1 : 0));
        sender.sendMessage(sent + "件のリージョンを送信しました。Modが入っていない場合は表示されません。");
        if (total > maxRegionsAll) {
            sender.sendMessage("上限 " + maxRegionsAll + " 件まで送信しました。config.yml の max-regions-all で変更できます。");
        }
        return true;
    }

    private boolean handleHide(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("このコマンドはプレイヤーのみ実行できます。");
            return true;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("action", "clear");
        sendPayload(player, payload);
        sender.sendMessage("非表示命令を送信しました。");
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("このコマンドはプレイヤーのみ実行できます。");
            return true;
        }

        RegionManager manager = getRegionManager(player.getWorld());
        if (manager == null) {
            sender.sendMessage("このワールドのWorldGuardリージョンを取得できませんでした。");
            return true;
        }

        List<String> ids = new ArrayList<>(manager.getRegions().keySet());
        ids.remove(GLOBAL_REGION_ID);
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        sender.sendMessage("リージョン (" + ids.size() + "): " + String.join(", ", ids));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("wgviz.reload")) {
            sender.sendMessage("権限がありません。");
            return true;
        }

        reloadConfig();
        loadBridgeConfig();
        sender.sendMessage("WGRegionVisualizerBridge の設定を再読み込みしました。");
        return true;
    }

    private void loadBridgeConfig() {
        maxRegionsAll = Math.max(1, getConfig().getInt("max-regions-all", 200));
        maxPayloadBytes = clamp(getConfig().getInt("max-payload-bytes", 30000), 1024, 32766);
    }

    private boolean sendRegion(Player player, World world, ProtectedRegion region) {
        JsonObject payload = toPayload(world, region);
        return sendPayload(player, payload);
    }

    private boolean sendPayload(Player player, JsonObject payload) {
        String json = gson.toJson(payload);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxPayloadBytes) {
            getLogger().warning("Plugin message was too large and was skipped: " + bytes.length + " bytes");
            player.sendMessage("送信データが大きすぎるため一部リージョンをスキップしました。");
            return false;
        }

        try {
            player.sendPluginMessage(this, CHANNEL, bytes);
            return true;
        } catch (IllegalArgumentException ex) {
            getLogger().warning("Failed to send plugin message: " + ex.getMessage());
            player.sendMessage("クライアントへの送信に失敗しました。サーバーログを確認してください。");
            return false;
        }
    }

    private JsonObject toPayload(World world, ProtectedRegion region) {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "add");
        payload.addProperty("world", world.getName());
        payload.addProperty("dimension", world.getKey().asString());
        payload.addProperty("regionId", region.getId());

        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        payload.addProperty("minY", min.y());
        payload.addProperty("maxY", max.y());

        if (region instanceof ProtectedCuboidRegion) {
            payload.addProperty("shape", "cuboid");
            payload.addProperty("minX", min.x());
            payload.addProperty("minZ", min.z());
            payload.addProperty("maxX", max.x());
            payload.addProperty("maxZ", max.z());
            return payload;
        }

        if (region instanceof ProtectedPolygonalRegion polygonalRegion) {
            payload.addProperty("shape", "polygon");
            JsonArray points = new JsonArray();
            for (BlockVector2 point : polygonalRegion.getPoints()) {
                JsonObject pointPayload = new JsonObject();
                pointPayload.addProperty("x", point.x());
                pointPayload.addProperty("z", point.z());
                points.add(pointPayload);
            }
            payload.add("points", points);
            return payload;
        }

        payload.addProperty("shape", "cuboid");
        payload.addProperty("minX", min.x());
        payload.addProperty("minZ", min.z());
        payload.addProperty("maxX", max.x());
        payload.addProperty("maxZ", max.z());
        return payload;
    }

    @Nullable
    private RegionManager getRegionManager(World world) {
        if (!worldGuardAvailable) {
            return null;
        }
        return WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("使い方:");
        sender.sendMessage("/wgviz show <regionId>");
        sender.sendMessage("/wgviz all");
        sender.sendMessage("/wgviz hide");
        sender.sendMessage("/wgviz list");
        sender.sendMessage("/wgviz reload");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subCommands = sender.hasPermission("wgviz.reload")
                    ? List.of("show", "all", "hide", "list", "reload")
                    : List.of("show", "all", "hide", "list");
            return filterPrefix(subCommands, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("show") && sender instanceof Player player) {
            RegionManager manager = getRegionManager(player.getWorld());
            if (manager == null) {
                return Collections.emptyList();
            }
            return filterPrefix(manager.getRegions().keySet(), args[1]);
        }
        return Collections.emptyList();
    }

    private static List<String> filterPrefix(Collection<String> values, String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lowerPrefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
