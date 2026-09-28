package com.example.modrpg.ai;

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

public class EnemyRpgManager {

    public static final String TAG_INITIALIZED = "modrpg_enemy_initialized";
    public static final String TAG_CASTER = "modrpg_enemy_caster";

    public static void tryInitializeMob(Monster mob) {
        if (mob.getTags().contains(TAG_INITIALIZED)) return;
        mob.addTag(TAG_INITIALIZED);

        ServerLevel level = (ServerLevel) mob.level();
        RandomSource random = mob.getRandom();

        // 1. Filtrar arquetipos compatibles
        List<EnemyArchetype> candidates = new ArrayList<>();
        for (EnemyArchetype archetype : EnemyArchetype.values()) {
            if (archetype.isCompatibleWith(mob.getType())) {
                candidates.add(archetype);
            }
        }

        if (candidates.isEmpty()) return;

        // 2. Calcular poder del entorno
        float power = calculatePowerRating(level, mob);

        // 3. Probabilidad de convertirse en Caster
        float casterChance = Math.min(0.65f, 0.20f + (power * 0.01f));
        if (random.nextFloat() > casterChance) return;

        EnemyArchetype selectedArchetype = selectArchetypeForPower(candidates, power, random);

        int spellPowerLevel = 1;
        if (power >= 40.0f) spellPowerLevel = 3 + random.nextInt(3);
        else if (power >= 20.0f) spellPowerLevel = 2;

        CraftedSpell spell = selectedArchetype.buildSpell(spellPowerLevel);
        mob.addTag(TAG_CASTER);

        // 4. Equipamiento base y tintado
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
        // 6. ASCENSO A CAMPEÓN / LÍDER (Probabilidad del 8% al 20% según el poder)
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

        // 7. Inyectar la IA Táctica
        mob.goalSelector.addGoal(1, new TacticalCasterGoal(mob, selectedArchetype, spell));
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