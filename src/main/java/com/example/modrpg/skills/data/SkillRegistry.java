package com.example.modrpg.skills.data;

import com.example.modrpg.ModRpg;
import com.example.modrpg.skills.nodes.defense.*;
import com.example.modrpg.skills.nodes.hybrid.*;
import com.example.modrpg.skills.nodes.magic.*;
import com.example.modrpg.skills.nodes.melee.*;
import com.example.modrpg.skills.nodes.mobility.*;
import com.example.modrpg.skills.nodes.ranged.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;

public class SkillRegistry {

    // Identificadores de ramas
    public static final ResourceLocation BRANCH_MELEE    = new ResourceLocation(ModRpg.MODID, "melee");
    public static final ResourceLocation BRANCH_RANGED   = new ResourceLocation(ModRpg.MODID, "ranged");
    public static final ResourceLocation BRANCH_MOBILITY = new ResourceLocation(ModRpg.MODID, "mobility");
    public static final ResourceLocation BRANCH_MAGIC    = new ResourceLocation(ModRpg.MODID, "magic");
    public static final ResourceLocation BRANCH_DEFENSE  = new ResourceLocation(ModRpg.MODID, "defense");

    // Contadores de práctica
    public static final ResourceLocation COUNTER_MELEE_KILLS   = new ResourceLocation(ModRpg.MODID, "melee_kills");
    public static final ResourceLocation COUNTER_RANGED_KILLS  = new ResourceLocation(ModRpg.MODID, "ranged_kills");
    public static final ResourceLocation COUNTER_MAGIC_CASTS   = new ResourceLocation(ModRpg.MODID, "magic_casts");
    public static final ResourceLocation COUNTER_DAMAGE_BLOCKED= new ResourceLocation(ModRpg.MODID, "damage_blocked");
    public static final ResourceLocation COUNTER_DISTANCE_RUN  = new ResourceLocation(ModRpg.MODID, "distance_run");

    // Nodos Melee
    public static final ResourceLocation NODE_DOUBLE_ATTACK     = new ResourceLocation(ModRpg.MODID, "melee_double_attack");
    public static final ResourceLocation NODE_ETHER_DUAL_SWORD  = new ResourceLocation(ModRpg.MODID, "melee_ether_dual_sword");
    public static final ResourceLocation NODE_HEAVY_TORNADO     = new ResourceLocation(ModRpg.MODID, "melee_heavy_tornado");
    public static final ResourceLocation NODE_MEGACUT           = new ResourceLocation(ModRpg.MODID, "melee_megacut");
    public static final ResourceLocation NODE_ULTRACUT          = new ResourceLocation(ModRpg.MODID, "melee_ultracut");

    // Nodos Ranged
    public static final ResourceLocation NODE_TAILWIND          = new ResourceLocation(ModRpg.MODID, "ranged_tailwind");
    public static final ResourceLocation NODE_CROSSBOW_ARTILLERY= new ResourceLocation(ModRpg.MODID, "ranged_crossbow_artillery");
    public static final ResourceLocation NODE_HYPERSONIC        = new ResourceLocation(ModRpg.MODID, "ranged_hypersonic");

    // Nodos Híbridos
    public static final ResourceLocation NODE_HYBRID_HUNTER     = new ResourceLocation(ModRpg.MODID, "hybrid_hunter");
    public static final ResourceLocation NODE_ARROW_PROPULSION  = new ResourceLocation(ModRpg.MODID, "hybrid_arrow_propulsion");
    public static final ResourceLocation NODE_SWORD_QUIVER      = new ResourceLocation(ModRpg.MODID, "hybrid_sword_quiver");
    public static final ResourceLocation NODE_COMBINED_ULTIMATE = new ResourceLocation(ModRpg.MODID, "hybrid_combined_ultimate");

    // Nodos Magia y Elementos (Sprint 1)
    public static final ResourceLocation NODE_FIREBALL          = new ResourceLocation(ModRpg.MODID, "magic_fireball");
    public static final ResourceLocation NODE_HEALING_AURA      = new ResourceLocation(ModRpg.MODID, "magic_healing_aura");
    public static final ResourceLocation NODE_NECROTIC_DRAIN    = new ResourceLocation(ModRpg.MODID, "magic_necrotic_drain");
    public static final ResourceLocation NODE_EARTH_TUNE        = new ResourceLocation(ModRpg.MODID, "magic_earth_tune");
    public static final ResourceLocation NODE_COUNTER_ATTACK    = new ResourceLocation(ModRpg.MODID, "magic_counter_attack");
    public static final ResourceLocation NODE_LIGHTNING_CHAIN   = new ResourceLocation(ModRpg.MODID, "magic_lightning_chain");

    // Nodos Movilidad
    public static final ResourceLocation NODE_LIGHT_STEP        = new ResourceLocation(ModRpg.MODID, "mobility_light_step");
    public static final ResourceLocation NODE_DASH              = new ResourceLocation(ModRpg.MODID, "mobility_dash");
    public static final ResourceLocation NODE_AIR_JUMP          = new ResourceLocation(ModRpg.MODID, "mobility_air_jump");

    // Nodos Defensa
    public static final ResourceLocation NODE_STONE_SKIN        = new ResourceLocation(ModRpg.MODID, "defense_stone_skin");
    public static final ResourceLocation NODE_IRON_FORTRESS     = new ResourceLocation(ModRpg.MODID, "defense_iron_fortress");

    private static final Map<ResourceLocation, SkillBranch> BRANCHES = new LinkedHashMap<>();
    private static final Map<ResourceLocation, SkillNode> NODES = new LinkedHashMap<>();

    public static void registerBranch(SkillBranch branch) {
        BRANCHES.put(branch.id(), branch);
    }

    public static SkillBranch getBranch(ResourceLocation id) {
        return BRANCHES.get(id);
    }

    public static Collection<SkillBranch> getAllBranches() {
        return Collections.unmodifiableCollection(BRANCHES.values());
    }

    public static synchronized void register(SkillNode node) {
        NODES.put(node.getId(), node);
    }

    public static SkillNode get(ResourceLocation id) {
        return NODES.get(id);
    }

    public static Collection<SkillNode> getAll() {
        return Collections.unmodifiableCollection(NODES.values());
    }

    public static void init() {
        BRANCHES.clear();
        NODES.clear();

        // 1. REGISTRO DE RAMAS PRINCIPALES
        registerBranch(new SkillBranch(BRANCH_MELEE,    Component.literal("Cuerpo a Cuerpo"), 10, COUNTER_MELEE_KILLS));
        registerBranch(new SkillBranch(BRANCH_RANGED,   Component.literal("Arquería"),         5,  COUNTER_RANGED_KILLS));
        registerBranch(new SkillBranch(BRANCH_MAGIC,    Component.literal("Magia"),            5,  COUNTER_MAGIC_CASTS));
        registerBranch(new SkillBranch(BRANCH_DEFENSE,  Component.literal("Defensa"),          8,  COUNTER_DAMAGE_BLOCKED));
        registerBranch(new SkillBranch(BRANCH_MOBILITY, Component.literal("Movilidad"),       10, COUNTER_DISTANCE_RUN));

        // 2. RAMA CUERPO A CUERPO (Arriba)
        register(new DoubleAttackSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 4))
                .setVisuals(0, -60, new ItemStack(Items.IRON_SWORD)));

        register(new EtherDualSwordSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 5))
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MAGIC, 6))
                .setVisuals(-60, -110, new ItemStack(Items.AMETHYST_SHARD), NODE_DOUBLE_ATTACK));

        register(new HeavyTornadoSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 10))
                .addRequirement(SkillRequirement.practice(COUNTER_MELEE_KILLS, 20, "bajas CaC"))
                .setVisuals(0, -130, new ItemStack(Items.DIAMOND_SWORD), NODE_DOUBLE_ATTACK));

        register(new MegacutSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 50))
                .addRequirement(SkillRequirement.practice(COUNTER_MELEE_KILLS, 100, "bajas CaC"))
                .setVisuals(0, -200, new ItemStack(Items.NETHERITE_SWORD), NODE_HEAVY_TORNADO));

        register(new UltracutSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 100))
                .addRequirement(SkillRequirement.practice(COUNTER_MELEE_KILLS, 250, "bajas CaC"))
                .setVisuals(0, -280, new ItemStack(Items.NETHER_STAR), NODE_MEGACUT));

        // 3. RAMA ARQUERÍA (Derecha)
        register(new TailwindSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_RANGED, 5))
                .setVisuals(70, 0, new ItemStack(Items.BOW)));

        register(new CrossbowArtillerySkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_RANGED, 20))
                .addRequirement(SkillRequirement.practice(COUNTER_RANGED_KILLS, 30, "bajas proyectil"))
                .setVisuals(150, -40, new ItemStack(Items.CROSSBOW), NODE_TAILWIND));

        register(new HypersonicArrowSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_RANGED, 50))
                .setVisuals(160, 40, new ItemStack(Items.SPECTRAL_ARROW), NODE_TAILWIND));

        // 4. SINERGIAS HÍBRIDAS
        register(new HybridHunterSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 25))
                .addRequirement(SkillRequirement.branchLevel(BRANCH_RANGED, 25))
                .setVisuals(80, -90, new ItemStack(Items.ENDER_EYE), NODE_DOUBLE_ATTACK, NODE_TAILWIND));

        register(new ArrowPropulsionSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 35))
                .addRequirement(SkillRequirement.branchLevel(BRANCH_RANGED, 30))
                .setVisuals(140, -110, new ItemStack(Items.FEATHER), NODE_HYBRID_HUNTER));

        register(new SwordQuiverSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 70))
                .addRequirement(SkillRequirement.branchLevel(BRANCH_RANGED, 50))
                .setVisuals(100, -190, new ItemStack(Items.ARROW), NODE_HYBRID_HUNTER));

        register(new CombinedUltimateSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 100))
                .addRequirement(SkillRequirement.branchLevel(BRANCH_RANGED, 70))
                .setVisuals(90, -280, new ItemStack(Items.DRAGON_BREATH), NODE_ULTRACUT, NODE_SWORD_QUIVER));

        // 5. RAMA MAGIA Y ELEMENTOS (Izquierda)
        register(new FireballSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MAGIC, 3))
                .setVisuals(-80, 0, new ItemStack(Items.FIRE_CHARGE)));

        register(new HealingAuraSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MAGIC, 6))
                .setVisuals(-160, -50, new ItemStack(Items.GLISTERING_MELON_SLICE), NODE_FIREBALL));

        register(new NecroticDrainSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MAGIC, 15))
                .setVisuals(-160, 50, new ItemStack(Items.WITHER_SKELETON_SKULL), NODE_FIREBALL));

        register(new EarthTuneSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MAGIC, 8))
                .setVisuals(-120, -100, new ItemStack(Items.COARSE_DIRT), NODE_FIREBALL));

        register(new CounterAttackSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MAGIC, 12))
                .setVisuals(-200, 0, new ItemStack(Items.SHIELD), NODE_FIREBALL));

        register(new LightningChainSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MAGIC, 20))
                .setVisuals(-120, 100, new ItemStack(Items.LIGHTNING_ROD), NODE_FIREBALL));

        // 6. RAMA MOVILIDAD (Abajo)
        register(new LightStepSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MOBILITY, 2))
                .setVisuals(0, 70, new ItemStack(Items.LEATHER_BOOTS)));

        register(new DashSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MOBILITY, 10))
                .setVisuals(-60, 140, new ItemStack(Items.SUGAR), NODE_LIGHT_STEP));

        register(new AirJumpSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MOBILITY, 20))
                .setVisuals(60, 140, new ItemStack(Items.PHANTOM_MEMBRANE), NODE_LIGHT_STEP));

        // 7. RAMA DEFENSA (Abajo-Derecha)
        register(new StoneSkinSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_DEFENSE, 3))
                .setVisuals(100, 90, new ItemStack(Items.SHIELD)));

        register(new IronFortressSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_DEFENSE, 20))
                .setVisuals(170, 150, new ItemStack(Items.IRON_CHESTPLATE), NODE_STONE_SKIN));
    }
}