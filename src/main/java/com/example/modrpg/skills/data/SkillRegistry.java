package com.example.modrpg.skills.data;

import com.example.modrpg.ModRpg;
import com.example.modrpg.skills.nodes.defense.*;
import com.example.modrpg.skills.nodes.hybrid.*;
import com.example.modrpg.skills.nodes.magic.*;
import com.example.modrpg.skills.nodes.melee.*;
import com.example.modrpg.skills.nodes.mobility.*;
import com.example.modrpg.skills.nodes.ranged.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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

    // CaC
    public static final ResourceLocation NODE_DOUBLE_ATTACK     = new ResourceLocation(ModRpg.MODID, "melee_double_attack");
    public static final ResourceLocation NODE_ETHER_DUAL_SWORD  = new ResourceLocation(ModRpg.MODID, "melee_ether_dual_sword");
    public static final ResourceLocation NODE_HEAVY_TORNADO     = new ResourceLocation(ModRpg.MODID, "melee_heavy_tornado");
    public static final ResourceLocation NODE_MEGACUT           = new ResourceLocation(ModRpg.MODID, "melee_megacut");
    public static final ResourceLocation NODE_ULTRACUT          = new ResourceLocation(ModRpg.MODID, "melee_ultracut");

    // Arquería
    public static final ResourceLocation NODE_TAILWIND          = new ResourceLocation(ModRpg.MODID, "ranged_tailwind");
    public static final ResourceLocation NODE_CROSSBOW_ARTILLERY= new ResourceLocation(ModRpg.MODID, "ranged_crossbow_artillery");
    public static final ResourceLocation NODE_HYPERSONIC        = new ResourceLocation(ModRpg.MODID, "ranged_hypersonic");

    // Híbridos
    public static final ResourceLocation NODE_HYBRID_HUNTER     = new ResourceLocation(ModRpg.MODID, "hybrid_hunter");
    public static final ResourceLocation NODE_ARROW_PROPULSION  = new ResourceLocation(ModRpg.MODID, "hybrid_arrow_propulsion");
    public static final ResourceLocation NODE_SWORD_QUIVER      = new ResourceLocation(ModRpg.MODID, "hybrid_sword_quiver");
    public static final ResourceLocation NODE_COMBINED_ULTIMATE = new ResourceLocation(ModRpg.MODID, "hybrid_combined_ultimate");

    // Magia
    public static final ResourceLocation NODE_FIREBALL          = new ResourceLocation(ModRpg.MODID, "magic_fireball");
    public static final ResourceLocation NODE_HEALING_AURA      = new ResourceLocation(ModRpg.MODID, "magic_healing_aura");
    public static final ResourceLocation NODE_NECROTIC_DRAIN    = new ResourceLocation(ModRpg.MODID, "magic_necrotic_drain");

    // Movilidad
    public static final ResourceLocation NODE_LIGHT_STEP        = new ResourceLocation(ModRpg.MODID, "mobility_light_step");
    public static final ResourceLocation NODE_DASH              = new ResourceLocation(ModRpg.MODID, "mobility_dash");
    public static final ResourceLocation NODE_AIR_JUMP          = new ResourceLocation(ModRpg.MODID, "mobility_air_jump");

    // Defensa
    public static final ResourceLocation NODE_STONE_SKIN        = new ResourceLocation(ModRpg.MODID, "defense_stone_skin");
    public static final ResourceLocation NODE_IRON_FORTRESS     = new ResourceLocation(ModRpg.MODID, "defense_iron_fortress");

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

    public static void init() {
        NODES.clear();

        // 1. RAMA CUERPO A CUERPO (Arriba)
        register(new DoubleAttackSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 4))
                .setVisuals(0, -60, null, new ItemStack(Items.IRON_SWORD)));

        register(new EtherDualSwordSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 5))
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MAGIC, 6))
                .setVisuals(-60, -110, NODE_DOUBLE_ATTACK, new ItemStack(Items.AMETHYST_SHARD)));

        register(new HeavyTornadoSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 10))
                .addRequirement(SkillRequirement.practice(COUNTER_MELEE_KILLS, 20, "bajas CaC"))
                .setVisuals(0, -130, NODE_DOUBLE_ATTACK, new ItemStack(Items.DIAMOND_SWORD)));

        register(new MegacutSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 50))
                .addRequirement(SkillRequirement.practice(COUNTER_MELEE_KILLS, 100, "bajas CaC"))
                .setVisuals(0, -200, NODE_HEAVY_TORNADO, new ItemStack(Items.NETHERITE_SWORD)));

        register(new UltracutSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 100))
                .addRequirement(SkillRequirement.practice(COUNTER_MELEE_KILLS, 250, "bajas CaC"))
                .setVisuals(0, -280, NODE_MEGACUT, new ItemStack(Items.NETHER_STAR)));

        // 2. RAMA ARQUERÍA (Derecha)
        register(new TailwindSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_RANGED, 5))
                .setVisuals(70, 0, null, new ItemStack(Items.BOW)));

        register(new CrossbowArtillerySkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_RANGED, 20))
                .addRequirement(SkillRequirement.practice(COUNTER_RANGED_KILLS, 30, "bajas proyectil"))
                .setVisuals(150, -40, NODE_TAILWIND, new ItemStack(Items.CROSSBOW)));

        register(new HypersonicArrowSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_RANGED, 50))
                .setVisuals(160, 40, NODE_TAILWIND, new ItemStack(Items.SPECTRAL_ARROW)));

        // 3. SINERGIAS HÍBRIDAS (Diagonal Arriba-Derecha)
        register(new HybridHunterSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 25))
                .addRequirement(SkillRequirement.branchLevel(BRANCH_RANGED, 25))
                .setVisuals(80, -90, NODE_DOUBLE_ATTACK, new ItemStack(Items.ENDER_EYE)));

        register(new ArrowPropulsionSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 35))
                .addRequirement(SkillRequirement.branchLevel(BRANCH_RANGED, 30))
                .setVisuals(140, -110, NODE_HYBRID_HUNTER, new ItemStack(Items.FEATHER)));

        register(new SwordQuiverSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 70))
                .addRequirement(SkillRequirement.branchLevel(BRANCH_RANGED, 50))
                .setVisuals(100, -190, NODE_HYBRID_HUNTER, new ItemStack(Items.ARROW)));

        register(new CombinedUltimateSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MELEE, 100))
                .addRequirement(SkillRequirement.branchLevel(BRANCH_RANGED, 70))
                .setVisuals(90, -280, NODE_ULTRACUT, new ItemStack(Items.DRAGON_BREATH)));

        // 4. RAMA MAGIA Y HECHICERÍA (Izquierda)
        register(new FireballSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MAGIC, 3))
                .setVisuals(-80, 0, null, new ItemStack(Items.FIRE_CHARGE)));

        register(new HealingAuraSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MAGIC, 6))
                .setVisuals(-160, -50, NODE_FIREBALL, new ItemStack(Items.GLISTERING_MELON_SLICE)));

        register(new NecroticDrainSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MAGIC, 15))
                .setVisuals(-160, 50, NODE_FIREBALL, new ItemStack(Items.WITHER_SKELETON_SKULL)));

        // 5. RAMA MOVILIDAD (Abajo)
        register(new LightStepSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MOBILITY, 2))
                .setVisuals(0, 70, null, new ItemStack(Items.LEATHER_BOOTS)));

        register(new DashSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MOBILITY, 10))
                .setVisuals(-60, 140, NODE_LIGHT_STEP, new ItemStack(Items.SUGAR)));

        register(new AirJumpSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_MOBILITY, 20))
                .setVisuals(60, 140, NODE_LIGHT_STEP, new ItemStack(Items.PHANTOM_MEMBRANE)));

        // 6. RAMA DEFENSA (Abajo a la Derecha)
        register(new StoneSkinSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_DEFENSE, 3))
                .setVisuals(100, 90, null, new ItemStack(Items.SHIELD)));

        register(new IronFortressSkill()
                .addRequirement(SkillRequirement.branchLevel(BRANCH_DEFENSE, 20))
                .setVisuals(170, 150, NODE_STONE_SKIN, new ItemStack(Items.IRON_CHESTPLATE)));

        ModRpg.LOGGER.info(">>> [SkillRegistry] ¡Árbol radial completo: {} nodos cargados! <<<", NODES.size());
    }
}