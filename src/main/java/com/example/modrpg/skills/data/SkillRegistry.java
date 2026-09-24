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

    // Ramas
    public static final ResourceLocation BRANCH_MELEE    = new ResourceLocation(ModRpg.MODID, "melee");
    public static final ResourceLocation BRANCH_RANGED   = new ResourceLocation(ModRpg.MODID, "ranged");
    public static final ResourceLocation BRANCH_MOBILITY = new ResourceLocation(ModRpg.MODID, "mobility");
    public static final ResourceLocation BRANCH_MAGIC    = new ResourceLocation(ModRpg.MODID, "magic");
    public static final ResourceLocation BRANCH_DEFENSE  = new ResourceLocation(ModRpg.MODID, "defense");

    // Práctica
    public static final ResourceLocation COUNTER_MELEE_KILLS   = new ResourceLocation(ModRpg.MODID, "melee_kills");
    public static final ResourceLocation COUNTER_RANGED_KILLS  = new ResourceLocation(ModRpg.MODID, "ranged_kills");
    public static final ResourceLocation COUNTER_MAGIC_CASTS   = new ResourceLocation(ModRpg.MODID, "magic_casts");
    public static final ResourceLocation COUNTER_DAMAGE_BLOCKED= new ResourceLocation(ModRpg.MODID, "damage_blocked");
    public static final ResourceLocation COUNTER_DISTANCE_RUN  = new ResourceLocation(ModRpg.MODID, "distance_run");

    // Melee (Existentes + Sprint 3)
    public static final ResourceLocation NODE_DOUBLE_ATTACK     = new ResourceLocation(ModRpg.MODID, "melee_double_attack");
    public static final ResourceLocation NODE_ETHER_DUAL_SWORD  = new ResourceLocation(ModRpg.MODID, "melee_ether_dual_sword");
    public static final ResourceLocation NODE_HEAVY_TORNADO     = new ResourceLocation(ModRpg.MODID, "melee_heavy_tornado");
    public static final ResourceLocation NODE_MEGACUT           = new ResourceLocation(ModRpg.MODID, "melee_megacut");
    public static final ResourceLocation NODE_ULTRACUT          = new ResourceLocation(ModRpg.MODID, "melee_ultracut");
    public static final ResourceLocation NODE_UNARMED_STYLE     = new ResourceLocation(ModRpg.MODID, "melee_unarmed_style");
    public static final ResourceLocation NODE_WEAPON_MASTERY    = new ResourceLocation(ModRpg.MODID, "melee_weapon_mastery");
    public static final ResourceLocation NODE_LEG_TRIP          = new ResourceLocation(ModRpg.MODID, "melee_leg_trip");
    public static final ResourceLocation NODE_WIDE_SWEEP        = new ResourceLocation(ModRpg.MODID, "melee_wide_sweep");

    // Ranged (Existentes + Sprint 3)
    public static final ResourceLocation NODE_TAILWIND          = new ResourceLocation(ModRpg.MODID, "ranged_tailwind");
    public static final ResourceLocation NODE_CROSSBOW_ARTILLERY= new ResourceLocation(ModRpg.MODID, "ranged_crossbow_artillery");
    public static final ResourceLocation NODE_HYPERSONIC        = new ResourceLocation(ModRpg.MODID, "ranged_hypersonic");
    public static final ResourceLocation NODE_RAPID_FIRE        = new ResourceLocation(ModRpg.MODID, "ranged_rapid_fire");
    public static final ResourceLocation NODE_HOMING_ARROW      = new ResourceLocation(ModRpg.MODID, "ranged_homing_arrow");

    // Sinergias Híbridas
    public static final ResourceLocation NODE_HYBRID_HUNTER     = new ResourceLocation(ModRpg.MODID, "hybrid_hunter");
    public static final ResourceLocation NODE_ARROW_PROPULSION  = new ResourceLocation(ModRpg.MODID, "hybrid_arrow_propulsion");
    public static final ResourceLocation NODE_SWORD_QUIVER      = new ResourceLocation(ModRpg.MODID, "hybrid_sword_quiver");
    public static final ResourceLocation NODE_COMBINED_ULTIMATE = new ResourceLocation(ModRpg.MODID, "hybrid_combined_ultimate");

    // Magia y Elementos
    public static final ResourceLocation NODE_FIREBALL          = new ResourceLocation(ModRpg.MODID, "magic_fireball");
    public static final ResourceLocation NODE_HEALING_AURA      = new ResourceLocation(ModRpg.MODID, "magic_healing_aura");
    public static final ResourceLocation NODE_NECROTIC_DRAIN    = new ResourceLocation(ModRpg.MODID, "magic_necrotic_drain");
    public static final ResourceLocation NODE_EARTH_TUNE        = new ResourceLocation(ModRpg.MODID, "magic_earth_tune");
    public static final ResourceLocation NODE_COUNTER_ATTACK    = new ResourceLocation(ModRpg.MODID, "magic_counter_attack");
    public static final ResourceLocation NODE_LIGHTNING_CHAIN   = new ResourceLocation(ModRpg.MODID, "magic_lightning_chain");
    public static final ResourceLocation NODE_SUMMON_ZOMBIES   = new ResourceLocation(ModRpg.MODID, "magic_summon_zombies");
    public static final ResourceLocation NODE_SUMMON_SKELETONS = new ResourceLocation(ModRpg.MODID, "magic_summon_skeletons");
    public static final ResourceLocation NODE_BEE_SWARM        = new ResourceLocation(ModRpg.MODID, "magic_bee_swarm");
    public static final ResourceLocation NODE_SUMMON_WOLVES    = new ResourceLocation(ModRpg.MODID, "magic_summon_wolves");

    // Movilidad
    public static final ResourceLocation NODE_LIGHT_STEP        = new ResourceLocation(ModRpg.MODID, "mobility_light_step");
    public static final ResourceLocation NODE_DASH              = new ResourceLocation(ModRpg.MODID, "mobility_dash");
    public static final ResourceLocation NODE_AIR_JUMP          = new ResourceLocation(ModRpg.MODID, "mobility_air_jump");

    // Defensa (Existentes + Sprint 3)
    public static final ResourceLocation NODE_STONE_SKIN        = new ResourceLocation(ModRpg.MODID, "defense_stone_skin");
    public static final ResourceLocation NODE_IRON_FORTRESS     = new ResourceLocation(ModRpg.MODID, "defense_iron_fortress");
    public static final ResourceLocation NODE_PUSH_AND_WEAR     = new ResourceLocation(ModRpg.MODID, "defense_push_and_wear");
    public static final ResourceLocation NODE_IRON_STRENGTH     = new ResourceLocation(ModRpg.MODID, "defense_iron_strength");

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

        // 1. REGISTRO DE RAMAS
        registerBranch(new SkillBranch(BRANCH_MELEE,    Component.literal("Cuerpo a Cuerpo"), 10, COUNTER_MELEE_KILLS));
        registerBranch(new SkillBranch(BRANCH_RANGED,   Component.literal("Arquería"),         5,  COUNTER_RANGED_KILLS));
        registerBranch(new SkillBranch(BRANCH_MAGIC,    Component.literal("Magia"),            5,  COUNTER_MAGIC_CASTS));
        registerBranch(new SkillBranch(BRANCH_DEFENSE,  Component.literal("Defensa"),          8,  COUNTER_DAMAGE_BLOCKED));
        registerBranch(new SkillBranch(BRANCH_MOBILITY, Component.literal("Movilidad"),       10, COUNTER_DISTANCE_RUN));

        // 2. RAMA CUERPO A CUERPO
        register(new DoubleAttackSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 4))
                .setVisuals(0, -60, new ItemStack(Items.IRON_SWORD)));

        register(new UnarmedStyleSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 4))
                .setVisuals(-40, -50, new ItemStack(Items.LEATHER), NODE_DOUBLE_ATTACK));

        register(new WeaponMasterySkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 8))
                .setVisuals(40, -50, new ItemStack(Items.GOLDEN_SWORD), NODE_DOUBLE_ATTACK));

        register(new LegTripSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 6))
                .setVisuals(-40, -90, new ItemStack(Items.IRON_BOOTS), NODE_UNARMED_STYLE));

        register(new WideSweepSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 12))
                .setVisuals(40, -90, new ItemStack(Items.IRON_AXE), NODE_WEAPON_MASTERY));

        register(new EtherDualSwordSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 5))
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MAGIC, 6))
                .setVisuals(-60, -130, new ItemStack(Items.AMETHYST_SHARD), NODE_DOUBLE_ATTACK));

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

        // 3. RAMA ARQUERÍA
        register(new TailwindSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_RANGED, 5))
                .setVisuals(70, 0, new ItemStack(Items.BOW)));

        register(new RapidFireSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_RANGED, 12))
                .setVisuals(130, -15, new ItemStack(Items.DISPENSER), NODE_TAILWIND));

        register(new HomingArrowSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_RANGED, 16))
                .setVisuals(130, 25, new ItemStack(Items.COMPASS), NODE_TAILWIND));

        register(new CrossbowArtillerySkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_RANGED, 20))
                .addRequirement(SkillRequirement.practice(COUNTER_RANGED_KILLS, 30, "bajas proyectil"))
                .setVisuals(180, -40, new ItemStack(Items.CROSSBOW), NODE_TAILWIND));

        register(new HypersonicArrowSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_RANGED, 50))
                .setVisuals(190, 40, new ItemStack(Items.SPECTRAL_ARROW), NODE_TAILWIND));

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

        // 5. RAMA MAGIA Y ELEMENTOS
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

        register(new SummonZombiesSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MAGIC, 10))
                .setVisuals(-240, -80, new ItemStack(Items.ZOMBIE_HEAD), NODE_FIREBALL));

        register(new SummonSkeletonsSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MAGIC, 15))
                .setVisuals(-300, -80, new ItemStack(Items.SKELETON_SKULL), NODE_SUMMON_ZOMBIES));

        register(new BeeSwarmSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MAGIC, 12))
                .setVisuals(-240, 80, new ItemStack(Items.HONEYCOMB), NODE_FIREBALL));

        register(new SummonWolvesSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MAGIC, 16))
                .setVisuals(-300, 80, new ItemStack(Items.BONE), NODE_BEE_SWARM));

        // 6. RAMA MOVILIDAD
        register(new LightStepSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MOBILITY, 2))
                .setVisuals(0, 70, new ItemStack(Items.LEATHER_BOOTS)));

        register(new DashSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MOBILITY, 10))
                .setVisuals(-60, 140, new ItemStack(Items.SUGAR), NODE_LIGHT_STEP));

        register(new AirJumpSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MOBILITY, 20))
                .setVisuals(60, 140, new ItemStack(Items.PHANTOM_MEMBRANE), NODE_LIGHT_STEP));

        // 7. RAMA DEFENSA
        register(new StoneSkinSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_DEFENSE, 3))
                .setVisuals(100, 90, new ItemStack(Items.SHIELD)));

        register(new PushAndWearSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_DEFENSE, 8))
                .setVisuals(140, 80, new ItemStack(Items.IRON_SWORD), NODE_STONE_SKIN));

        register(new IronStrengthSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_DEFENSE, 14))
                .setVisuals(140, 130, new ItemStack(Items.GOLDEN_APPLE), NODE_STONE_SKIN));

        register(new IronFortressSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_DEFENSE, 20))
                .setVisuals(180, 150, new ItemStack(Items.IRON_CHESTPLATE), NODE_IRON_STRENGTH));
    }
}