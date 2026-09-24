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
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class SummonZombiesSkill extends SkillNode {

    public SummonZombiesSkill() {
        super(
                SkillRegistry.NODE_SUMMON_ZOMBIES,
                SkillRegistry.BRANCH_MAGIC,
                Component.literal("Necromancia: Horda de Infantes"),
                Component.literal("Levanta de la tierra a 5 zombis pequeños con cascos protectores que asedian a tus agresores (20 segundos)."),
                NodeType.ACTIVE_ABILITY,
                400 // 20 segundos de recarga
        );
        this.setManaCost(45.0f);
    }

    @Override
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {
        ServerLevel level = (ServerLevel) player.level();
        int count = 5;

        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI / count) * i;
            double offsetX = Math.cos(angle) * 2.0;
            double offsetZ = Math.sin(angle) * 2.0;
            Vec3 pos = player.position().add(offsetX, 0, offsetZ);

            MinionHelper.spawnMinion(player, EntityType.ZOMBIE, pos, 400, (Zombie zombie) -> {
                zombie.setBaby(true);
                // Casco de hierro para que no se quemen a la luz del día y duren más
                zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                zombie.setDropChance(EquipmentSlot.HEAD, 0.0f);
                zombie.setCustomName(Component.literal("§2Esbirro de " + player.getName().getString()));
                zombie.setCustomNameVisible(false);
            });
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.PLAYERS, 1.2f, 1.4f);
        player.displayClientMessage(Component.literal("§2🧟 ¡HORDA DE INFANTES INVOCADA! (5 Zombis aliados)"), true);
    }
}