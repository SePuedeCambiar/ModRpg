package com.example.modrpg.skills.nodes.hybrid;

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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

public class SwordQuiverSkill extends SkillNode {

    public static final String SWORD_ARROW_TAG = "modrpg_sword_arrow";

    public SwordQuiverSkill() {
        super(
                SkillRegistry.NODE_SWORD_QUIVER,
                SkillRegistry.BRANCH_MELEE,
                Component.literal("Carcaj de Espadas"),
                Component.literal("Tus proyectiles a distancia se infunden con filos cortantes de espada, sumando tu daño de CaC al disparo."),
                NodeType.HYBRID_SYNERGY,
                0
        );
    }

    @Override
    public void onArrowShoot(ServerPlayer player, EntityJoinLevelEvent event, AbstractArrow arrow, PlayerSkills skills) {
        arrow.addTag(SWORD_ARROW_TAG);
        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(ParticleTypes.CRIT, arrow.getX(), arrow.getY(), arrow.getZ(), 8, 0.2, 0.2, 0.2, 0.1);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 1.5f);
    }

    @Override
    public void onLivingHurt(ServerPlayer player, LivingHurtEvent event, PlayerSkills skills) {
        if (event.getSource().getDirectEntity() instanceof AbstractArrow arrow && arrow.getTags().contains(SWORD_ARROW_TAG)) {
            float meleeDamage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            float bonus = meleeDamage * 1.5f;
            event.setAmount(event.getAmount() + bonus);

            LivingEntity target = event.getEntity();
            ServerLevel level = (ServerLevel) player.level();
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 1.0, target.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6f, 1.8f);

            player.displayClientMessage(Component.literal("§6🗡 ¡IMPACTO DE ESPADA ARROJADIZA! §e+" + String.format("%.1f", bonus) + " Daño CaC"), true);
        }
    }
}
