package com.example.modrpg.skills.nodes.melee;

import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

public class UnarmedStyleSkill extends SkillNode {

    public UnarmedStyleSkill() {
        super(
                SkillRegistry.NODE_UNARMED_STYLE,
                SkillRegistry.BRANCH_MELEE,
                Component.literal("Estilo de Lucha (Desarmado)"),
                Component.literal("+10% de daño al pelear con los puños limpios. Las armas equipadas hacen -5% de daño."),
                NodeType.PASSIVE_STAT,
                0
        );
    }

    @Override
    public void onLivingHurt(ServerPlayer player, LivingHurtEvent event, PlayerSkills skills) {
        if (event.getSource().getDirectEntity() != player) return;

        boolean isUnarmed = player.getMainHandItem().isEmpty();

        if (isUnarmed) {
            // +10% de daño a puño limpio
            event.setAmount(event.getAmount() * 1.10f);
            ServerLevel level = (ServerLevel) player.level();
            level.sendParticles(ParticleTypes.CRIT, event.getEntity().getX(), event.getEntity().getY() + 1.0, event.getEntity().getZ(), 8, 0.2, 0.2, 0.2, 0.1);
        } else {
            // -5% de daño si usa un arma
            event.setAmount(event.getAmount() * 0.95f);
        }
    }
}