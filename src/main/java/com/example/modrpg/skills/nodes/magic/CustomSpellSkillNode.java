package com.example.modrpg.skills.nodes.magic;

import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.SkillEconomy;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import com.example.modrpg.skills.magic.modular.CraftedSpell;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class CustomSpellSkillNode extends SkillNode {

    private final int slotIndex;

    public CustomSpellSkillNode(ResourceLocation id, int slotIndex) {
        super(
                id,
                SkillRegistry.BRANCH_MAGIC,
                Component.literal("Ranura Mágica " + (slotIndex + 1)),
                Component.literal("Dispara el hechizo modular guardado en tu memoria."),
                NodeType.ACTIVE_ABILITY,
                0 // El cooldown se calcula dinámicamente según el hechizo
        );
        this.slotIndex = slotIndex;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    @Override
    public float getManaCost() {
        return 0.0f; // La deducción de maná se valida dinámicamente en onExecuteActive
    }

    @Override
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {
        CraftedSpell spell = skills.getSpell(slotIndex);

        if (spell == null) {
            player.displayClientMessage(
                    Component.literal("§c[RPG] La Ranura Mágica " + (slotIndex + 1) + " está vacía. ¡Crea un hechizo con [O]!"),
                    true
            );
            return;
        }

        float manaCost = spell.calculateManaCost();
        if (skills.getCurrentMana() < manaCost) {
            player.displayClientMessage(
                    Component.literal("§9§l⚡ ¡Maná insuficiente! §7(Necesitas: §b" + (int) manaCost + "§7)"),
                    true
            );
            return;
        }

        // Deducción y cooldown del hechizo personalizado
        skills.consumeMana(manaCost);
        int cooldownTicks = spell.calculateCooldownTicks();
        skills.setCooldown(this.getId(), cooldownTicks);

        // Ejecutar casteo
        spell.cast(player, player.getLookAngle());
        SkillEconomy.syncSkills(player);
    }
}