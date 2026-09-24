package com.example.modrpg.skills.nodes.ranged;

import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;

public class RapidFireSkill extends SkillNode {

    public static final String TAG_RAPID_FIRE = "modrpg_rapid_fire_active";

    public RapidFireSkill() {
        super(
                SkillRegistry.NODE_RAPID_FIRE,
                SkillRegistry.BRANCH_RANGED,
                Component.literal("Disparo Rápido (Ametralladora)"),
                Component.literal("Desata una lluvia de 6 flechas a velocidad máxima sin necesidad de tensar el arco."),
                NodeType.ACTIVE_ABILITY,
                300 // 15 segundos
        );
    }

    @Override
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 look = player.getLookAngle();

        // Dispara una ráfaga inmediata de flechas frontales
        for (int i = 0; i < 6; i++) {
            Arrow arrow = new Arrow(level, player);
            Vec3 spread = look.add((Math.random() - 0.5) * 0.15, (Math.random() - 0.5) * 0.15, (Math.random() - 0.5) * 0.15);
            arrow.shoot(spread.x, spread.y, spread.z, 2.8f, 1.0f);
            arrow.setBaseDamage(arrow.getBaseDamage() * 1.2);
            level.addFreshEntity(arrow);
        }

        level.sendParticles(ParticleTypes.FIREWORK, player.getX(), player.getEyeY(), player.getZ(), 15, 0.2, 0.2, 0.2, 0.05);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.4f, 1.6f);
        player.displayClientMessage(Component.literal("§b🏹 ¡RÁFAGA DE DISPARO RÁPIDO DESATADA!"), true);
    }
}