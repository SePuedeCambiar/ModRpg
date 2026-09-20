package com.example.modrpg.skills.data;

import com.example.modrpg.skills.PlayerSkills;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class SkillNode {

    public enum NodeType {
        PASSIVE_STAT,
        ACTIVE_ABILITY,
        HYBRID_SYNERGY,
        ULTIMATE
    }

    private final ResourceLocation id;
    private final ResourceLocation branchId;
    private final Component displayName;
    private final Component description;
    private final NodeType type;
    private final int defaultCooldownTicks;
    private final List<SkillRequirement> requirements = new ArrayList<>();

    // Propiedades visuales para la GUI
    private int posX = 0;
    private int posY = 0;
    private final List<ResourceLocation> parentIds = new ArrayList<>();
    private ItemStack icon = new ItemStack(Items.BOOK);

    public SkillNode(ResourceLocation id, ResourceLocation branchId, Component displayName, Component description, NodeType type, int defaultCooldownTicks) {
        this.id = id;
        this.branchId = branchId;
        this.displayName = displayName;
        this.description = description;
        this.type = type;
        this.defaultCooldownTicks = defaultCooldownTicks;
    }

    public SkillNode addRequirement(SkillRequirement requirement) {
        this.requirements.add(requirement);
        return this;
    }

    // Configuración visual con soporte multi-padre
    public SkillNode setVisuals(int x, int y, ItemStack icon, ResourceLocation... parents) {
        this.posX = x;
        this.posY = y;
        this.icon = icon;
        for (ResourceLocation parent : parents) {
            if (parent != null && !this.parentIds.contains(parent)) {
                this.parentIds.add(parent);
            }
        }
        return this;
    }

    public SkillNode addParent(ResourceLocation parentId) {
        if (parentId != null && !this.parentIds.contains(parentId)) {
            this.parentIds.add(parentId);
        }
        return this;
    }

    public ResourceLocation getId() { return id; }
    public ResourceLocation getBranchId() { return branchId; }
    public Component getDisplayName() { return displayName; }
    public Component getDescription() { return description; }
    public NodeType getType() { return type; }
    public int getDefaultCooldownTicks() { return defaultCooldownTicks; }
    public List<SkillRequirement> getRequirements() { return Collections.unmodifiableList(requirements); }

    public int getPosX() { return posX; }
    public int getPosY() { return posY; }
    public List<ResourceLocation> getParentIds() { return Collections.unmodifiableList(parentIds); }
    public ItemStack getIcon() { return icon; }

    public boolean canUnlock(ServerPlayer player, PlayerSkills skills) {
        if (skills.isNodeUnlocked(this.id)) return false;
        for (SkillRequirement req : requirements) {
            if (!req.isMet(player, skills)) return false;
        }
        return true;
    }

    public boolean tryUnlock(ServerPlayer player, PlayerSkills skills) {
        if (!canUnlock(player, skills)) return false;

        for (SkillRequirement req : requirements) {
            req.consume(player, skills);
        }

        skills.unlockNode(this.id);
        onUnlocked(player, skills);
        return true;
    }

    public void onUnlocked(ServerPlayer player, PlayerSkills skills) {}
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {}
    public void onLivingHurt(ServerPlayer player, LivingHurtEvent event, PlayerSkills skills) {}
    public void onArrowShoot(ServerPlayer player, EntityJoinLevelEvent event, AbstractArrow arrow, PlayerSkills skills) {}
}