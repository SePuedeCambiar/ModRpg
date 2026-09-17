package com.example.modrpg.skills.nodes.defense;

import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

public class StoneSkinSkill extends SkillNode {

    public StoneSkinSkill() {
        super(
                SkillRegistry.NODE_STONE_SKIN,
                SkillRegistry.BRANCH_DEFENSE,
                Component.literal("Piel de Piedra"),
                Component.literal("Tu cuerpo se endurece como granito, reduciendo pasivamente el daño recibido en un 20%."),
                NodeType.PASSIVE_STAT,
                0
        );
    }

    @Override
    public void onLivingHurt(ServerPlayer player, LivingHurtEvent event, PlayerSkills skills) {
        // Se ejecuta si el jugador es la víctima que recibe el golpe
        if (event.getEntity() == player) {
            float reduced = event.getAmount() * 0.80f; // 20% de reducción
            event.setAmount(reduced);
        }
    }
}
