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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class SummonSkeletonsSkill extends SkillNode {

    public SummonSkeletonsSkill() {
        super(
                SkillRegistry.NODE_SUMMON_SKELETONS,
                SkillRegistry.BRANCH_MAGIC,
                Component.literal("Necromancia: Arqueros Espectrales"),
                Component.literal("Invoca 2 esqueletos arqueros con cascos protectores que disparan a los enemigos a la distancia (25 segundos)."),
                NodeType.ACTIVE_ABILITY,
                500 // 25 segundos
        );
        this.setManaCost(50.0f);
    }

    @Override
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 pos1 = player.position().add(-2.0, 0, 1.5);
        Vec3 pos2 = player.position().add(2.0, 0, 1.5);

        for (Vec3 pos : new Vec3[]{pos1, pos2}) {
            MinionHelper.spawnMinion(player, EntityType.SKELETON, pos, 500, (Skeleton skeleton) -> {
                skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                skeleton.setDropChance(EquipmentSlot.HEAD, 0.0f);
                skeleton.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
                skeleton.setCustomName(Component.literal("§7Arquero de " + player.getName().getString()));
                skeleton.setCustomNameVisible(false);
            });
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SKELETON_AMBIENT, SoundSource.PLAYERS, 1.2f, 0.8f);
        player.displayClientMessage(Component.literal("§7🏹 ¡ARQUEROS ESPECTRALES INVOCADOS!"), true);
    }
}