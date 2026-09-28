package com.example.modrpg.ai;

import com.example.modrpg.skills.magic.modular.CraftedSpell;
import com.example.modrpg.skills.magic.modular.SpellElement;
import com.example.modrpg.skills.magic.modular.SpellShape;
import com.example.modrpg.skills.magic.modular.SpellTiming;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Set;

public enum EnemyArchetype {

    CRYO_SNIPER(
            "Arquero Glacial",
            "§b",
            SpellElement.FROST,
            SpellShape.PROJECTILE,
            SpellTiming.BALANCED,
            15.0,   // Distancia preferida
            7.0,    // Umbral de kiting (si el jugador está a menos de 7 bloques, retrocede)
            false,  // No embiste cuerpo a cuerpo
            0x33EBFF, // Color de armadura: Cian glacial
            Items.BOW,
            Set.of(EntityType.SKELETON, EntityType.STRAY)
    ),

    FLAME_JUGGERNAUT(
            "Bruto Ígneo",
            "§c",
            SpellElement.FIRE,
            SpellShape.SELF_AURA,
            SpellTiming.BALANCED,
            2.5,    // Distancia preferida cuerpo a cuerpo
            0.0,    // Nunca hace kiting hacia atrás
            true,   // Embiste con agresividad frontal
            0xCC3300, // Color de armadura: Rojo volcánico
            Items.IRON_AXE,
            Set.of(EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED)
    ),

    CRYPT_NECROMANCER(
            "Nigromante de Cripta",
            "§5",
            SpellElement.VOID,
            SpellShape.GROUND_AOE,
            SpellTiming.BALANCED,
            12.0,
            6.0,
            false,
            0x4B0082, // Color de armadura: Púrpura oscuro abisal
            Items.BONE,
            Set.of(EntityType.SKELETON, EntityType.WITHER_SKELETON)
    ),

    VOID_WEAVER(
            "Tejedora del Vacío",
            "§d",
            SpellElement.VOID,
            SpellShape.BEAM,
            SpellTiming.BALANCED,
            6.5,
            3.5,
            false,
            0x9900CC, // Púrpura brillante
            Items.ENDER_EYE,
            Set.of(EntityType.SPIDER, EntityType.CAVE_SPIDER)
    ),

    STORM_EVOKER(
            "Hechicera de Tormentas",
            "§e",
            SpellElement.LIGHTNING,
            SpellShape.PROJECTILE,
            SpellTiming.RAPID_FIRE,
            11.0,
            5.0,
            false,
            0x00E5FF, // Amarillo/Cian eléctrico
            Items.AMETHYST_SHARD,
            Set.of(EntityType.WITCH)
    );

    private final String displayName;
    private final String colorCode;
    private final SpellElement element;
    private final SpellShape shape;
    private final SpellTiming timing;
    private final double preferredDistance;
    private final double kitingThresholdDistance;
    private final boolean aggressiveRush;
    private final int armorColor;
    private final Item mainHandItem;
    private final Set<EntityType<?>> compatibleEntities;

    EnemyArchetype(String displayName, String colorCode, SpellElement element, SpellShape shape, SpellTiming timing,
                   double preferredDistance, double kitingThresholdDistance, boolean aggressiveRush,
                   int armorColor, Item mainHandItem, Set<EntityType<?>> compatibleEntities) {
        this.displayName = displayName;
        this.colorCode = colorCode;
        this.element = element;
        this.shape = shape;
        this.timing = timing;
        this.preferredDistance = preferredDistance;
        this.kitingThresholdDistance = kitingThresholdDistance;
        this.aggressiveRush = aggressiveRush;
        this.armorColor = armorColor;
        this.mainHandItem = mainHandItem;
        this.compatibleEntities = compatibleEntities;
    }

    public String getDisplayName() { return displayName; }
    public String getColorCode() { return colorCode; }
    public SpellElement getElement() { return element; }
    public SpellShape getShape() { return shape; }
    public SpellTiming getTiming() { return timing; }
    public double getPreferredDistance() { return preferredDistance; }
    public double getKitingThresholdDistance() { return kitingThresholdDistance; }
    public boolean isAggressiveRush() { return aggressiveRush; }
    public int getArmorColor() { return armorColor; }
    public Item getMainHandItem() { return mainHandItem; }

    public boolean isCompatibleWith(EntityType<?> type) {
        return compatibleEntities.contains(type);
    }

    public CraftedSpell buildSpell(int powerLevel) {
        return new CraftedSpell(this.displayName, this.element, this.shape, this.timing, powerLevel);
    }
}