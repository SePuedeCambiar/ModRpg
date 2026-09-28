package com.example.modrpg.ai;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum ChampionAffix {
    NONE("", ""),
    COMMANDER("§6[Comandante]", "modrpg_affix_commander"),
    RUNIC_SHIELD("§b[Escudo Rúnico]", "modrpg_affix_runic_shield"),
    VAMPIRIC("§c[Vampírico]", "modrpg_affix_vampiric"),
    MANA_BURN("§9[Quemador de Maná]", "modrpg_affix_mana_burn");

    private final String prefix;
    private final String tag;

    ChampionAffix(String prefix, String tag) {
        this.prefix = prefix;
        this.tag = tag;
    }

    public String getPrefix() { return prefix; }
    public String getTag() { return tag; }

    public void applyModifiers(Monster mob) {
        if (this == NONE) return;

        mob.addTag("modrpg_champion");
        mob.addTag(this.tag);

        // Bonificación de vida de élite (+35%)
        var hpAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.setBaseValue(hpAttr.getBaseValue() * 1.35);
            mob.setHealth(mob.getMaxHealth());
        }

        // Equipamiento y señales visuales específicas
        switch (this) {
            case COMMANDER -> {
                mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
                mob.setDropChance(EquipmentSlot.HEAD, 0.05f);
                var kb = mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
                if (kb != null) kb.setBaseValue(kb.getBaseValue() + 0.3);
            }
            case RUNIC_SHIELD -> {
                mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                mob.setDropChance(EquipmentSlot.OFFHAND, 0.05f);
            }
            case VAMPIRIC -> {
                var dmg = mob.getAttribute(Attributes.ATTACK_DAMAGE);
                if (dmg != null) dmg.setBaseValue(dmg.getBaseValue() * 1.20);
            }
            case MANA_BURN -> {
                mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.AMETHYST_SHARD));
            }
        }

        // Título sobre la cabeza
        String currentName = mob.getCustomName() != null ? mob.getCustomName().getString() : mob.getName().getString();
        mob.setCustomName(Component.literal(this.prefix + " §l" + currentName));
        mob.setCustomNameVisible(true); // Los campeones son visibles de lejos
    }

    public static void emitCommanderAura(Monster commander, ServerLevel level) {
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, commander.getX(), commander.getY() + 2.0, commander.getZ(), 3, 0.3, 0.3, 0.3, 0.05);
    }
}