package com.example.modrpg.skills.data;

import com.example.modrpg.ModRpg;
import com.example.modrpg.skills.nodes.hybrid.HybridHunterSkill;
import com.example.modrpg.skills.nodes.melee.CapstoneMeleeSkill;
import com.example.modrpg.skills.nodes.melee.DoubleAttackSkill;
import com.example.modrpg.skills.nodes.melee.SpinAttackSkill;
import com.example.modrpg.skills.nodes.ranged.HypersonicArrowSkill;
import com.example.modrpg.skills.nodes.ranged.TailwindSkill;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public class SkillRegistry {

    public static final ResourceLocation BRANCH_MELEE    = new ResourceLocation(ModRpg.MODID, "melee");
    public static final ResourceLocation BRANCH_RANGED   = new ResourceLocation(ModRpg.MODID, "ranged");
    public static final ResourceLocation BRANCH_MOBILITY = new ResourceLocation(ModRpg.MODID, "mobility");
    public static final ResourceLocation BRANCH_MAGIC    = new ResourceLocation(ModRpg.MODID, "magic");
    public static final ResourceLocation BRANCH_HEALING  = new ResourceLocation(ModRpg.MODID, "healing");
    public static final ResourceLocation BRANCH_DEFENSE  = new ResourceLocation(ModRpg.MODID, "defense");

    public static final ResourceLocation COUNTER_MELEE_KILLS  = new ResourceLocation(ModRpg.MODID, "melee_kills");
    public static final ResourceLocation COUNTER_RANGED_KILLS = new ResourceLocation(ModRpg.MODID, "ranged_kills");

    public static final ResourceLocation NODE_DOUBLE_ATTACK   = new ResourceLocation(ModRpg.MODID, "melee_double_attack");
    public static final ResourceLocation NODE_SPIN_ATTACK     = new ResourceLocation(ModRpg.MODID, "melee_spin_attack");
    public static final ResourceLocation NODE_CAPSTONE_MELEE  = new ResourceLocation(ModRpg.MODID, "melee_capstone");
    public static final ResourceLocation NODE_TAILWIND        = new ResourceLocation(ModRpg.MODID, "ranged_tailwind");
    public static final ResourceLocation NODE_HYPERSONIC      = new ResourceLocation(ModRpg.MODID, "ranged_hypersonic");
    public static final ResourceLocation NODE_HYBRID_HUNTER   = new ResourceLocation(ModRpg.MODID, "hybrid_hunter");

    private static final Map<ResourceLocation, SkillNode> NODES = new LinkedHashMap<>();

    public static synchronized void register(SkillNode node) {
        NODES.put(node.getId(), node);
    }

    public static SkillNode get(ResourceLocation id) {
        return NODES.get(id);
    }

    public static Collection<SkillNode> getAll() {
        return Collections.unmodifiableCollection(NODES.values());
    }

    public static List<SkillNode> getNodesInBranch(ResourceLocation branchId) {
        List<SkillNode> list = new ArrayList<>();
        for (SkillNode node : NODES.values()) {
            if (node.getBranchId().equals(branchId)) {
                list.add(node);
            }
        }
        return list;
    }

    /**
     * Registra todas las habilidades del mod asociándoles sus requisitos.
     */
    public static void init() {
        NODES.clear();

        // 1. Doble Ataque: CaC Nivel 4
        register(new DoubleAttackSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 4)));

        // 2. Torbellino 360°: CaC Nivel 20 + 20 bajas CaC
        register(new SpinAttackSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 20))
                .addRequirement(SkillRequirement.practice(COUNTER_MELEE_KILLS, 20, "bajas CaC")));

        // 3. Golpe Definitivo: CaC Nivel 50 + Nivel 25 XP
        register(new CapstoneMeleeSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 50))
                .addRequirement(SkillRequirement.minPlayerXpLevel(25)));

        // 4. Viento a Favor: Arquería Nivel 5
        register(new TailwindSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_RANGED, 5)));

        // 5. Flecha Hipersónica: Arquería Nivel 50
        register(new HypersonicArrowSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_RANGED, 50)));

        // 6. Cazador Híbrido: CaC Nivel 25 + Arquería Nivel 25
        register(new HybridHunterSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 25))
                .addRequirement(SkillRequirement.branchLevel(BRANCH_RANGED, 25)));

        ModRpg.LOGGER.info(">>> [SkillRegistry] Se han registrado exitosamente {} habilidades RPG <<<", NODES.size());
    }
}