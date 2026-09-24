package com.example.modrpg.skills.nodes.magic;

import com.example.modrpg.skills.PlayerSkills;
import com.example.modrpg.skills.data.SkillNode;
import com.example.modrpg.skills.data.SkillRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;

public class SummonWolvesSkill extends SkillNode {

    public SummonWolvesSkill() {
        super(
                SkillRegistry.NODE_SUMMON_WOLVES,
                SkillRegistry.BRANCH_MAGIC,
                Component.literal("Invocación: Manada Espectral"),
                Component.literal("Llama a una manada de 3 lobos domesticados leales que despedazan a tus presas durante 15 segundos."),
                NodeType.ACTIVE_ABILITY,
                360 // 18 segundos de recarga
        );
        this.setManaCost(40.0f);
    }

    @Override
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {
        ServerLevel level = (ServerLevel) player.level();
        int wolfLifespan = 300; // 15 segundos

        for (int i = 0; i < 3; i++) {
            double angle = (2 * Math.PI / 3) * i;
            Vec3 pos = player.position().add(Math.cos(angle) * 2.0, 0, Math.sin(angle) * 2.0);

            MinionHelper.spawnMinion(player, EntityType.WOLF, pos, wolfLifespan, (Wolf wolf) -> {
                wolf.tame(player);
                wolf.setCollarColor(DyeColor.PURPLE);
                wolf.setCustomName(Component.literal("§dGuardián de " + player.getName().getString()));
                wolf.setCustomNameVisible(false);
            });
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOLF_HOWL, SoundSource.PLAYERS, 1.2f, 1.0f);
        player.displayClientMessage(Component.literal("§d🐺 ¡MANADA ESPECTRAL INVOCADA! (3 Lobos por 15s)"), true);
    }
}