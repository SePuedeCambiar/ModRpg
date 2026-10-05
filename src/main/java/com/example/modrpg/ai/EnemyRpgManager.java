package com.example.modrpg.ai;

import com.example.modrpg.ai.goals.TacticalBoundingGoal;
import com.example.modrpg.ai.goals.TacticalFlankGoal;
import com.example.modrpg.ai.goals.TacticalPeelGoal;
import com.example.modrpg.ai.goals.director.AmbushAssaultGoal;
import com.example.modrpg.ai.goals.director.StalkerLurkGoal;
import com.example.modrpg.ai.nemesis.NemesisHordeManager;
import com.example.modrpg.skills.PlayerSkillsProvider;
import com.example.modrpg.skills.magic.modular.CraftedSpell;
import com.example.modrpg.skills.nodes.magic.MinionHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
 * 1. Inicializa arquetipos de combate y los persiste en NBT (Fix A2).
 * 2. Equipa armaduras teñidas y armas temáticas.
 * 3. Purga metas vanilla DESPUÉS de equipar para anular reassessWeaponGoal() (Fix B1).
 * 4. Asigna miembros a escuadrones coordinados (SquadCoordinator) incluso tras recargas de chunk.
 * 5. Inyecta la jerarquía integrada de metas: F.E.A.R. + Alien: Isolation Director.
 */
public class EnemyRpgManager {

    public static final String TAG_INITIALIZED = "modrpg_enemy_initialized";
    public static final String TAG_CASTER = "modrpg_enemy_caster";

    // SPRINT 1 FIX (Bug A2): Claves para persistir la identidad del mob en disco (NBT)
    public static final String TAG_ARCHETYPE = "modrpg_archetype";
    public static final String TAG_SPELL_LEVEL = "modrpg_spell_level";

    public static void tryInitializeMob(Monster mob) {
        if (mob == null) return;

        // Ignorar esbirros aliados del jugador
        if (mob.getTags().contains(MinionHelper.TAG_MINION)) return;

        boolean alreadyInitialized = mob.getTags().contains(TAG_INITIALIZED);

        EnemyArchetype selectedArchetype = null;
        int spellPowerLevel = 1;

        if (!alreadyInitialized) {
            // =====================================================================
            // CASO A: PRIMERA VEZ (Spawn nuevo en el mundo)
            // =====================================================================
            ServerLevel level = (ServerLevel) mob.level();
            RandomSource random = mob.getRandom();

            List<EnemyArchetype> candidates = new ArrayList<>();
            for (EnemyArchetype archetype : EnemyArchetype.values()) {
                if (archetype.isCompatibleWith(mob.getType())) {
                    candidates.add(archetype);
                }
            }
            if (candidates.isEmpty()) return;

            float power = calculatePowerRating(level, mob);

            boolean isHordeMember = mob.getTags().contains(NemesisHordeManager.TAG_NEMESIS_SQUAD);
            float casterChance = Math.min(0.65f, 0.20f + (power * 0.01f));

            if (!isHordeMember && random.nextFloat() > casterChance) {
                return;
            }

            selectedArchetype = selectArchetypeForPower(candidates, power, random);

            if (power >= 40.0f) spellPowerLevel = 3 + random.nextInt(3);
            else if (power >= 20.0f) spellPowerLevel = 2;

            // SPRINT 1 FIX (A2): Guardar en NBT para sobrevivir a recargas de chunks
            mob.addTag(TAG_INITIALIZED);
            mob.addTag(TAG_CASTER);
            mob.getPersistentData().putString(TAG_ARCHETYPE, selectedArchetype.name());
            mob.getPersistentData().putInt(TAG_SPELL_LEVEL, spellPowerLevel);

            // SPRINT 1 FIX (B1): Equipar al mob PRIMERO (esto puede disparar reassessWeaponGoal en esqueletos)
            applyVisualsAndEquipment(mob, selectedArchetype);

        } else {
            // =====================================================================
            // CASO B: RECARGA DE CHUNK (El mob ya existía y fue cargado de disco)
            // =====================================================================
            if (mob.getPersistentData().contains(TAG_ARCHETYPE)) {
                try {
                    selectedArchetype = EnemyArchetype.valueOf(mob.getPersistentData().getString(TAG_ARCHETYPE));
                    spellPowerLevel = mob.getPersistentData().getInt(TAG_SPELL_LEVEL);
                } catch (IllegalArgumentException e) {
                    return;
                }
            } else {
                return; // Mob vanilla o sin datos válidos
            }
        }

        // SPRINT 1 FIX (A2 y B1): Re-acoplar el escuadrón y las metas tácticas (se ejecuta siempre)
        attachBehaviorAndSquad(mob, selectedArchetype, spellPowerLevel);
    }

    /**
     * Aplica el equipamiento, tintado de armadura y atributos base.
     */
    public static void applyVisualsAndEquipment(Monster mob, EnemyArchetype selectedArchetype) {
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
    }

    /**
     * SPRINT 1 FIX (Bugs A2 y B1):
     * 1. Purga las metas vanilla DESPUÉS de haber equipado las armas (destruye reassessWeaponGoal()).
     * 2. Re-asigna o recupera el escuadrón táctico.
     * 3. Inyecta la jerarquía de metas custom tanto en spawns nuevos como tras recargas de chunk.
     */
    public static void attachBehaviorAndSquad(Monster mob, EnemyArchetype selectedArchetype, int spellPowerLevel) {
        if (mob == null || selectedArchetype == null) return;

        // B1 FIX: Purgar metas vanilla DESPUÉS del equipamiento para neutralizar reassessWeaponGoal()
        purgeVanillaAttackGoals(mob);

        // A2 FIX: Registrar o reasociar en el escuadrón táctico
        if (SquadCoordinator.getSquadFor(mob) == null) {
            SquadCoordinator.assignToSquad(mob);
        }
        SquadCoordinator.Squad squad = SquadCoordinator.getSquadFor(mob);

        // Ascenso a Campeón / Líder (solo en nuevos y si el escuadrón no tiene líder)
        if (!mob.getTags().contains("modrpg_champion") && squad != null && squad.leaderUUID == null && !mob.level().isClientSide()) {
            ServerLevel level = (ServerLevel) mob.level();
            float power = calculatePowerRating(level, mob);
            float championChance = Math.min(0.20f, 0.08f + (power * 0.003f));
            if (mob.getRandom().nextFloat() < championChance) {
                ChampionAffix[] affixes = new ChampionAffix[]{
                        ChampionAffix.COMMANDER,
                        ChampionAffix.RUNIC_SHIELD,
                        ChampionAffix.VAMPIRIC,
                        ChampionAffix.MANA_BURN
                };
                ChampionAffix affix = affixes[mob.getRandom().nextInt(affixes.length)];
                affix.applyModifiers(mob);
                squad.leaderUUID = mob.getUUID();
            }
        }

        CraftedSpell spell = selectedArchetype.buildSpell(spellPowerLevel);

        // Inyección idempotente de metas de IA
        if (selectedArchetype.isAggressiveRush()) {
            mob.goalSelector.addGoal(1, new TacticalPeelGoal(mob, selectedArchetype));
            mob.goalSelector.addGoal(2, new AmbushAssaultGoal(mob, selectedArchetype));
        } else {
            mob.goalSelector.addGoal(3, new StalkerLurkGoal(mob, selectedArchetype));
            if (selectedArchetype.getMainHandItem() == Items.BOW) {
                mob.goalSelector.addGoal(4, new TacticalBoundingGoal(mob, selectedArchetype));
            }
            mob.goalSelector.addGoal(5, new TacticalFlankGoal(mob, selectedArchetype));
        }

        mob.goalSelector.addGoal(6, new TacticalCasterGoal(mob, selectedArchetype, spell));
    }

    /**
     * Elimina metas vanilla de ataque para que no compitan con la IA táctica del mod.
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
            int[] totalLevels = new int[1];
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