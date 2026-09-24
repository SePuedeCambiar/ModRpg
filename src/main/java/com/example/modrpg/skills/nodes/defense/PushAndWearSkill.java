package com.example.modrpg.skills.nodes.defense;

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
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

public class PushAndWearSkill extends SkillNode {

    public PushAndWearSkill() {
        super(
                SkillRegistry.NODE_PUSH_AND_WEAR,
                SkillRegistry.BRANCH_DEFENSE,
                Component.literal("Empuje y Desgaste"),
                Component.literal("Tus ataques empujan con violencia al enemigo y tienen una probabilidad de desgastar su arma equipada."),
                NodeType.PASSIVE_STAT,
                0
        );
    }

    @Override
    public void onLivingHurt(ServerPlayer player, LivingHurtEvent event, PlayerSkills skills) {
        if (event.getSource().getDirectEntity() != player) return;

        LivingEntity target = event.getEntity();
        if (target == null) return;

        // Empuje masivo
        target.knockback(1.5, -(target.getX() - player.getX()), -(target.getZ() - player.getZ()));

        // Desgaste del arma enemiga
        ItemStack enemyItem = target.getMainHandItem();
        if (!enemyItem.isEmpty() && enemyItem.isDamageableItem()) {
            enemyItem.hurtAndBreak(5, target, entity -> entity.broadcastBreakEvent(InteractionHand.MAIN_HAND));
            ServerLevel level = (ServerLevel) player.level();
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.0, target.getZ(), 10, 0.2, 0.2, 0.2, 0.05);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6f, 1.8f);
        }
    }
}