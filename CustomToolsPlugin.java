package com.finn.customtools;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class CustomToolsPlugin extends JavaPlugin implements Listener {
    private final Set<UUID> processing = new HashSet<>();
    private NamespacedKey toolKey;
    private File pendingFile;
    private YamlConfiguration pending;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        toolKey = new NamespacedKey(this, "tool_type");
        loadPending();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("CustomTools enabled.");
    }

    private void loadPending() {
        pendingFile = new File(getDataFolder(), "pending.yml");
        if (!pendingFile.exists()) {
            try {
                getDataFolder().mkdirs();
                pendingFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Could not create pending.yml: " + e.getMessage());
            }
        }
        pending = YamlConfiguration.loadConfiguration(pendingFile);
    }

    private void savePending() {
        try {
            pending.save(pendingFile);
        } catch (IOException e) {
            getLogger().severe("Could not save pending deliveries: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // In-game command access is OP-only. Console commands remain allowed
        // so Tebex can safely deliver purchases through its console command action.
        if (sender instanceof Player player && !player.isOp()) {
            send(sender, "no-permission");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            send(sender, "reloaded");
            return true;
        }

        if (args.length != 3 || !args[0].equalsIgnoreCase("give")) {
            send(sender, "usage");
            return true;
        }

        String targetName = args[1];
        String requested = args[2].toLowerCase();
        List<String> types = switch (requested) {
            case "pickaxe" -> List.of("pickaxe");
            case "axe" -> List.of("axe");
            case "shovel" -> List.of("shovel");
            case "bundle" -> List.of("pickaxe", "axe", "shovel");
            default -> null;
        };

        if (types == null) {
            send(sender, "invalid-tool");
            return true;
        }

        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) {
            for (String type : types) giveItem(online, createTool(type));
            sender.sendMessage(message("given")
                    .replaceText(builder -> builder.matchLiteral("%tool%").replacement(requested))
                    .replaceText(builder -> builder.matchLiteral("%player%").replacement(online.getName())));
            return true;
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(targetName);
        if (offline == null || (!offline.hasPlayedBefore() && !offline.isOnline())) {
            send(sender, "player-not-found");
            return true;
        }

        List<String> queue = pending.getStringList(offline.getUniqueId().toString());
        queue = new ArrayList<>(queue);
        queue.addAll(types);
        pending.set(offline.getUniqueId().toString(), queue);
        savePending();

        sender.sendMessage(message("queued")
                .replaceText(builder -> builder.matchLiteral("%tool%").replacement(requested))
                .replaceText(builder -> builder.matchLiteral("%player%").replacement(targetName)));
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String key = player.getUniqueId().toString();
        List<String> queued = pending.getStringList(key);
        if (queued.isEmpty()) return;

        for (String type : queued) {
            if (List.of("pickaxe", "axe", "shovel").contains(type)) {
                giveItem(player, createTool(type));
            }
        }
        pending.set(key, null);
        savePending();
        player.sendMessage(Component.text("Your purchased CustomTools items have been delivered!"));
    }

    private void giveItem(Player player, ItemStack item) {
        var leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(stack ->
                player.getWorld().dropItemNaturally(player.getLocation(), stack));
    }

    private ItemStack createTool(String type) {
        String path = "tools." + type + ".";
        Material material = Material.matchMaterial(getConfig().getString(path + "material", ""));
        if (material == null || !material.isItem()) {
            material = switch (type) {
                case "axe" -> Material.DIAMOND_AXE;
                case "shovel" -> Material.DIAMOND_SHOVEL;
                default -> Material.DIAMOND_PICKAXE;
            };
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

        String name = getConfig().getString(path + "name", "&bCustom " + type);
        meta.displayName(legacy.deserialize(name));

        List<String> loreLines = getConfig().getStringList(path + "lore");
        if (!loreLines.isEmpty()) {
            List<Component> lore = loreLines.stream().map(legacy::deserialize).toList();
            meta.lore(lore);
        }

        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, type);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickaxeMine(BlockBreakEvent event) {
        if (!getConfig().getBoolean("mining.enabled", true)) return;

        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (processing.contains(id)) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!isCustomTool(tool, "pickaxe")) return;

        Block centre = event.getBlock();
        Material centreType = centre.getType();
        BlockFace facing = player.getFacing();

        int[][] offsets;
        if (facing == BlockFace.NORTH || facing == BlockFace.SOUTH) {
            offsets = new int[][]{{-1,-1,0},{0,-1,0},{1,-1,0},{-1,0,0},{1,0,0},{-1,1,0},{0,1,0},{1,1,0}};
        } else if (facing == BlockFace.EAST || facing == BlockFace.WEST) {
            offsets = new int[][]{{0,-1,-1},{0,-1,0},{0,-1,1},{0,0,-1},{0,0,1},{0,1,-1},{0,1,0},{0,1,1}};
        } else {
            offsets = new int[][]{{-1,0,-1},{-1,0,0},{-1,0,1},{0,0,-1},{0,0,1},{1,0,-1},{1,0,0},{1,0,1}};
        }

        boolean sameOnly = getConfig().getBoolean("mining.same-block-only", false);
        processing.add(id);
        try {
            for (int[] offset : offsets) {
                Block target = centre.getRelative(offset[0], offset[1], offset[2]);
                if (target.getType().isAir()) continue;
                if (sameOnly && target.getType() != centreType) continue;
                if (!target.getType().isBlock() || target.isLiquid()) continue;
                // Uses the normal player break path so protection plugins and block-break events can cancel it.
                player.breakBlock(target);
            }
        } finally {
            processing.remove(id);
        }
    }

    private boolean isCustomTool(ItemStack item, String type) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        String stored = item.getItemMeta().getPersistentDataContainer()
                .get(toolKey, PersistentDataType.STRING);
        return type.equals(stored);
    }

    private void send(CommandSender sender, String key) {
        sender.sendMessage(message(key));
    }

    private Component message(String key) {
        String prefix = getConfig().getString("messages.prefix", "&8[&bCustomTools&8] ");
        String body = getConfig().getString("messages." + key, "&cMissing message: " + key);
        return LegacyComponentSerializer.legacyAmpersand().deserialize(prefix + body);
    }
}
