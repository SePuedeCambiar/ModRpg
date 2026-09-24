package com.example.modrpg.skills.nodes.magic;

import com.example.modrpg.networking.ModMessages;
import com.example.modrpg.networking.PacketSyncMana;
import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

public class EarthTuneSkill extends SkillNode {

    public EarthTuneSkill() {
        super(
                SkillRegistry.NODE_EARTH_TUNE,
                SkillRegistry.BRANCH_MAGIC,
                Component.literal("Sintonía Terrenal"),
                Component.literal("Canaliza la resonancia del suelo (tierra, roca o arena). Consume energía vital para restaurar 40 de maná."),
                NodeType.ACTIVE_ABILITY,
                200 // 10 segundos
        );
    }

    @Override
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {
        BlockPos below = player.blockPosition().below();
        BlockState state = player.level().getBlockState(below);

        boolean isEarth = state.is(BlockTags.BASE_STONE_OVERWORLD) ||
                state.is(BlockTags.DIRT) ||
                state.is(BlockTags.SAND);

        if (!isEarth) {
            player.displayClientMessage(Component.literal("§c✖ Debes estar sobre tierra, roca o arena para sintonizar."), true);
            skills.setCooldown(this.getId(), 40); // 2s de penalización
            return;
        }

        // Paga 2 puntos de hambre (1 muslito) para recuperar 40 de maná
        if (player.getFoodData().getFoodLevel() > 2) {
            player.causeFoodExhaustion(4.0f);
        }

        skills.restoreMana(40.0f);
        ModMessages.sendToPlayer(new PacketSyncMana(skills.getCurrentMana(), skills.getMaxMana()), player);

        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(ParticleTypes.COMPOSTER, player.getX(), player.getY() + 0.5, player.getZ(), 20, 0.4, 0.2, 0.4, 0.05);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f);

        player.displayClientMessage(Component.literal("§a🌱 ¡SINTONÍA TERRENAL! §b+40 Maná restaurado"), true);
    }
}