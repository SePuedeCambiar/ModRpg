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
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.List;

public class UltracutSkill extends SkillNode {

    // 10 minutos exactos = 10 * 60 * 20 = 12,000 ticks
    public static final int COOLDOWN_10_MINUTES = 12000;

    public UltracutSkill() {
        super(
                SkillRegistry.NODE_ULTRACUT,
                SkillRegistry.BRANCH_MELEE,
                Component.literal("Definitiva CaC: Ultracorte Final"),
                Component.literal("Carga la espada definitiva. El próximo ataque físico inflige un 500% de daño catastrófico con detonación en área (10 min CD)."),
                NodeType.ULTIMATE,
                COOLDOWN_10_MINUTES
        );
    }

    @Override
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {
        if (skills.isUltimateCharged()) {
            player.displayClientMessage(Component.literal("§e⚡ ¡Tu espada ya ruge con el Ultracorte! Golpea a tu objetivo."), true);
            return;
        }

        skills.setUltimateCharged(true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.0f, 0.8f);
        player.displayClientMessage(Component.literal("§4§l⚡ ¡ULTRACORTE FINAL ACTIVADO! §c(Próximo impacto: +500% de Daño Catastrófico)"), true);
    }

    @Override
    public void onLivingHurt(ServerPlayer player, LivingHurtEvent event, PlayerSkills skills) {
        if (!skills.isUltimateCharged() || event.getSource().getDirectEntity() != player) return;

        skills.setUltimateCharged(false);
        // Aplica el cooldown de 10 minutos
        skills.setCooldown(this.getId(), COOLDOWN_10_MINUTES);

        float damageFinal = event.getAmount() * 5.0f;
        event.setAmount(damageFinal);

        LivingEntity mainTarget = event.getEntity();
        ServerLevel level = (ServerLevel) player.level();

        // Explosión de partículas gigantes
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, mainTarget.getX(), mainTarget.getY() + 1.0, mainTarget.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.FLASH, mainTarget.getX(), mainTarget.getY() + 1.0, mainTarget.getZ(), 3, 0.5, 0.5, 0.5, 0);
        level.sendParticles(ParticleTypes.CRIT, mainTarget.getX(), mainTarget.getY() + 1.0, mainTarget.getZ(), 100, 1.0, 1.0, 1.0, 0.5);

        level.playSound(null, mainTarget.getX(), mainTarget.getY(), mainTarget.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.5f, 0.9f);
        level.playSound(null, mainTarget.getX(), mainTarget.getY(), mainTarget.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.2f, 1.0f);

        // Daño colateral a enemigos cercanos (radio de 6 bloques)
        AABB box = mainTarget.getBoundingBox().inflate(6.0);
        List<LivingEntity> nearby = level.getEntitiesOfClass(
                LivingEntity.class,
                box,
                e -> e != player && e != mainTarget && e.isAlive() && !e.isAlliedTo(player)
        );

        for (LivingEntity splashTarget : nearby) {
            splashTarget.hurt(player.damageSources().playerAttack(player), damageFinal * 0.5f);
            splashTarget.knockback(1.5, -(splashTarget.getX() - mainTarget.getX()), -(splashTarget.getZ() - mainTarget.getZ()));
        }

        player.displayClientMessage(
                Component.literal("§4§l💥 ¡ULTRACORTE FINAL DESATADO! §fDaño al jefe: §e" + String.format("%.1f", damageFinal) + " §7(Enemigos cercanos alcanzados: " + nearby.size() + ")"),
                true
        );
    }
}