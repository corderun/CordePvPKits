package org.corderun.cordepvpkits;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import net.raidstone.wgevents.WorldGuardEvents;
import net.raidstone.wgevents.events.RegionEnteredEvent;
import net.raidstone.wgevents.events.RegionLeftEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.*;

public final class CordePvPKits extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
    }

    private Map<String, ItemStack[]> playerInventories = new HashMap<>();
    private Map<String, ItemStack[]> playerArmor = new HashMap<>();
    private List<Player> playerPvPDeath = new ArrayList<>();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("pvpkits")) {
            if(!sender.hasPermission("cordepvpkits.admin")) {
                sender.sendMessage(getConfig().getString("messages.no-perm").replace("&", "§"));
                return true;
            }
            if(args.length == 0) {
                sender.sendMessage(getConfig().getString("messages.help").replace("&", "§"));
                return true;
            }
            if(args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                sender.sendMessage(getConfig().getString("messages.reload").replace("&", "§"));
                return true;
            }
            if(args[0].equalsIgnoreCase("setspawn")) {
                Player player = (Player) sender;
                Location loc = player.getLocation();
                getConfig().set("location-spawn", loc);
                saveConfig();
                sender.sendMessage(getConfig().getString("messages.setspawn").replace("&", "§"));
                return true;
            }
            if(args[0].equalsIgnoreCase("set")) {
                Player player = (Player) sender;
                saveKit(player);
                sender.sendMessage(getConfig().getString("messages.set").replace("&", "§"));
                return true;
            }
        }
        return true;
    }

    @EventHandler
    public void onEnterRegion(RegionEnteredEvent event) {
        Player player = event.getPlayer();
        String regionName = event.getRegionName();
        if (regionName.equalsIgnoreCase(getConfig().getString("pvp-region"))) {
            if(getConfig().getBoolean("enable-notify")) {
                player.sendMessage(getConfig().getString("messages.notify.enter").replace("&", "§"));
            }
            playerInventories.put(player.getName(), player.getInventory().getContents());
            playerArmor.put(player.getName(), player.getInventory().getArmorContents());
            player.getInventory().clear();
            loadKit(player);
        }
    }

    @EventHandler
    public void onLeaveRegion(RegionLeftEvent event) {
        Player player = event.getPlayer();
        String regionName = event.getRegionName();
        if (regionName.equalsIgnoreCase(getConfig().getString("pvp-region"))) {
            if(getConfig().getBoolean("enable-notify")) {
                player.sendMessage(getConfig().getString("messages.notify.leave").replace("&", "§"));
            }
            player.getInventory().clear();
            player.getInventory().setContents(playerInventories.get(player.getName()));
            player.getInventory().setArmorContents(playerArmor.get(player.getName()));
            playerInventories.remove(player.getName());
            playerArmor.remove(player.getName());
        }
    }

    @EventHandler
    public void onPlayerDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if(WorldGuardEvents.isPlayerInAnyRegion(player.getUniqueId(), getConfig().getString("pvp-region"))){
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeathPlayer(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if(WorldGuardEvents.isPlayerInAnyRegion(player.getUniqueId(), getConfig().getString("pvp-region"))){
            event.getDrops().clear();
            playerPvPDeath.add(player);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerPostRespawnEvent event) {
        Player player = event.getPlayer();
        if(playerPvPDeath.contains(player)){
            player.teleport(Objects.requireNonNull(getConfig().getLocation("location-spawn")));
            playerPvPDeath.remove(player);
        }
    }

    @EventHandler
    public void onPlayerLeavePvp(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if(WorldGuardEvents.isPlayerInAnyRegion(player.getUniqueId(), getConfig().getString("pvp-region"))){
            player.teleport(Objects.requireNonNull(getConfig().getLocation("location-spawn")));
        }
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if(WorldGuardEvents.isPlayerInAnyRegion(player.getUniqueId(), getConfig().getString("pvp-region"))){
                player.teleport(Objects.requireNonNull(getConfig().getLocation("location-spawn")));
                player.getInventory().clear();
                player.getInventory().setContents(playerInventories.get(player.getName()));
                player.getInventory().setArmorContents(playerArmor.get(player.getName()));
                playerInventories.remove(player.getName());
                playerArmor.remove(player.getName());
            }
        }
    }

    public static void saveKit(Player player) {
        Inventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();

        Map<String, Object> kit = new HashMap<>();
        Map<String, Object> items = new HashMap<>();

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null) {
                Map<String, Object> itemData = new HashMap<>();
                itemData.put("type", item.getType().name());
                itemData.put("amount", item.getAmount());
                itemData.put("durability", item.getDurability());
                itemData.put("name", item.getItemMeta().getDisplayName());
                itemData.put("lore", item.getItemMeta().getLore());
                items.put(String.valueOf(i), itemData);
            }
        }

        kit.put("items", items);

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml();

        try {
            FileWriter writer = new FileWriter("plugins/CordePvPKits/kit.yml");
            yaml.dump(kit, writer);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void loadKit(Player player) {
        Yaml yaml = new Yaml();
        File file = new File("plugins/CordePvPKits/kit.yml");

        try {
            Map<String, Object> kit = yaml.load(new FileInputStream(file));
            Map<String, Map<String, Object>> items = (Map<String, Map<String, Object>>) kit.get("items");

            Inventory inventory = player.getInventory();
            inventory.clear();

            for (Map.Entry<String, Map<String, Object>> entry : items.entrySet()) {
                int slot = Integer.parseInt(entry.getKey());
                Map<String, Object> itemData = entry.getValue();
                String type = (String) itemData.get("type");
                int amount = (int) itemData.get("amount");
                short durability = Short.parseShort(String.valueOf(itemData.get("durability")));
                String name = String.valueOf(itemData.get("name"));
                String lore = (String) itemData.get("lore");

                Material material = Material.matchMaterial(type);
                if (material != null) {
                    ItemStack item = new ItemStack(material, amount);
                    item.setDurability(durability);
                    ItemMeta itemMeta = item.getItemMeta();
                    itemMeta.setDisplayName(name);
                    if(lore != null) {
                        itemMeta.setLore(Arrays.asList(lore.split("\\|")));
                    }
                    item.setItemMeta(itemMeta);
                    inventory.setItem(slot, item);
                }
            }

            player.updateInventory();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> tabCompletions = new ArrayList<>();
        if(sender.hasPermission("cordepvpkits.admin")){
            tabCompletions.add("set");
            tabCompletions.add("setspawn");
            tabCompletions.add("reload");
        }
        return tabCompletions;
    }

}
