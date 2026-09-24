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

public class IronStrengthSkill extends SkillNode {

    public static final String TAG_IRON_STRENGTH = "modrpg_iron_strength_active";

    public IronStrengthSkill() {
        super(
                SkillRegistry.NODE_IRON_STRENGTH,
                SkillRegistry.BRANCH_DEFENSE,
                Component.literal("Fuerza de Hierro"),
                Component.literal("Durante 12 segundos, los ataques entrantes no producen daño y cada golpe recibido restaura tu vida."),
                NodeType.ACTIVE_ABILITY,
                600 // 30 segundos de recarga
        );
    }

    @Override
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {
        player.addTag(TAG_IRON_STRENGTH);

        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.5, 0.5, 0.5, 0.2);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 1.5f, 0.7f);

        player.displayClientMessage(Component.literal("§6🛡 ¡FUERZA DE HIERRO ACTIVADA! (12s de invulnerabilidad y absorción)"), true);
    }
}