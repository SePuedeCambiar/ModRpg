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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class IronFortressSkill extends SkillNode {

    public IronFortressSkill() {
        super(
                SkillRegistry.NODE_IRON_FORTRESS,
                SkillRegistry.BRANCH_DEFENSE,
                Component.literal("Fortaleza Inquebrantable"),
                Component.literal("Otorga Resistencia III y Corazones de Absorción dorados durante 10 segundos."),
                NodeType.ACTIVE_ABILITY,
                600 // 30 segundos de recarga
        );
    }

    @Override
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 2, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1, false, false));

        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.5, 0.5, 0.5, 0.2);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 1.2f, 0.8f);

        player.displayClientMessage(Component.literal("§6🛡 ¡FORTALEZA INQUEBRANTABLE ACTIVADA!"), true);
    }
}
