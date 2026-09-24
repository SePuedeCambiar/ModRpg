package com.example.modrpg.skills.nodes.melee;

import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

public class WeaponMasterySkill extends SkillNode {

    public WeaponMasterySkill() {
        super(
                SkillRegistry.NODE_WEAPON_MASTERY,
                SkillRegistry.BRANCH_MELEE,
                Component.literal("Dominio de las Armas"),
                Component.literal("Aumenta +15% de daño con armas cuerpo a cuerpo. Las armas sufren un desgaste de durabilidad ligeramente mayor."),
                NodeType.PASSIVE_STAT,
                0
        );
    }

    @Override
    public void onLivingHurt(ServerPlayer player, LivingHurtEvent event, PlayerSkills skills) {
        if (event.getSource().getDirectEntity() != player) return;

        ItemStack weapon = player.getMainHandItem();
        if (weapon.getItem() instanceof TieredItem) {
            event.setAmount(event.getAmount() * 1.15f);

            // Probabilidad del 15% de consumir 1 punto de durabilidad extra por golpe
            if (player.getRandom().nextFloat() < 0.15f && weapon.isDamageableItem()) {
                weapon.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
            }

            ServerLevel level = (ServerLevel) player.level();
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, event.getEntity().getX(), event.getEntity().getY() + 0.8, event.getEntity().getZ(), 2, 0.1, 0.1, 0.1, 0.0);
        }
    }
}