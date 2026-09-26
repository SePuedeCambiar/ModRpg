package com.example.modrpg.ai;

import com.example.modrpg.skills.PlayerSkillsProvider;
import com.example.modrpg.skills.magic.modular.CraftedSpell;
import com.example.modrpg.skills.magic.modular.SpellElement;
import com.example.modrpg.skills.magic.modular.SpellShape;
import com.example.modrpg.skills.magic.modular.SpellTiming;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class EnemyRpgManager {

    public static final String TAG_INITIALIZED = "modrpg_enemy_initialized";
    public static final String TAG_CASTER = "modrpg_enemy_caster";

    /**
     * Evalúa y equipa a los monstruos al momento de aparecer en el mundo.
     */
    public static void tryInitializeMob(Monster mob) {
        if (mob.getTags().contains(TAG_INITIALIZED)) return;
        mob.addTag(TAG_INITIALIZED);

        ServerLevel level = (ServerLevel) mob.level();
        RandomSource random = mob.getRandom();

        // 1. Calcular Poder del Entorno (Días del Mundo + Nivel de Jugadores cercanos en 64 bloques)
        float power = calculatePowerRating(level, mob);

        // 2. Probabilidad de convertirse en Caster Elemental (Escala de 15% hasta 70% según poder)
        float casterChance = Math.min(0.70f, 0.15f + (power * 0.01f));
        if (random.nextFloat() > casterChance) return; // Se queda como mob vanilla normal

        // 3. Generar Build Mágica coherente con su Poder
        CraftedSpell spell = generateSpellForPower(power, random);
        mob.addTag(TAG_CASTER);

        // 4. Identificador visual inmediato: Llevar el elemento en la mano secundaria (Off-hand)
        ItemStack indicator = new ItemStack(spell.getElement().getIconItem());
        mob.setItemSlot(EquipmentSlot.OFFHAND, indicator);
        mob.setDropChance(EquipmentSlot.OFFHAND, 0.05f); // 5% de probabilidad de drop

        // Prefijo en el nombre para avisar al jugador
        mob.setCustomName(Component.literal(spell.getElement().getColorCode() + "§l" + spell.getName() + " " + mob.getName().getString()));
        mob.setCustomNameVisible(false); // Solo visible al mirarlo de cerca

        // 5. Inyectar la IA Táctica estilo F.E.A.R.
        mob.goalSelector.addGoal(1, new TacticalCasterGoal(mob, spell));
    }

    private static float calculatePowerRating(ServerLevel level, Monster mob) {
        // Días transcurridos en el mundo
        long dayCount = level.getDayTime() / 24000L;
        float dayPower = dayCount * 1.5f;

        // Promedio de nivel de ramas de jugadores cercanos en un radio de 64 bloques
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

    private static CraftedSpell generateSpellForPower(float power, RandomSource random) {
        SpellElement element = SpellElement.values()[random.nextInt(SpellElement.values().length)];
        SpellShape shape;
        SpellTiming timing;
        int spellPowerLevel;

        if (power < 15.0f) {
            // Nivel Bajo: Proyectiles o Toques equilibrados / rápidos
            shape = random.nextBoolean() ? SpellShape.PROJECTILE : SpellShape.TOUCH;
            timing = random.nextBoolean() ? SpellTiming.BALANCED : SpellTiming.RAPID_FIRE;
            spellPowerLevel = 1;
        } else if (power < 40.0f) {
            // Nivel Medio: Rayos instantáneos o Runas en el suelo
            shape = random.nextBoolean() ? SpellShape.BEAM : SpellShape.GROUND_AOE;
            timing = SpellTiming.BALANCED;
            spellPowerLevel = 2;
        } else {
            // Nivel Alto: Detonaciones Pesadas, Auras de estallido o Rayos de Vacío
            shape = SpellShape.values()[random.nextInt(SpellShape.values().length)];
            timing = random.nextFloat() < 0.4f ? SpellTiming.HEAVY_BURST : SpellTiming.BALANCED;
            spellPowerLevel = 3 + random.nextInt(3);
        }

        String title = element.getDisplayName() + " " + shape.getDisplayName();
        return new CraftedSpell(title, element, shape, timing, spellPowerLevel);
    }
}