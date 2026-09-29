package com.example.modrpg.ai;

import com.example.modrpg.ai.goals.TacticalBoundingGoal;
import com.example.modrpg.ai.goals.TacticalFlankGoal;
import com.example.modrpg.ai.goals.TacticalPeelGoal;
import com.example.modrpg.skills.PlayerSkillsProvider;
import com.example.modrpg.skills.magic.modular.CraftedSpell;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gestor central de inicialización RPG para monstruos.
 * Transforma entidades vanilla en combatientes tácticos con arquetipos,
 * equipo distintivo por silueta, magia modular y metas coordinadas de escuadrón.
 */
public class EnemyRpgManager {

    public static final String TAG_INITIALIZED = "modrpg_enemy_initialized";
    public static final String TAG_CASTER      = "modrpg_enemy_caster";

    public static void tryInitializeMob(Monster mob) {
        if (mob.getTags().contains(TAG_INITIALIZED)) return;
        mob.addTag(TAG_INITIALIZED);

        ServerLevel level = (ServerLevel) mob.level();
        RandomSource random = mob.getRandom();

        // 1. Filtrar arquetipos compatibles según el tipo de entidad (Zombie, Skeleton, Spider, etc.)
        List<EnemyArchetype> candidates = new ArrayList<>();
        for (EnemyArchetype archetype : EnemyArchetype.values()) {
            if (archetype.isCompatibleWith(mob.getType())) {
                candidates.add(archetype);
            }
        }

        if (candidates.isEmpty()) return;

        // 2. Calcular poder del entorno (Días transcurridos + Niveles de los jugadores cercanos)
        float power = calculatePowerRating(level, mob);

        // 3. Probabilidad de ascender a Lanzador Táctico / Especialista (20% base + escala por poder)
        float casterChance = Math.min(0.65f, 0.20f + (power * 0.01f));
        if (random.nextFloat() > casterChance) return;

        EnemyArchetype selectedArchetype = selectArchetypeForPower(candidates, power, random);

        // Nivel de poder del hechizo asignado
        int spellPowerLevel = 1;
        if (power >= 40.0f) spellPowerLevel = 3 + random.nextInt(3);
        else if (power >= 20.0f) spellPowerLevel = 2;

        CraftedSpell spell = selectedArchetype.buildSpell(spellPowerLevel);
        mob.addTag(TAG_CASTER);

        // 4. Equipamiento base, siluetas distintivas y armadura de cuero tintada
        mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(selectedArchetype.getMainHandItem()));
        mob.setDropChance(EquipmentSlot.MAINHAND, 0.02f);

        ItemStack elementItem = new ItemStack(selectedArchetype.getElement().getIconItem());
        mob.setItemSlot(EquipmentSlot.OFFHAND, elementItem);
        mob.setDropChance(EquipmentSlot.OFFHAND, 0.05f);

        ItemStack helmet = new ItemStack(Items.LEATHER_HELMET);
        ItemStack chestplate = new ItemStack(Items.LEATHER_CHESTPLATE);
        if (helmet.getItem() instanceof DyeableLeatherItem dyeableHelmet) {
            dyeableHelmet.setColor(helmet, selectedArchetype.getArmorColor());
        }
        if (chestplate.getItem() instanceof DyeableLeatherItem dyeableChest) {
            dyeableChest.setColor(chestplate, selectedArchetype.getArmorColor());
        }

        mob.setItemSlot(EquipmentSlot.HEAD, helmet);
        mob.setItemSlot(EquipmentSlot.CHEST, chestplate);
        mob.setDropChance(EquipmentSlot.HEAD, 0.0f);
        mob.setDropChance(EquipmentSlot.CHEST, 0.0f);

        // Nombre táctico visible en el HUD de objetivo (ChampionOverlay)
        mob.setCustomName(Component.literal(selectedArchetype.getColorCode() + "§l" + selectedArchetype.getDisplayName()));
        mob.setCustomNameVisible(false);

        // Modificadores físicos de la Vanguardia frontal
        if (selectedArchetype.isAggressiveRush()) {
            var hpAttr = mob.getAttribute(Attributes.MAX_HEALTH);
            if (hpAttr != null) {
                hpAttr.setBaseValue(hpAttr.getBaseValue() + 15.0);
                mob.setHealth(mob.getMaxHealth());
            }
            var kbAttr = mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (kbAttr != null) {
                kbAttr.setBaseValue(kbAttr.getBaseValue() + 0.4);
            }
        }

        // =========================================================================
        // 5. ASIGNACIÓN A ESCUADRÓN TÁCTICO F.E.A.R.
        // =========================================================================
        SquadCoordinator.assignToSquad(mob);
        SquadCoordinator.Squad squad = SquadCoordinator.getSquadFor(mob);

        // =========================================================================
        // 6. ASCENSO A CAMPEÓN / LÍDER (Probabilidad del 8% al 20% según poder)
        // =========================================================================
        float championChance = Math.min(0.20f, 0.08f + (power * 0.003f));
        if (squad != null && squad.leaderUUID == null && random.nextFloat() < championChance) {
            ChampionAffix[] affixes = new ChampionAffix[]{
                    ChampionAffix.COMMANDER,
                    ChampionAffix.RUNIC_SHIELD,
                    ChampionAffix.VAMPIRIC,
                    ChampionAffix.MANA_BURN
            };
            ChampionAffix affix = affixes[random.nextInt(affixes.length)];
            affix.applyModifiers(mob);
            squad.leaderUUID = mob.getUUID();
        }

        // =========================================================================
        // 7. INYECCIÓN DEL ÁRBOL DE METAS TÁCTICAS F.E.A.R. (SPRINT 4)
        // =========================================================================
        // Prioridad 1: Maniobra de Rescate (Peeling) - Solo tanques de vanguardia
        if (selectedArchetype.isAggressiveRush()) {
            mob.goalSelector.addGoal(1, new TacticalPeelGoal(mob, selectedArchetype));
        }

        // Prioridad 2: Avance escalonado con Fuego de Supresión - Solo tiradores a distancia
        if (!selectedArchetype.isAggressiveRush() && selectedArchetype.getMainHandItem() == Items.BOW) {
            mob.goalSelector.addGoal(2, new TacticalBoundingGoal(mob, selectedArchetype));
        }

        // Prioridad 3: Flanqueo lateral cinemático hacia el punto ciego del jugador
        if (!selectedArchetype.isAggressiveRush()) {
            mob.goalSelector.addGoal(3, new TacticalFlankGoal(mob, selectedArchetype));
        }

        // Prioridad 4: Hechicería táctica, uso de coberturas y telegrafiado de castigo
        mob.goalSelector.addGoal(4, new TacticalCasterGoal(mob, selectedArchetype, spell));
    }

    private static EnemyArchetype selectArchetypeForPower(List<EnemyArchetype> candidates, float power, RandomSource random) {
        if (candidates.contains(EnemyArchetype.CRYPT_NECROMANCER) && power >= 25.0f && random.nextFloat() < 0.5f) {
            return EnemyArchetype.CRYPT_NECROMANCER;
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private static float calculatePowerRating(ServerLevel level, Monster mob) {
        long dayCount = level.getDayTime() / 24000L;
        float dayPower = dayCount * 1.5f;

        AABB searchArea = mob.getBoundingBox().inflate(64.0);
        List<ServerPlayer> nearbyPlayers = level.getEntitiesOfClass(ServerPlayer.class, searchArea);

        float playerPower = 0.0f;
        if (!nearbyPlayers.isEmpty()) {
            AtomicInteger totalLevels = new AtomicInteger();
            for (ServerPlayer player : nearbyPlayers) {
                player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                    skills.getAllBranchLevels().values().forEach(totalLevels::addAndGet);
                });
            }
            playerPower = (float) totalLevels.get() / nearbyPlayers.size() * 0.5f;
        }

        return dayPower + playerPower;
    }
}