package com.example.modrpg.skills.data;

import com.example.modrpg.skills.PlayerSkills;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class SkillNode {

    public enum NodeType {
        PASSIVE_STAT,   // Aumento de estadísticas lineales (+2% daño por nivel)
        ACTIVE_ABILITY, // Habilidad activable por tecla (Torbellino, Megacorte)
        HYBRID_SYNERGY, // Cruce entre dos ramas (CaC + Arquería, CaC + Magia)
        ULTIMATE        // Definitiva con tiempo de carga alto (Ultracorte 10 min)
    }

    private final ResourceLocation id;
    private final ResourceLocation branchId;
    private final Component displayName;
    private final Component description;
    private final NodeType type;
    private final int defaultCooldownTicks;
    private final List<SkillRequirement> requirements = new ArrayList<>();

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

    public ResourceLocation getId() { return id; }
    public ResourceLocation getBranchId() { return branchId; }
    public Component getDisplayName() { return displayName; }
    public Component getDescription() { return description; }
    public NodeType getType() { return type; }
    public int getDefaultCooldownTicks() { return defaultCooldownTicks; }
    public List<SkillRequirement> getRequirements() { return Collections.unmodifiableList(requirements); }

    /**
     * Valida si el jugador puede desbloquear este nodo.
     */
    public boolean canUnlock(ServerPlayer player, PlayerSkills skills) {
        if (skills.isNodeUnlocked(this.id)) return false;
        for (SkillRequirement req : requirements) {
            if (!req.isMet(player, skills)) return false;
        }
        return true;
    }

    /**
     * Compra el nodo, cobrando costos y registrándolo en los desbloqueos del jugador.
     */
    public boolean tryUnlock(ServerPlayer player, PlayerSkills skills) {
        if (!canUnlock(player, skills)) return false;

        for (SkillRequirement req : requirements) {
            req.consume(player, skills);
        }

        skills.unlockNode(this.id);
        onUnlocked(player, skills);
        return true;
    }

    // =========================================================================
    // GANCHOS / EVENTOS SOBREESCRIBIBLES POR CADA HABILIDAD
    // =========================================================================

    public void onUnlocked(ServerPlayer player, PlayerSkills skills) {}

    /**
     * Se ejecuta cuando el jugador presiona la tecla asignada para esta habilidad.
     */
    public void onExecuteActive(ServerPlayer player, PlayerSkills skills) {}

    /**
     * Se ejecuta durante el cálculo de combate cuando el jugador hiere a una entidad.
     */
    public void onLivingHurt(ServerPlayer player, LivingHurtEvent event, PlayerSkills skills) {}

    /**
     * Se ejecuta cuando el jugador dispara una flecha u otro proyectil.
     */
    public void onArrowShoot(ServerPlayer player, EntityJoinLevelEvent event, AbstractArrow arrow, PlayerSkills skills) {}
}