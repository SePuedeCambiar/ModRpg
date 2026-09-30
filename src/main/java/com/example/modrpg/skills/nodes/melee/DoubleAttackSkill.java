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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

public class DoubleAttackSkill extends SkillNode {

    public static final String RECURSION_TAG = "modrpg_double_slice_hit";
    public static final String PLAYER_EXECUTING_TAG = "modrpg_double_attack_active";

    public DoubleAttackSkill() {
        super(
                SkillRegistry.NODE_DOUBLE_ATTACK,
                SkillRegistry.BRANCH_MELEE,
                Component.literal("Doble Ataque"),
                Component.literal("Al atacar con la barra de carga al 100%, asesta un segundo golpe consecutivo al 80% de daño."),
                NodeType.ACTIVE_ABILITY,
                0
        );
    }

    @Override
    public void onLivingHurt(ServerPlayer player, LivingHurtEvent event, PlayerSkills skills) {
        LivingEntity target = event.getEntity();
        if (target == null || !target.isAlive() || player.level().isClientSide()) return;

        // SPRINT 2 FIX: Evitar bucles de recursión bidireccionales (en jugador y en víctima)
        if (target.getTags().contains(RECURSION_TAG) || player.getTags().contains(PLAYER_EXECUTING_TAG)) {
            return;
        }

        // SPRINT 2 FIX: No duplicar el Ultracorte Final si se está ejecutando
        if (player.getTags().contains(UltracutSkill.ULTRACUT_HIT_TAG)) {
            return;
        }

        // Validar que sea un ataque directo cuerpo a cuerpo (no flechas, no magia, no espinas)
        boolean isDirectMelee = event.getSource().getDirectEntity() == player
                && !(event.getSource().getDirectEntity() instanceof Projectile)
                && !event.getSource().is(DamageTypes.ARROW)
                && !event.getSource().is(DamageTypes.MAGIC)
                && !event.getSource().is(DamageTypes.INDIRECT_MAGIC)
                && !event.getSource().is(DamageTypes.THORNS);

        if (isDirectMelee && player.getAttackStrengthScale(0.5f) >= 0.92f) {
            // El primer golpe se ajusta al 80% y el segundo hace otro 80% (total 160% de daño)
            float singleHitDamage = event.getAmount() * 0.80f;
            event.setAmount(singleHitDamage);

            try {
                // Marcar ambas entidades para bloquear reentradas en LivingHurtEvent
                target.addTag(RECURSION_TAG);
                player.addTag(PLAYER_EXECUTING_TAG);

                int prevInvulnerable = target.invulnerableTime;
                target.invulnerableTime = 0;

                // Asestar el segundo golpe físico
                target.hurt(player.damageSources().playerAttack(player), singleHitDamage);

                target.invulnerableTime = Math.max(prevInvulnerable, 10);
            } finally {
                target.removeTag(RECURSION_TAG);
                player.removeTag(PLAYER_EXECUTING_TAG);
            }

            // Efectos visuales y de sonido
            ServerLevel level = (ServerLevel) player.level();
            player.swing(InteractionHand.MAIN_HAND, true);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 0.9, target.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 0.9, target.getZ(), 12, 0.25, 0.25, 0.25, 0.1);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.4f);

            player.displayClientMessage(Component.literal("§c§l⚔ ¡DOBLE ATAQUE! §f(2 impactos al 80%)"), true);
        }
    }
}