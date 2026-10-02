package com.example.modrpg.ai.nemesis;

import com.example.modrpg.ai.EnemyArchetype;
import com.example.modrpg.ai.EnemyRpgManager;
import com.example.modrpg.ai.SquadCoordinator;
import com.example.modrpg.ai.director.DirectorState;
import com.example.modrpg.ai.director.MacroDirectorManager;
import com.example.modrpg.skills.PlayerSkillsProvider;
import com.example.modrpg.skills.SkillEconomy;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orquesta la economía de hordas en el mundo Hardcore de 100 días.
 * - SPRINT 1: Aislamiento por dimensión y limpieza de memoria en desconexión.
 * - SPRINT 4: Generación volumétrica 3D adaptada a Cuevas y Nether.
 * - SPRINT 4 FIX: Asignación inmediata de Target, rango de seguimiento de 48m y activación del combate.
 */
public class NemesisHordeManager {

    public static final String TAG_NEMESIS_SQUAD = "modrpg_nemesis_squad";

    // Registra el último tick en el que se lanzó una incursión a cada jugador (cooldown de 2 a 3 días)
    private static final Map<UUID, Long> LAST_RAID_TICK = new ConcurrentHashMap<>();

    public static void clearPlayer(UUID playerUUID) {
        if (playerUUID != null) {
            LAST_RAID_TICK.remove(playerUUID);
        }
    }

    public static boolean isNemesisRaidActiveNear(ServerPlayer player) {
        if (player == null || player.level().isClientSide()) return false;

        AABB box = player.getBoundingBox().inflate(48.0);
        List<Monster> activeNemesisMobs = player.serverLevel().getEntitiesOfClass(
                Monster.class, box, m -> m.getTags().contains(TAG_NEMESIS_SQUAD) && m.isAlive()
        );
        return !activeNemesisMobs.isEmpty();
    }

    public static void tryTriggerNemesisRaid(ServerLevel level, ServerPlayer player) {
        if (level == null || player == null || !player.isAlive() || isNemesisRaidActiveNear(player)) return;

        long currentTick = level.getGameTime();
        long lastRaid = LAST_RAID_TICK.getOrDefault(player.getUUID(), -72000L);

        // Intervalo mínimo de 2 días de Minecraft (48,000 ticks) entre incursiones
        if (currentTick - lastRaid < 48000L) {
            return;
        }

        LAST_RAID_TICK.put(player.getUUID(), currentTick);
        spawnTacticalHorde(level, player);
    }

    /**
     * Genera la escuadra táctica de tamaño controlado en un punto ciego exterior o subterráneo.
     */
    public static void spawnTacticalHorde(ServerLevel level, ServerPlayer player) {
        int hordeSize = HordeComposition.calculateHordeSize(level.getDayTime());
        List<EnemyArchetype> archetypes = HordeComposition.buildSquadArchetypes(hordeSize);
        RandomSource random = player.getRandom();

        // 1. Localizar un punto ancla seguro en el entorno 3D real del jugador
        BlockPos anchorGroundPos = findSafeHordeSpawnPosition(level, player, random);
        if (anchorGroundPos == null) {
            LAST_RAID_TICK.put(player.getUUID(), level.getGameTime() - 42000L);
            return;
        }

        // 2. Obtener o generar los datos del Capitán Némesis
        long day = level.getDayTime() / 24000L;
        NemesisCaptain captain = null;
        var nemesisData = NemesisSavedData.get(level);

        if (day >= 15) {
            var waitingList = nemesisData.getCaptainsWaitingRevenge();
            if (!waitingList.isEmpty()) {
                captain = waitingList.get(0);
                captain.setStatus(NemesisCaptain.Status.STALKING);
                nemesisData.addOrUpdateCaptain(captain);
            } else {
                var dominantStyle = PlayerCombatProfiler.getDominantStyle(player);
                String name = NemesisPersonalityEngine.generateProceduralName(random);
                String title = NemesisPersonalityEngine.assignReactiveTitle(dominantStyle, null, random);
                NemesisTrait trait = NemesisTrait.selectCounterForStyle(dominantStyle);

                captain = new NemesisCaptain(
                        UUID.randomUUID(), name, title, trait.getId(), 50, (int) day,
                        NemesisCaptain.Status.STALKING, "NONE"
                );
                nemesisData.addOrUpdateCaptain(captain);
            }
        }

        SquadCoordinator.Squad unifiedSquad = null;

        // 3. Instanciar los miembros de la escuadra
        for (int i = 0; i < hordeSize; i++) {
            EnemyArchetype archetype = archetypes.get(i);
            Monster mob = createMobForArchetype(level, archetype);
            if (mob == null) continue;

            // Offset horizontal disperso
            double offsetX = (random.nextDouble() - 0.5) * 4.0;
            double offsetZ = (random.nextDouble() - 0.5) * 4.0;
            int mobX = (int) Math.round(anchorGroundPos.getX() + offsetX);
            int mobZ = (int) Math.round(anchorGroundPos.getZ() + offsetZ);

            BlockPos mobSpawnPos = findLocalGround(level, mobX, anchorGroundPos.getY(), mobZ);
            if (mobSpawnPos == null) {
                mobSpawnPos = anchorGroundPos;
            }

            mob.moveTo(mobSpawnPos.getX() + 0.5, mobSpawnPos.getY(), mobSpawnPos.getZ() + 0.5,
                    random.nextFloat() * 360.0f, 0.0f);

            // SPRINT 4 FIX: Ampliar visión a 48 bloques para que detecten al jugador desde la distancia de spawn
            var followAttr = mob.getAttribute(Attributes.FOLLOW_RANGE);
            if (followAttr != null && followAttr.getBaseValue() < 48.0) {
                followAttr.setBaseValue(48.0);
            }

            // Asignar al jugador como objetivo hostil de inmediato
            mob.setTarget(player);

            mob.addTag(TAG_NEMESIS_SQUAD);
            level.addFreshEntity(mob); // Añadir al nivel antes de inicializar para que la detección de escuadrón funcione

            EnemyRpgManager.tryInitializeMob(mob);

            // Recuperar el escuadrón unificado creado en SquadCoordinator
            if (unifiedSquad == null) {
                unifiedSquad = SquadCoordinator.getSquadFor(mob);
            }

            // Si es el primer miembro y hay capitán disponible: promover a Capitán Némesis
            if (i == 0 && captain != null) {
                promoteToNemesisCaptain(mob, captain);
                if (unifiedSquad != null) {
                    unifiedSquad.leaderUUID = mob.getUUID();
                }
            }

            level.sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getY() + 0.5, mob.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
        }

        // 4. Asegurar la bolsa de tokens del escuadrón
        if (unifiedSquad != null) {
            unifiedSquad.rebuildTokenPool();
        }

        // SPRINT 4 FIX: Activar al Director en modo Emboscada de inmediato para que coordinen el ataque
        MacroDirectorManager.getPacingData(player.getUUID()).setState(DirectorState.AMBUSH_READY, 40);

        // 5. Señal de audio ominosa al iniciar la incursión
        level.playSound(null, anchorGroundPos.getX(), anchorGroundPos.getY(), anchorGroundPos.getZ(),
                SoundEvents.RAID_HORN.get(), SoundSource.HOSTILE, 1.4f, 0.75f);

        player.displayClientMessage(
                Component.literal("§4§l⚠ ¡INCURSIÓN HOSTIL! §7Una escuadra de §c" + hordeSize + " especialistas§7 avanza hacia tu posición."),
                true
        );
    }

    /**
     * Localiza un punto de aparición 3D seguro y no visible directamente.
     */
    private static BlockPos findSafeHordeSpawnPosition(ServerLevel level, ServerPlayer player, RandomSource random) {
        boolean hasCeiling = level.dimensionType().hasCeiling();
        int playerBlockX = player.getBlockX();
        int playerBlockZ = player.getBlockZ();
        int playerSurfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, playerBlockX, playerBlockZ);

        boolean isUnderground = hasCeiling || (player.getY() < playerSurfaceY - 8) || !level.canSeeSky(player.blockPosition());

        for (int attempt = 0; attempt < 6; attempt++) {
            float spawnAngle = player.getYRot() + 110.0f + random.nextFloat() * 140.0f;
            double radians = Math.toRadians(spawnAngle);
            double spawnDist = 26.0 + random.nextDouble() * 8.0;

            int targetX = (int) (player.getX() - Math.sin(radians) * spawnDist);
            int targetZ = (int) (player.getZ() + Math.cos(radians) * spawnDist);

            BlockPos candidate = null;

            if (!isUnderground) {
                BlockPos surfacePos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(targetX, 0, targetZ));
                if (Math.abs(surfacePos.getY() - player.getY()) <= 16 && isValidSpawnFloor(level, surfacePos)) {
                    candidate = surfacePos;
                }
            }

            if (candidate == null) {
                int startY = (int) player.getY() + 6;
                int minY = Math.max(level.getMinBuildHeight() + 2, (int) player.getY() - 14);
                int maxY = hasCeiling ? Math.min(120, startY) : Math.min(level.getMaxBuildHeight() - 2, startY);

                for (int y = maxY; y >= minY; y--) {
                    BlockPos checkPos = new BlockPos(targetX, y, targetZ);
                    if (isValidSpawnFloor(level, checkPos)) {
                        candidate = checkPos;
                        break;
                    }
                }
            }

            if (candidate != null) {
                return candidate;
            }
        }

        return null;
    }

    private static BlockPos findLocalGround(ServerLevel level, int x, int anchorY, int z) {
        for (int dy = 3; dy >= -3; dy--) {
            BlockPos check = new BlockPos(x, anchorY + dy, z);
            if (isValidSpawnFloor(level, check)) {
                return check;
            }
        }
        return null;
    }

    private static boolean isValidSpawnFloor(ServerLevel level, BlockPos pos) {
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState floor = level.getBlockState(pos.below());

        if (!feet.isAir() || !head.isAir()) {
            return false;
        }

        if (!floor.blocksMotion()
                || floor.is(Blocks.LAVA)
                || floor.is(Blocks.FIRE)
                || floor.is(Blocks.MAGMA_BLOCK)
                || floor.is(Blocks.WATER)) {
            return false;
        }

        return true;
    }

    private static void promoteToNemesisCaptain(Monster mob, NemesisCaptain captain) {
        mob.addTag("modrpg_nemesis_captain");
        mob.getPersistentData().putUUID("modrpg_nemesis_uuid", captain.getCaptainUUID());
        mob.getPersistentData().putString("modrpg_nemesis_trait", captain.getCounterTrait());
        mob.setCustomName(Component.literal("§4§l" + captain.getName() + " §6§l" + captain.getTitle()));
        mob.setCustomNameVisible(true);

        mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        mob.setDropChance(EquipmentSlot.HEAD, 0.05f);

        mob.goalSelector.addGoal(0, new com.example.modrpg.ai.nemesis.goals.NemesisEscapeGoal(mob));
    }

    private static Monster createMobForArchetype(ServerLevel level, EnemyArchetype archetype) {
        return switch (archetype) {
            case CRYO_SNIPER -> EntityType.SKELETON.create(level);
            case FLAME_JUGGERNAUT -> EntityType.ZOMBIE.create(level);
            case CRYPT_NECROMANCER -> EntityType.WITHER_SKELETON.create(level);
            case VOID_WEAVER -> EntityType.SPIDER.create(level);
            case STORM_EVOKER -> EntityType.WITCH.create(level);
        };
    }

    public static void onNemesisKilled(ServerLevel level, Monster deadCaptain, ServerPlayer player) {
        if (level == null || deadCaptain == null) return;
        Vec3 pos = deadCaptain.position();

        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y + 1.0, pos.z, 50, 0.6, 0.8, 0.6, 0.2);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.5f, 1.0f);

        if (player != null) {
            player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                skills.addPractice(SkillRegistry.COUNTER_ELITE_KILLS, 25);
                SkillEconomy.checkMilestones(player, skills);
                SkillEconomy.syncSkills(player);
            });
            player.giveExperienceLevels(8);
            player.displayClientMessage(
                    Component.literal("§6§l★ ¡NÉMESIS DERROTADO! §a+25 Bajas Élite §7| §e+8 Niveles XP §7| §dBotín Legendario"),
                    false
            );
        }

        RandomSource random = (player != null) ? player.getRandom() : level.getRandom();
        ItemStack rewardItem = (random.nextFloat() < 0.40f)
                ? new ItemStack(Items.NETHERITE_INGOT)
                : new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);

        ItemEntity drop = new ItemEntity(level, pos.x, pos.y + 0.5, pos.z, rewardItem);
        level.addFreshEntity(drop);
    }
}