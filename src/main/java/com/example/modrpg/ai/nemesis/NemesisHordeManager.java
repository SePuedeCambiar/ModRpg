package com.example.modrpg.ai.nemesis;

import com.example.modrpg.ai.EnemyArchetype;
import com.example.modrpg.ai.EnemyRpgManager;
import com.example.modrpg.ai.SquadCoordinator;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orquesta la economía de hordas en el mundo Hardcore de 100 días.
 * Asegura combates quirúrgicos de 3 a 10 enemigos y asigna capitanes con rencor.
 */
public class NemesisHordeManager {

    public static final String TAG_NEMESIS_SQUAD = "modrpg_nemesis_squad";

    // Registra el último tick en el que se lanzó una incursión a cada jugador (cooldown de 2 a 3 días)
    private static final Map<UUID, Long> LAST_RAID_TICK = new ConcurrentHashMap<>();

    /**
     * Comprueba si hay una incursión Némesis activa combatiendo cerca del jugador.
     */
    public static boolean isNemesisRaidActiveNear(ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(48.0);
        List<Monster> activeNemesisMobs = player.serverLevel().getEntitiesOfClass(
                Monster.class, box, m -> m.getTags().contains(TAG_NEMESIS_SQUAD) && m.isAlive()
        );
        return !activeNemesisMobs.isEmpty();
    }

    /**
     * Evalúa y despliega una incursión táctica si las condiciones del Director y del mundo se cumplen.
     */
    public static void tryTriggerNemesisRaid(ServerLevel level, ServerPlayer player) {
        if (player == null || isNemesisRaidActiveNear(player)) return;

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
     * Genera la escuadra táctica de tamaño controlado en un punto ciego exterior.
     */
    public static void spawnTacticalHorde(ServerLevel level, ServerPlayer player) {
        int hordeSize = HordeComposition.calculateHordeSize(level.getDayTime());
        List<EnemyArchetype> archetypes = HordeComposition.buildSquadArchetypes(hordeSize);
        RandomSource random = player.getRandom();

        // 1. Calcular posición de spawn táctica fuera del campo de visión (a 30-34 bloques)
        float spawnAngle = player.getYRot() + 130.0f + random.nextFloat() * 100.0f; // Detrás o en ángulo ciego
        double radians = Math.toRadians(spawnAngle);
        double spawnDist = 30.0 + random.nextDouble() * 4.0;

        int targetX = (int) (player.getX() - Math.sin(radians) * spawnDist);
        int targetZ = (int) (player.getZ() + Math.cos(radians) * spawnDist);
        BlockPos groundPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(targetX, 0, targetZ));

        // 2. Fundar el escuadrón táctico
        SquadCoordinator.Squad squad = new SquadCoordinator.Squad();

        // 3. Obtener o generar al Capitán Némesis
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
                // Generar nuevo Capitán adaptado
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

        // 4. Instanciar los miembros de la escuadra
        for (int i = 0; i < hordeSize; i++) {
            EnemyArchetype archetype = archetypes.get(i);
            Monster mob = createMobForArchetype(level, archetype);
            if (mob == null) continue;

            double offsetX = (random.nextDouble() - 0.5) * 4.0;
            double offsetZ = (random.nextDouble() - 0.5) * 4.0;
            mob.moveTo(groundPos.getX() + offsetX, groundPos.getY(), groundPos.getZ() + offsetZ, random.nextFloat() * 360.0f, 0.0f);

            mob.addTag(TAG_NEMESIS_SQUAD);
            EnemyRpgManager.tryInitializeMob(mob);

            // Si es el primer miembro y hay capitán disponible: este mob es el Capitán Némesis
            if (i == 0 && captain != null) {
                promoteToNemesisCaptain(mob, captain);
                squad.leaderUUID = mob.getUUID();
            }

            squad.memberUUIDs.add(mob.getUUID());
            level.addFreshEntity(mob);

            // Efectos de invocación táctica
            level.sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getY() + 0.5, mob.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
        }

        squad.rebuildTokenPool();

        // 5. Señal de audio ominosa al iniciar la incursión
        level.playSound(null, groundPos.getX(), groundPos.getY(), groundPos.getZ(),
                SoundEvents.RAID_HORN.get(), SoundSource.HOSTILE, 1.4f, 0.75f);
        player.displayClientMessage(
                Component.literal("§4§l⚠ ¡INCURSIÓN HOSTIL! §7Una escuadra de §c" + hordeSize + " especialistas§7 avanza hacia tu posición."),
                true
        );
    }

    private static void promoteToNemesisCaptain(Monster mob, NemesisCaptain captain) {
        mob.addTag("modrpg_nemesis_captain");
        mob.getPersistentData().putUUID("modrpg_nemesis_uuid", captain.getCaptainUUID());
        mob.getPersistentData().putString("modrpg_nemesis_trait", captain.getCounterTrait());
        mob.setCustomName(Component.literal("§4§l" + captain.getName() + " §6§l" + captain.getTitle()));
        mob.setCustomNameVisible(true);

        // Equipo visual distintivo
        mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        mob.setDropChance(EquipmentSlot.HEAD, 0.05f);

        // Meta de escape de máxima prioridad (Sprint 11)
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

    /**
     * Otorga las recompensas legendarias al derrotar a un Capitán Némesis.
     */
    public static void onNemesisKilled(ServerLevel level, Monster deadCaptain, ServerPlayer player) {
        Vec3 pos = deadCaptain.position();

        // 1. Partículas y sonido de reto legendario superado
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y + 1.0, pos.z, 50, 0.6, 0.8, 0.6, 0.2);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.5f, 1.0f);

        // 2. Progresión RPG: +25 bajas de élite para pruebas de ascensión del árbol
        player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
            skills.addPractice(SkillRegistry.COUNTER_ELITE_KILLS, 25);
            SkillEconomy.checkMilestones(player, skills);
            SkillEconomy.syncSkills(player);
        });

        // 3. Experiencia directa (8 niveles de XP)
        player.giveExperienceLevels(8);

        // 4. Botín garantizado de alta calidad (Lingote de Netherite o Manzana Dorada Encantada)
        ItemStack rewardItem = (player.getRandom().nextFloat() < 0.40f)
                ? new ItemStack(Items.NETHERITE_INGOT)
                : new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);

        ItemEntity drop = new ItemEntity(level, pos.x, pos.y + 0.5, pos.z, rewardItem);
        level.addFreshEntity(drop);

        player.displayClientMessage(
                Component.literal("§6§l★ ¡NÉMESIS DERROTADO! §a+25 Bajas Élite §7| §e+8 Niveles XP §7| §dBotín Legendario"),
                false
        );
    }
}