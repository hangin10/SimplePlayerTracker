package org.main.playerTracker;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World; // 여기 추가
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

public class PlayerTracker extends JavaPlugin implements Listener {

    // 플러그인 활성화 시 호출
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        this.getCommand("tracker").setExecutor(this::onCommand);

        // 커스텀 조합법 등록
        registerCustomRecipe();

        getLogger().info("PlayerTracker has been enabled.");
    }

    // 플러그인 비활성화 시 호출
    @Override
    public void onDisable() {
        getLogger().info("PlayerTracker has been disabled.");
    }

    // 1. 추적기 나침반 생성
    private static ItemStack createTrackerCompass() {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§l위치추적기");
            meta.setLore(List.of("§7추적기를 우클릭하여 상대방의 위치를 추적하세요!"));
            // 인첸트 효과 추가 - 기본 효과는 없지만 보라색 시각 효과를 보여줍니다.
            meta.addEnchant(Enchantment.UNBREAKING, 1, true); // "LUCK"은 예시
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS); // 툴팁에서 인첸트 숨기기
            meta.setUnbreakable(true);
            compass.setItemMeta(meta);
        }
        return compass;
    }

    // 2. 추적기 나침반 조합법 등록
    private void registerCustomRecipe() {
        ItemStack trackerCompass = createTrackerCompass();
        NamespacedKey recipeKey = new NamespacedKey(this, "tracker_compass");

        ShapedRecipe trackerRecipe = new ShapedRecipe(recipeKey, trackerCompass);
        trackerRecipe.shape(" I ", "IDI", " I "); // 조합법 모양
        trackerRecipe.setIngredient('I', Material.IRON_INGOT);
        trackerRecipe.setIngredient('D', Material.DIAMOND);

        getServer().addRecipe(trackerRecipe);
    }

    // 3. 명령어 처리 (/tracker)
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("tracker")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;

                // OP 여부를 확인
                if (!player.isOp()) {
                    player.sendMessage("§c이 명령어는 OP 권한이 있어야 사용할 수 있습니다.");
                    return true; // 명령어 처리 종료
                }

                ItemStack tracker = createTrackerCompass();
                player.getInventory().addItem(tracker);
                player.sendMessage("§6위치추적기§a를 지급하였습니다!");
                return true;
            } else {
                sender.sendMessage("§cOnly players can use this command.");
                return true;
            }
        }
        return false;
    }

    // 4. 나침반 우클릭 이벤트 처리
    @EventHandler
    public void onPlayerUseCompass(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item != null && item.hasItemMeta() && "§6§l위치추적기".equals(item.getItemMeta().getDisplayName())) {
            // GUI 열기
            openTrackingGUI(player);

            // 파티클 효과 및 사운드
            player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, player.getLocation(), 30, 0.5, 1, 0.5, 0);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0F, 1.0F);

            // 나침반 소모
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        }
    }

    // 5. 플레이어 목록 GUI 열기
    private void openTrackingGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, "§6추적할 대상을 선택하세요");

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (!target.equals(player)) {
                ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) playerHead.getItemMeta();
                if (meta != null) {
                    meta.setOwningPlayer(target);
                    meta.setDisplayName("§e" + target.getName());
                    playerHead.setItemMeta(meta);
                }
                gui.addItem(playerHead);
            }
        }

        player.openInventory(gui);
    }

    // 6. GUI 클릭 이벤트 처리
    @EventHandler
    public void onGUIClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getClickedInventory();

        if (inventory != null && event.getView().getTitle().equals("§6추적할 대상을 선택하세요")) {
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() != Material.PLAYER_HEAD) return;

            String targetName = clickedItem.getItemMeta().getDisplayName().substring(2);
            Player target = Bukkit.getPlayerExact(targetName);

            if (target != null) {
                checkNearbyPlayers(player);
                displayParticleEffectWithSound(player, target.getLocation()); // 파티클 효과
                player.closeInventory();
                player.sendMessage("§a당신은 §e" + target.getName() + "§a을(를) 추적하였습니다!");
            } else {
                player.sendMessage("§c플레이어를 찾을 수 없습니다.");
            }

            event.setCancelled(true);
        }
    }

    // 7. 주변 플레이어 확인
    private void checkNearbyPlayers(Player player) {
        boolean foundNearby = false;

        for (Player nearby : player.getWorld().getPlayers()) {
            if (nearby.equals(player)) continue; // 자신은 제외

            double distance = nearby.getLocation().distance(player.getLocation());
            double yDifference = Math.abs(nearby.getLocation().getY() - player.getLocation().getY());

            // 50블록 거리 && Y좌표 차이 20 이내
            if (distance <= 50 && yDifference <= 20) {
                player.sendTitle("§4긴급!", "§c아주 가까운 거리의 플레이어를 발견!", 10, 70, 20);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0F, 0.8F);

                // 폭죽 파티클 효과 및 소리 추가
                launchTrackingParticle(player, nearby.getLocation()); // 커스텀 파티클 효과 실행

                nearby.sendTitle("§4경고!", "§c매우 가까운 거리에서 당신을 추적중입니다!", 10, 70, 20);
                nearby.playSound(nearby.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0F, 0.8F);
                foundNearby = true;
                break;
            }
        }

        // 기존 조건 처리
        if (!foundNearby) {
            // 기존 로직: 75 블록 이하, 150 블록 이하 경고 처리
            for (Player nearby : player.getWorld().getPlayers()) {
                double distance = nearby.getLocation().distance(player.getLocation());

                if (!nearby.equals(player) && distance <= 75) {
                    foundNearby = true;
                    player.sendTitle("§c위험!", "§e적이 매우 가까이 있습니다!", 10, 70, 20);
                    player.playSound(player.getLocation(), Sound.BLOCK_BELL_RESONATE, 1.0F, 1.0F);

                    nearby.sendMessage("§6[알림] §c주변에서 당신을 추적 중입니다!");
                    nearby.playSound(nearby.getLocation(), Sound.BLOCK_BELL_RESONATE, 1.0F, 1.0F);
                    break;
                }
            }
        }

        // 기존 조건 처리
        if (!foundNearby) {
            for (Player nearby : player.getWorld().getPlayers()) {
                double distance = nearby.getLocation().distance(player.getLocation());

                if (!nearby.equals(player) && distance <= 150) {
                    player.sendTitle("§c경고!", "§e주변에 플레이어가 있습니다!", 10, 70, 20);
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.2F);
                    break;
                }
            }
        }
    }

    // 9. 파티클 효과 및 폭죽 소리 추가
    private void launchTrackingParticle(Player player, Location targetLocation) {
        Location playerLocation = player.getLocation().add(0, 1, 0); // 플레이어의 위치(약간 위로 상승)
        Location currentLocation = playerLocation.clone(); // 현재 파티클 위치 추적
        World world = player.getWorld();

        // 비동기적으로 작업 수행
        new BukkitRunnable() {
            double yOffset = 0; // Y축 이동 조정 변수
            boolean reachingTarget = false; // 목표 플레이어로 진행 여부 체크

            @Override
            public void run() {
                if (!reachingTarget) {
                    //플레이어 위로 10 블록 올라감
                    if (yOffset < 10) { //기본값(10)
                        yOffset += 0.5; // 위로 0.5블록씩 이동
                        currentLocation.add(0, 0.5, 0);
                        world.spawnParticle(Particle.FIREWORK, currentLocation, 5, 0.2, 0.2, 0.2, 0.01);
                    } else {
                        reachingTarget = true; // 2단계로 전환
                    }
                } else {
                    //목표 플레이어 쪽으로 이동
                    Vector direction = targetLocation.clone().subtract(currentLocation).toVector().normalize(); // 방향 계산
                    currentLocation.add(direction.multiply(0.8)); // 방향 이동
                    world.spawnParticle(Particle.FIREWORK, currentLocation, 1, 0.2, 0.2, 0.2, 0.01);

                    // 목표 도달 확인
                    if (currentLocation.distance(targetLocation) < 1) {
                        // 폭죽과 같은 효과 연출
                        world.spawnParticle(Particle.EXPLOSION, targetLocation, 10);
                        world.spawnParticle(Particle.FIREWORK, targetLocation, 20, 1, 1, 1, 0.2);
                        world.spawnParticle(Particle.FLASH, targetLocation, 1);
                        world.playSound(targetLocation, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0F, 1.0F);
                        this.cancel(); // 반복 종료
                    }
                }
            }
        }.runTaskTimer(this, 0, 0);
    }

    // 8. 엔드로드 파티클과 사운드 효과
    private void displayParticleEffectWithSound(Player player, Location targetLocation) {
        final Location playerLocation = player.getLocation();
        if (playerLocation == null || targetLocation == null) {
            player.sendMessage("§cError: 대상이나 위치를 찾을 수 없습니다.");
            return;
        }

        final double directionX = targetLocation.getX() - playerLocation.getX();
        final double directionY = targetLocation.getY() - playerLocation.getY();
        final double directionZ = targetLocation.getZ() - playerLocation.getZ();
        final double totalLength = Math.sqrt(directionX * directionX + directionY * directionY + directionZ * directionZ);

        if (totalLength == 0) {
            player.sendMessage("§cError: 당신은 이미 목표 위치에 있습니다!"); //이건 나도 뭔지 모르겠다
            return;
        }

        final double normalizedX = directionX / totalLength;
        final double normalizedY = directionY / totalLength;
        final double normalizedZ = directionZ / totalLength;

        final double MAX_DISTANCE = 13;
        final double STEP = 0.8;
        final double SPIRAL_RADIUS = 0.5;
        final double PARTICLE_DENSITY = 0.05;
        final double[] spiralAngle = {0};
        final double[] currentDistance = {0};

        new BukkitRunnable() {
            @Override
            public void run() {
                if (currentDistance[0] > MAX_DISTANCE) {
                    this.cancel();
                    return;
                }

                double particleX = playerLocation.getX() + normalizedX * currentDistance[0];
                double particleY = playerLocation.getY() + normalizedY * currentDistance[0] + 1;
                double particleZ = playerLocation.getZ() + normalizedZ * currentDistance[0];

                player.getWorld().spawnParticle(Particle.END_ROD, particleX, particleY, particleZ, 1, 0, 0, 0, 0);

                for (int i = 0; i < 2; i++) {
                    double angle = spiralAngle[0] + (Math.PI * i);
                    double offsetX = Math.cos(angle) * SPIRAL_RADIUS;
                    double offsetZ = Math.sin(angle) * SPIRAL_RADIUS;

                    player.getWorld().spawnParticle(Particle.DRAGON_BREATH,
                            particleX + offsetX, particleY, particleZ + offsetZ, 1, 0, 0, 0, PARTICLE_DENSITY);
                }
                // 3. 플레이어 주변 효과
                player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation(), 30, 1, 1, 1, 0.1);

                // 4. 효과음 - 기존 효과음
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5F, 1.8F);

                spiralAngle[0] += Math.PI / 8;
                currentDistance[0] += STEP;
            }
        }.runTaskTimer(this, 0, 1);

        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7F, 1.5F);
    }
}