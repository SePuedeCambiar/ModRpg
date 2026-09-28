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

        // 1. Filtrar arquetipos compatibles con la especie de este monstruo
        List<EnemyArchetype> candidates = new ArrayList<>();
        for (EnemyArchetype archetype : EnemyArchetype.values()) {
            if (archetype.isCompatibleWith(mob.getType())) {
                candidates.add(archetype);
            }
        }

        // Si la criatura no tiene ningún arquetipo compatible registrado, se queda como mob vanilla
        if (candidates.isEmpty()) return;

        // 2. Calcular Nivel de Amenaza del Entorno
        float power = calculatePowerRating(level, mob);

        // 3. Probabilidad de ascender a Caster (del 20% al 65% según el poder)
        float casterChance = Math.min(0.65f, 0.20f + (power * 0.01f));
        if (random.nextFloat() > casterChance) return;

        // 4. Seleccionar Arquetipo
        EnemyArchetype selectedArchetype = selectArchetypeForPower(candidates, power, random);

        // 5. Escalar nivel de poder del hechizo (1 a 5)
        int spellPowerLevel = 1;
        if (power >= 40.0f) spellPowerLevel = 3 + random.nextInt(3);
        else if (power >= 20.0f) spellPowerLevel = 2;

        CraftedSpell spell = selectedArchetype.buildSpell(spellPowerLevel);
        mob.addTag(TAG_CASTER);

        // =========================================================================
        // 6. EQUIPAMIENTO Y SEÑALIZACIÓN VISUAL (Reconocimiento a simple vista)
        // =========================================================================
        // Arma principal sugerida por el arquetipo
        mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(selectedArchetype.getMainHandItem()));
        mob.setDropChance(EquipmentSlot.MAINHAND, 0.02f);

        // Icono elemental en mano secundaria
        ItemStack elementItem = new ItemStack(selectedArchetype.getElement().getIconItem());
        mob.setItemSlot(EquipmentSlot.OFFHAND, elementItem);
        mob.setDropChance(EquipmentSlot.OFFHAND, 0.05f);

        // Casco y Peto de cuero teñidos del color distintivo del arquetipo
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

        // Nombre visible al apuntar de cerca
        mob.setCustomName(Component.literal(selectedArchetype.getColorCode() + "§l" + selectedArchetype.getDisplayName()));
        mob.setCustomNameVisible(false);

        // =========================================================================
        // 7. BUFFS DE ATRIBUTOS PARA ROLES ESPECÍFICOS
        // =========================================================================
        if (selectedArchetype.isAggressiveRush()) {
            // El Bruto Ígneo recibe más vida y resistencia a empuje para aguantar la carga
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
        // 8. INYECCIÓN DE LA IA TÁCTICA
        // =========================================================================
        mob.goalSelector.addGoal(1, new TacticalCasterGoal(mob, selectedArchetype, spell));
    }

    private static EnemyArchetype selectArchetypeForPower(List<EnemyArchetype> candidates, float power, RandomSource random) {
        // Si es esqueleto y hay alto poder, mayor probabilidad de ser Nigromante
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