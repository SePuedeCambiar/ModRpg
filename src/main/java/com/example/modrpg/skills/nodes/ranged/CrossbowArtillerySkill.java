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
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

public class CrossbowArtillerySkill extends SkillNode {

    public CrossbowArtillerySkill() {
        super(
                SkillRegistry.NODE_CROSSBOW_ARTILLERY,
                SkillRegistry.BRANCH_RANGED,
                Component.literal("Artillería Pesada con Ballesta"),
                Component.literal("Los cohetes disparados con ballesta causan una explosión sónica con aliento de dragón y daño devastador."),
                NodeType.ACTIVE_ABILITY,
                0
        );
    }

    @Override
    public void onLivingHurt(ServerPlayer player, LivingHurtEvent event, PlayerSkills skills) {
        if (event.getSource().getDirectEntity() instanceof FireworkRocketEntity) {
            int rangedLvl = skills.getBranchLevel(SkillRegistry.BRANCH_RANGED);
            float bonusDamage = 15.0f + (rangedLvl * 0.5f);
            event.setAmount(event.getAmount() + bonusDamage);

            ServerLevel level = (ServerLevel) player.level();
            level.sendParticles(ParticleTypes.DRAGON_BREATH, event.getEntity().getX(), event.getEntity().getY() + 0.8, event.getEntity().getZ(), 50, 0.8, 0.8, 0.8, 0.1);
            level.sendParticles(ParticleTypes.SONIC_BOOM, event.getEntity().getX(), event.getEntity().getY() + 0.5, event.getEntity().getZ(), 1, 0, 0, 0, 0);
            level.playSound(null, event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2f, 0.8f);

            player.displayClientMessage(Component.literal("§c§l💥 ¡DETONACIÓN DE ARTILLERÍA! §e+" + String.format("%.1f", bonusDamage) + " Daño"), true);
        }
    }
}
