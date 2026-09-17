package com.example.modrpg.skills.data;

import com.example.modrpg.ModRpg;
import com.example.modrpg.skills.nodes.hybrid.HybridHunterSkill;
import com.example.modrpg.skills.nodes.melee.DoubleAttackSkill;
import com.example.modrpg.skills.nodes.melee.EtherDualSwordSkill;
import com.example.modrpg.skills.nodes.melee.HeavyTornadoSkill;
import com.example.modrpg.skills.nodes.melee.MegacutSkill;
import com.example.modrpg.skills.nodes.melee.UltracutSkill;
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

    public static final ResourceLocation NODE_DOUBLE_ATTACK     = new ResourceLocation(ModRpg.MODID, "melee_double_attack");
    public static final ResourceLocation NODE_ETHER_DUAL_SWORD  = new ResourceLocation(ModRpg.MODID, "melee_ether_dual_sword");
    public static final ResourceLocation NODE_HEAVY_TORNADO     = new ResourceLocation(ModRpg.MODID, "melee_heavy_tornado");
    public static final ResourceLocation NODE_MEGACUT           = new ResourceLocation(ModRpg.MODID, "melee_megacut");
    public static final ResourceLocation NODE_ULTRACUT          = new ResourceLocation(ModRpg.MODID, "melee_ultracut");

    public static final ResourceLocation NODE_TAILWIND          = new ResourceLocation(ModRpg.MODID, "ranged_tailwind");
    public static final ResourceLocation NODE_HYPERSONIC        = new ResourceLocation(ModRpg.MODID, "ranged_hypersonic");
    public static final ResourceLocation NODE_HYBRID_HUNTER     = new ResourceLocation(ModRpg.MODID, "hybrid_hunter");

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

    public static void init() {
        NODES.clear();

        // 1. Doble Ataque base
        register(new DoubleAttackSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 4)));

        // 2. Doble Espada Espectral (CaC 5 + Magia 6)
        register(new EtherDualSwordSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 5))
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MAGIC, 6)));

        // 3. Torbellino Ultrapesado (CaC 10 + 20 bajas)
        register(new HeavyTornadoSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 10))
                .addRequirement(SkillRequirement.practice(COUNTER_MELEE_KILLS, 20, "bajas CaC")));

        // 4. Semidefinitiva Megacorte (CaC 50 + 100 bajas)
        register(new MegacutSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 50))
                .addRequirement(SkillRequirement.practice(COUNTER_MELEE_KILLS, 100, "bajas CaC")));

        // 5. Definitiva Ultracorte Final (CaC 100 + 250 bajas)
        register(new UltracutSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 100))
                .addRequirement(SkillRequirement.practice(COUNTER_MELEE_KILLS, 250, "bajas CaC")));

        // 6. Arquería e Híbridos
        register(new TailwindSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_RANGED, 5)));

        register(new HypersonicArrowSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_RANGED, 50)));

        register(new HybridHunterSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 25))
                .addRequirement(SkillRequirement.branchLevel(BRANCH_RANGED, 25)));

        ModRpg.LOGGER.info(">>> [SkillRegistry] ¡{} habilidades RPG registradas correctamente! <<<", NODES.size());
    }
}