package com.example.modrpg.ai;

import com.example.modrpg.ai.goals.TacticalBoundingGoal;
import com.example.modrpg.ai.goals.TacticalFlankGoal;
import com.example.modrpg.ai.goals.TacticalPeelGoal;
import com.example.modrpg.ai.goals.director.AmbushAssaultGoal;
import com.example.modrpg.ai.goals.director.StalkerLurkGoal;
import com.example.modrpg.skills.PlayerSkillsProvider;
import com.example.modrpg.skills.magic.modular.CraftedSpell;
import com.example.modrpg.skills.nodes.magic.MinionHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Administrador central de la IA hostil:
 * 1. Inicializa arquetipos de combate (Sniper, Juggernaut, Necromancer, etc.).
 * 2. Equipa armaduras teñidas y armas temáticas.
 * 3. Asigna miembros a escuadrones coordinados (SquadCoordinator).
 * 4. Asciende líderes campeones con afijos únicos.
 * 5. Inyecta la jerarquía integrada de metas: F.E.A.R. + Alien: Isolation Director.
 */
public class EnemyRpgManager {

    public static final String TAG_INITIALIZED = "modrpg_enemy_initialized";
    public static final String TAG_CASTER = "modrpg_enemy_caster";

    public static void tryInitializeMob(Monster mob) {
        if (mob == null || mob.getTags().contains(TAG_INITIALIZED)) return;

        // SPRINT 3 FIX (Bug C-06): Si la entidad es un esbirro aliado del jugador, ignorar por completo
        if (mob.getTags().contains(MinionHelper.TAG_MINION)) return;

        mob.addTag(TAG_INITIALIZED);

        ServerLevel level = (ServerLevel) mob.level();
        RandomSource random = mob.getRandom();

        // =========================================================================
        // 1. FILTRADO DE ARQUETIPOS COMPATIBLES CON EL TIPO DE MOB
        // =========================================================================
        List<EnemyArchetype> candidates = new ArrayList<>();
        for (EnemyArchetype archetype : EnemyArchetype.values()) {
            if (archetype.isCompatibleWith(mob.getType())) {
                candidates.add(archetype);
            }
        }

        if (candidates.isEmpty()) return;

        // =========================================================================
        // 2. CÁLCULO DE PODER DEL ENTORNO (DÍAS + PROGRESIÓN DE JUGADORES)
        // =========================================================================
        float power = calculatePowerRating(level, mob);

        // =========================================================================
        // 3. SELECCIÓN DE ARQUETIPO Y NIVEL DE HECHIZO
        // =========================================================================
        float casterChance = Math.min(0.65f, 0.20f + (power * 0.01f));
        if (random.nextFloat() > casterChance) return; // Mantiene el mob como vanilla estándar

        EnemyArchetype selectedArchetype = selectArchetypeForPower(candidates, power, random);

        int spellPowerLevel = 1;
        if (power >= 40.0f) spellPowerLevel = 3 + random.nextInt(3);
        else if (power >= 20.0f) spellPowerLevel = 2;

        CraftedSpell spell = selectedArchetype.buildSpell(spellPowerLevel);
        mob.addTag(TAG_CASTER);

        // SPRINT 3 FIX: Purgar metas vanilla de ataque para que no colisionen con las tácticas F.E.A.R.
        purgeVanillaAttackGoals(mob);

        // =========================================================================
        // 4. EQUIPAMIENTO, TINTADO Y SILUETA VISUAL LEGIBLE
        // =========================================================================
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

        mob.setCustomName(Component.literal(selectedArchetype.getColorCode() + "§l" + selectedArchetype.getDisplayName()));
        mob.setCustomNameVisible(false);

        // Modificadores de vanguardia pesada
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
        // 6. ASCENSO A CAMPEÓN / LÍDER (Probabilidad 8% - 20% según el poder)
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
        // 7. INYECCIÓN DE LA JERARQUÍA DE METAS (GOALS) INTEGRADA
        // =========================================================================
        // Prioridad 1: Rescate de Emergencia (Peeling). Solo tanques; interrumpe todo para salvar al caster.
        if (selectedArchetype.isAggressiveRush()) {
            mob.goalSelector.addGoal(1, new TacticalPeelGoal(mob, selectedArchetype));
        }

        // Prioridad 2: Asalto en Emboscada. Se activa cuando el Director da luz verde (cierra salidas y pre-avisa).
        mob.goalSelector.addGoal(2, new AmbushAssaultGoal(mob, selectedArchetype));

        // Prioridad 3: Acecho en Sombras. Mobs que acechan fuera del campo visual y huyen si el jugador los mira fijamente.
        mob.goalSelector.addGoal(3, new StalkerLurkGoal(mob, selectedArchetype));

        // Prioridad 4: Fuego de Supresión. Los arqueros saturan al jugador para permitir el avance del tanque.
        if (!selectedArchetype.isAggressiveRush() && selectedArchetype.getMainHandItem() == Items.BOW) {
            mob.goalSelector.addGoal(4, new TacticalBoundingGoal(mob, selectedArchetype));
        }

        // Prioridad 5: Flanqueo Lateral. Asesinos y tropas rápidas buscan ángulos de 90° en el punto ciego.
        if (!selectedArchetype.isAggressiveRush()) {
            mob.goalSelector.addGoal(5, new TacticalFlankGoal(mob, selectedArchetype));
        }

        // Prioridad 6: Combate Táctico F.E.A.R. Coberturas sin lag, canalizaciones con telegrafiado y Rompe-Postura.
        mob.goalSelector.addGoal(6, new TacticalCasterGoal(mob, selectedArchetype, spell));
    }

    /**
     * SPRINT 3 FIX: Elimina las metas vanilla de ataque para que no compitan con la IA F.E.A.R.
     */
    private static void purgeVanillaAttackGoals(Monster mob) {
        mob.goalSelector.removeAllGoals(goal ->
                goal instanceof MeleeAttackGoal
                        || goal instanceof RangedBowAttackGoal
                        || goal instanceof RangedAttackGoal
                        || goal instanceof RangedCrossbowAttackGoal
        );
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
            int[] totalLevels = new int[1]; // SPRINT 3 FIX: Evita instanciar AtomicInteger innecesario
            for (ServerPlayer player : nearbyPlayers) {
                player.getCapability(PlayerSkillsProvider.PLAYER_SKILLS).ifPresent(skills -> {
                    skills.getAllBranchLevels().values().forEach(lvl -> totalLevels[0] += lvl);
                });
            }
            playerPower = (float) totalLevels[0] / nearbyPlayers.size() * 0.5f;
        }

        return dayPower + playerPower;
    }
}