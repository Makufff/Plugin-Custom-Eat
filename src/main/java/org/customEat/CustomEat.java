package org.customEat;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

public final class CustomEat extends JavaPlugin implements Listener {
    private final List<Material> allowedFoods = new CopyOnWriteArrayList<>();
    private final Set<Material> allFoods = new HashSet<>();
    private final Set<Material> specialFoods = new HashSet<>();
    private BukkitTask randomizeTask;
    private String prefix;
    private int foodsPerWeek;
    private long nextRandomization;
    private static final long SIX_HOURS_IN_TICKS = 20L * 60L * 60L * 6L; // 6 ชั่วโมงในหน่วย tick

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            loadConfig();

            initializeSpecialFoods();
            initializeFoodList();
            randomizeWeeklyFoods();
            startRandomizeTask();

            registerCommands();

            getServer().getPluginManager().registerEvents(this, this);

            getLogger().info("Custom-Eat Plugin has been enabled!");

        } catch (Exception e) {
            getLogger().severe("เกิดข้อผิดพลาดในการเริ่มต้น plugin: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void initializeSpecialFoods() {
        specialFoods.addAll(Arrays.asList(
                // Bottles
                Material.POTION,
                Material.HONEY_BOTTLE,
                Material.SUSPICIOUS_STEW,  // Added for consistency with bottles section
                Material.MILK_BUCKET,      // Added milk bucket
                Material.OMINOUS_BOTTLE,      // Added ominous_bottle

                // Special Items
                Material.ENCHANTED_GOLDEN_APPLE,
                Material.GOLDEN_APPLE,
                Material.GOLDEN_CARROT,

                // Stews and Soups
                Material.MUSHROOM_STEW,
                Material.RABBIT_STEW,
                Material.BEETROOT_SOUP
        ));
    }

    // Rest of the code remains unchanged...
    private void registerCommands() {
        CommandExecutor eatInfoExecutor = new CommandExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(prefix + "คำสั่งนี้ใช้ได้เฉพาะผู้เล่นเท่านั้น");
                    return true;
                }

                showFoodList(player);
                return true;
            }
        };

        CommandExecutor reloadExecutor = new CommandExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (!sender.hasPermission("customeat.reload")) {
                    sender.sendMessage(prefix + getConfig().getString("messages.no-permission", "§cคุณไม่มีสิทธิ์ใช้คำสั่งนี้!"));
                    return true;
                }

                reloadPlugin(sender);
                return true;
            }
        };

        Optional.ofNullable(getCommand("eat-info")).ifPresent(cmd -> cmd.setExecutor(eatInfoExecutor));
        Optional.ofNullable(getCommand("reload-food")).ifPresent(cmd -> cmd.setExecutor(reloadExecutor));
    }

    private String formatTimeRemaining() {
        long currentTime = System.currentTimeMillis();
        long timeRemaining = nextRandomization - currentTime;

        if (timeRemaining <= 0) {
            return "กำลังจะสุ่มใหม่...";
        }

        long hours = TimeUnit.MILLISECONDS.toHours(timeRemaining);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(timeRemaining) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(timeRemaining) % 60;

        StringBuilder time = new StringBuilder();
        if (hours > 0) time.append(hours).append(" ชั่วโมง ");
        if (minutes > 0) time.append(minutes).append(" นาที ");
        if (seconds > 0) time.append(seconds).append(" วินาที");

        if (time.length() == 0) {
            time.append("น้อยกว่า 1 วินาที");
        }

        return time.toString().trim();
    }

    private void showFoodList(Player player) {
        player.sendMessage("\n§d=== เวลาที่เหลือก่อนสุ่มใหม่ ===");
        player.sendMessage("§f" + formatTimeRemaining());

        player.sendMessage("\n§a=== รายการอาหารที่สามารถกินได้ในขณะนี้ ===");
        for (Material food : allowedFoods) {
            player.sendMessage("§f- " + food.name().toLowerCase().replace('_', ' '));
        }

        player.sendMessage("\n§6=== รายการอาหารพิเศษที่สามารถกินได้ตลอด ===");
        for (Material food : specialFoods) {
            player.sendMessage("§e- " + food.name().toLowerCase().replace('_', ' '));
        }

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 0.5f, 1.0f);
    }

    private void loadConfig() {
        FileConfiguration config = getConfig();
        prefix = config.getString("messages.prefix", "§a[Custom-Eat] §f");
        foodsPerWeek = config.getInt("foods-per-week", 5);
    }

    @Override
    public void onDisable() {
        if (randomizeTask != null) {
            randomizeTask.cancel();
        }
        getLogger().info("Custom-Eat Plugin has been disabled!");
    }

    private void startRandomizeTask() {
        if (randomizeTask != null) {
            randomizeTask.cancel();
        }

        nextRandomization = System.currentTimeMillis() + (SIX_HOURS_IN_TICKS * 50); // 50ms per tick

        randomizeTask = new BukkitRunnable() {
            @Override
            public void run() {
                randomizeWeeklyFoods();
                nextRandomization = System.currentTimeMillis() + (SIX_HOURS_IN_TICKS * 50);
                Bukkit.broadcastMessage(prefix + "รายการอาหารได้ถูกสุ่มใหม่แล้ว!");
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                }
            }
        }.runTaskTimer(this, SIX_HOURS_IN_TICKS, SIX_HOURS_IN_TICKS);
    }

    private void initializeFoodList() {
        allFoods.clear();
        allFoods.addAll(Arrays.asList(
                Material.APPLE,
                Material.BREAD,
                Material.COOKED_BEEF,
                Material.COOKED_CHICKEN,
                Material.COOKED_MUTTON,
                Material.COOKED_PORKCHOP,
                Material.BAKED_POTATO,
                Material.COOKIE,
                Material.PUMPKIN_PIE,
                Material.COOKED_RABBIT,
                Material.COOKED_COD,
                Material.COOKED_SALMON,
                Material.DRIED_KELP,
                Material.SWEET_BERRIES,
                Material.GLOW_BERRIES,
                Material.MELON_SLICE,
                Material.BEEF,
                Material.PORKCHOP,
                Material.MUTTON,
                Material.CHICKEN,
                Material.RABBIT,
                Material.COD,
                Material.SALMON,
                Material.TROPICAL_FISH,
                Material.ROTTEN_FLESH,
                Material.SPIDER_EYE,
                Material.CARROT,
                Material.POTATO,
                Material.BEETROOT,
                Material.PUFFERFISH
        ));
    }

    private synchronized void randomizeWeeklyFoods() {
        allowedFoods.clear();
        List<Material> foodsList = new ArrayList<>(allFoods);
        Collections.shuffle(foodsList);
        allowedFoods.addAll(foodsList.subList(0, Math.min(foodsPerWeek, foodsList.size())));
    }

    private void sendWarningMessage(Player player, Material foodType) {
        String cannotEatMessage = getConfig().getString("messages.cannot-eat", "§cคุณไม่สามารถกินอาหารชนิดนี้ได้!");
        player.sendMessage(prefix + cannotEatMessage);

        player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                new TextComponent("§c❌ ไม่สามารถกิน " + foodType.name().toLowerCase().replace('_', ' ') + " ได้ในขณะนี้ §c❌")
        );

        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);

        new BukkitRunnable() {
            @Override
            public void run() {
                player.sendMessage("§e💡 ใช้คำสั่ง §f/eat-info §eเพื่อดูรายการอาหารที่สามารถกินได้");
            }
        }.runTaskLater(this, 40);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFoodConsume(PlayerItemConsumeEvent event) {
        if (event.isCancelled()) return;

        ItemStack food = event.getItem();
        Material foodType = food.getType();
        Player player = event.getPlayer();

        if (specialFoods.contains(foodType)) {
            return;
        }

        if (!allowedFoods.contains(foodType)) {
            event.setCancelled(true);
            sendWarningMessage(player, foodType);
        }
    }

    private void reloadPlugin(CommandSender sender) {
        try {
            reloadConfig();
            loadConfig();
            initializeSpecialFoods();
            initializeFoodList();
            randomizeWeeklyFoods();
            startRandomizeTask();

            String reloadMessage = getConfig().getString("messages.reload-success", "Reload plugin สำเร็จ!");
            sender.sendMessage(prefix + reloadMessage);

            if (sender instanceof Player player) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f);
            }
        } catch (Exception e) {
            sender.sendMessage(prefix + "§cเกิดข้อผิดพลาดในการ reload plugin: " + e.getMessage());
            getLogger().severe("เกิดข้อผิดพลาดในการ reload plugin: " + e.getMessage());
            e.printStackTrace();
        }
    }
}