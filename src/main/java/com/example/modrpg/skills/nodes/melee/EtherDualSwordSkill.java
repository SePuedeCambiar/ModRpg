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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.SwordItem;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

public class EtherDualSwordSkill extends SkillNode {

    public static final String ETHER_TAG = "modrpg_ether_slice";

    public EtherDualSwordSkill() {
        super(
                SkillRegistry.NODE_ETHER_DUAL_SWORD,
                SkillRegistry.BRANCH_MELEE,
                Component.literal("Doble Espada Espectral"),
                Component.literal("Canaliza magia arcana para manifestar una espada espiritual en tu mano secundaria, asestando un segundo golpe mágico."),
                NodeType.ACTIVE_ABILITY,
                0
        );
    }

    @Override
    public void onLivingHurt(ServerPlayer player, LivingHurtEvent event, PlayerSkills skills) {
        LivingEntity target = event.getEntity();
        if (target == null || target.getTags().contains(ETHER_TAG)) return;

        // Solo con espada y ataque directo
        if (event.getSource().getDirectEntity() == player && player.getMainHandItem().getItem() instanceof SwordItem) {
            ServerLevel level = (ServerLevel) player.level();
            float magicDamage = event.getAmount() * 0.75f;

            target.addTag(ETHER_TAG);
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().magic(), magicDamage);
            target.invulnerableTime = 10;

            // Animación de mano secundaria (mano libre o espada etérea)
            player.swing(InteractionHand.OFF_HAND, true);

            // Partículas mágicas de espada espiritual
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY() + 1.0, target.getZ(), 25, 0.3, 0.4, 0.3, 0.15);
            level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + 0.8, target.getZ(), 10, 0.2, 0.3, 0.2, 0.05);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.8f, 1.6f);

            player.displayClientMessage(Component.literal("§d§l⚔ ¡CORTE ESPIRITUAL! §fDaño mágico: §b+" + String.format("%.1f", magicDamage)), true);
        }
    }
}