package com.example.modrpg.skills.nodes.melee;

import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

public class CapstoneMeleeSkill extends SkillNode {

    public CapstoneMeleeSkill() {
        super(
                SkillRegistry.NODE_CAPSTONE_MELEE,
                SkillRegistry.BRANCH_MELEE,
                Component.literal("Golpe Definitivo (500%)"),
                Component.literal("Canaliza tu poder en tu arma. Tu próximo golpe físico infligirá un 500% de daño devastador."),
                NodeType.ULTIMATE,
                6000 // 5 minutos de enfriamiento
        );
    }

    @Override
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {
        if (skills.isUltimateCharged()) {
            player.displayClientMessage(Component.literal("§e⚡ ¡Tu arma ya está cargada! Ataca a un enemigo."), true);
            return;
        }

        skills.setUltimateCharged(true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.6f, 1.8f);
        player.displayClientMessage(Component.literal("§6§l⚡ ¡GOLPE DEFINITIVO CARGADO! §e(Próximo impacto: +500% daño)"), true);
    }

    @Override
    public void onLivingHurt(ServerPlayer player, LivingHurtEvent event, PlayerSkills skills) {
        if (!skills.isUltimateCharged() || event.getSource().getDirectEntity() != player) return;

        skills.setUltimateCharged(false);
        float damageFinal = event.getAmount() * 5.0f;
        event.setAmount(damageFinal);

        LivingEntity target = event.getEntity();
        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 1.0, target.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.0, target.getZ(), 50, 0.5, 0.5, 0.5, 0.3);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 1.4f);

        player.displayClientMessage(Component.literal("§c§l💥 ¡IMPACTO CRÍTICO (500%)! §fDaño: §4§l" + String.format("%.1f", damageFinal)), true);
    }
}