# ⚔️ ModRPG

> **A deep RPG progression system for Minecraft Java Edition.**
>
> Build your character through massive skill branches, unlock active abilities, combine different playstyles, master magic, and face enemies that evolve alongside you.

**ModRPG** is an RPG progression framework for Minecraft focused on **long-term character development, specialization and combat variety**.

Instead of simply giving the player stronger equipment, ModRPG lets your character **become stronger through practice and specialization**.

---

## ✨ Features

### 🌳 Deep Skill Tree

Build your character through multiple interconnected skill branches.

Current main branches include:

- ⚔️ **Melee**
- 🏹 **Ranged**
- 🏃 **Mobility**
- 🛡️ **Defense**
- 🔮 **Magic**

Each branch contains different types of nodes:

- Passive stat bonuses
- Passive toggles
- Active abilities
- Hybrid synergies
- Ultimate abilities

Skills can have prerequisites, branch-level requirements, practice requirements and dependencies on previous nodes.

The skill tree is displayed through a dedicated in-game interface for navigating and managing your progression.

---

## 📈 Character Progression

Each skill branch has its own level.

Branch progression can reach **level 100**, with the cost of advancement increasing as the player approaches higher levels.

Progression is not based exclusively on spending XP.

Each branch also tracks its own **practice**, encouraging players to actually use the playstyle they want to develop.

For example:

- ⚔️ Melee → progress through melee combat
- 🏹 Ranged → progress through ranged combat
- 🔮 Magic → progress through spellcasting
- 🛡️ Defense → progress through defensive gameplay
- 🏃 Mobility → progress through movement

The result is a progression system where **what you do affects what you become**.

### 📊 Scaling Philosophy

Low levels are intentionally subtle.

As the player invests further into a specialization, the bonuses become increasingly noticeable.

The progression system includes scaling for areas such as:

- Melee damage
- Mobility
- Defensive mitigation
- Ranged damage
- XP costs
- Practice requirements

The goal is for high-level specialists to feel dramatically different from newly trained characters.

---

# ⚔️ Melee

The melee branch focuses on close-range combat, weapon mastery and high-impact abilities.

Examples include:

- **Double Attack**
- **Wide Sweep**
- **Heavy Tornado**
- **Leg Trip**
- **Vital Cleave**
- **Berserker Stance**
- **Weapon Mastery**
- **MegaCut**
- **UltraCut**
- **Ether Dual Sword**
- **Unarmed Style**

The branch contains both passive improvements and abilities that fundamentally change how the player fights.

---

# 🏹 Ranged

The ranged branch focuses on bows, arrows, crossbows and specialized projectile mechanics.

Current abilities include:

### 🌪️ Tailwind

Improves the initial velocity of arrows.

### 🔥 Rapid Fire

Fires multiple arrows in rapid succession.

### 🎯 Homing Arrow

Searches for a nearby target and redirects a projectile toward it.

### 💥 Crossbow Artillery

Adds special interactions for crossbow rockets, including explosive effects and additional damage.

### ⚡ Hypersonic Arrow

A high-level ranged ability that can dramatically increase projectile speed, remove gravity and provide strong piercing capabilities.

---

# 🏃 Mobility

Mobility is more than simply moving faster.

It includes movement abilities that allow the player to turn movement itself into a combat mechanic.

Examples include:

- Dash
- Air Jump
- Impact Jump
- Light Step
- Flurry of Strikes
- Mobility-based melee improvements

Some abilities interact with combat events, movement and defensive mechanics.

---

# 🛡️ Defense

The Defense branch focuses on survivability and defensive mechanics.

Current abilities include:

- **Stone Skin**
- **Push and Wear**
- **Iron Strength**
- **Iron Fortress**

Defense progression can reduce incoming damage while advanced abilities can introduce additional defensive mechanics.

---

# 🔮 Magic

Magic is designed as its own progression system rather than simply being a collection of predefined spells.

The mod includes:

- Mana
- Magical projectiles
- Elements
- Spell shapes
- Spell timing
- Spell crafting
- Elemental reactions
- Magical summons
- Healing
- Offensive spells
- Magical status effects

Current elements include:

- 🔥 Fire
- ⚡ Lightning
- ❄️ Frost
- 🕳️ Void
- ✨ Holy

---

## 🧪 Modular Spell Crafting

Players can create custom spells by combining different properties.

A crafted spell contains:

**Element + Shape + Timing + Power**

This allows the player to create different spell configurations rather than relying exclusively on fixed abilities.

Crafted spells can be saved and reconstructed through NBT data.

---

## ⚡ Elemental Reactions

Magic can also interact through elemental combinations.

Enemies can become temporarily **primed with an element**, allowing subsequent elemental attacks to trigger reactions.

Examples currently implemented include:

- ❄️ Frost + ⚡ Lightning
- 🔥 Fire + 🕳️ Void
- 🔥 Fire + ✨ Holy
- ❄️ Frost + 🕳️ Void

This creates a combat loop where the player can deliberately set up elemental combinations instead of simply spamming the strongest spell.

---

# 🔀 Hybrid Skills

Specialization doesn't necessarily mean choosing only one branch.

ModRPG includes **Hybrid Synergy** nodes that connect different playstyles.

Examples include:

- 🏹⚔️ Sword + Bow interactions
- Projectile/mobility combinations
- Hybrid hunter abilities
- Combined ultimate abilities

The intention is to reward players who invest in multiple branches and discover combinations between them.

---

# 👹 RPG Enemies

Character progression is only half of the system.

ModRPG also introduces RPG-style enemy progression.

Enemies can dynamically receive:

- Different archetypes
- Specialized equipment
- Spells
- Increased power
- Champion status
- Special affixes
- Tactical behavior

Enemy power takes into account factors such as **world progression and nearby players' RPG progression**.

This allows the world to become increasingly dangerous as the player becomes stronger.

---

# 🧠 Tactical Enemy AI

Enemies are not intended to simply have more HP and damage.

The mod includes tactical behavior for specialized casters.

Enemies can:

- Kite players
- Retreat from dangerous situations
- Flank
- Coordinate with nearby mobs
- Request assistance
- Protect important targets
- Use interruptible cast attacks
- React to stagger states
- Coordinate through squads

Enemies can also form tactical groups with a leader and shared combat behavior.

---

# 👑 Champions

Some enemies can become **Champions**.

Champions receive special modifiers called **Affixes**.

Current affixes include:

- **Commander**
- **Runic Shield**
- **Vampiric**
- **Mana Burn**

Champions also receive additional visual and combat characteristics, making them more dangerous encounters than ordinary mobs.

---

# 💥 Combat Feedback

Abilities are designed to communicate their effects visually and audibly.

The mod uses:

- Particles
- Sounds
- Explosions
- Knockback
- Special projectile behavior
- Combat messages
- Cooldown indicators
- Mana display
- Champion HUD
- Stagger feedback

The goal is for powerful abilities to **feel powerful**, not simply modify an invisible number.

---

# 🎮 In-Game Interfaces

ModRPG includes several dedicated interfaces.

### 🌳 Skill Tree

Open the RPG skill tree to:

- Browse branches
- Inspect requirements
- Unlock abilities
- Track progression
- Equip skills
- View branch levels
- Monitor practice
- Navigate large sections of the tree

### 🎡 Radial Skill Menu

Quickly access equipped abilities through a radial menu.

Active abilities, ultimates and toggleable passives can be assigned to the combat loadout.

### 🔮 Spell Crafter

Create and save custom spells through the modular magic system.

### 📊 Combat HUD

The mod provides additional information such as:

- Mana
- Skill cooldowns
- Target information
- Champion status
- Stagger status
- Elemental effects

---

# ⌨️ Controls

Default keybinds currently include:

| Key | Action |
|---|---|
| `Z` | Skill radial menu |
| `K` | Skill tree |
| `O` | Spell crafting |
| `R` | Selected skill |
| `V` | Spin attack |
| `B` | MegaCut |
| `G` | Dash |
| `X` | Fireball |
| `C` | Heal |

> Keybindings may be changed through Minecraft's controls menu.

---

# 🧩 Technical Architecture

The project is built as a modular Forge mod.

Major systems are separated into:

```text
com.example.modrpg/
├── ai/
│   ├── ChampionAffix
│   ├── EnemyArchetype
│   ├── EnemyRpgManager
│   ├── SquadCoordinator
│   └── TacticalCasterGoal
│
├── client/
│   ├── SkillTreeScreen
│   ├── SpellCraftingScreen
│   ├── RadialMenuScreen
│   ├── ChampionOverlay
│   ├── ManaOverlay
│   └── SkillCooldownOverlay
│
├── networking/
│   ├── Skill synchronization
│   ├── Skill unlocking
│   ├── Skill upgrading
│   ├── Skill equipping
│   ├── Mana synchronization
│   └── Spell synchronization
│
└── skills/
    ├── data/
    ├── magic/
    └── nodes/
        ├── melee/
        ├── ranged/
        ├── mobility/
        ├── defense/
        ├── magic/
        └── hybrid/
```

The skill system is built around reusable `SkillNode` objects with different node types and configurable requirements.

---

# 📐 Progression System

The current progression system uses a nonlinear XP cost curve.

The cost of advancing from level `L` to `L + 1` is approximately:

```text
C(L) = 1 + (L / 99)^1.6 × 99
```

with the cost capped at **100 XP levels** near the end of the progression.

Practice requirements also scale with the desired level:

```text
Required Practice = Next Level × 3
```

This means reaching the highest levels requires both:

**XP + Practice**

rather than simply accumulating XP.

---

# 🧪 Testing

The project includes unit tests for parts of the progression system.

Current test coverage includes:

- `PlayerSkillsTest`
- `SkillProgressionTest`

The Gradle project is configured for JUnit 5 and Mockito.

---

# 🚧 Development Status

**Early development / prototype**

The current version already contains the core architecture for:

- Skill progression
- Multiple RPG branches
- Active abilities
- Passive abilities
- Hybrid abilities
- Ultimate abilities
- Practice-based progression
- Mana
- Modular spell crafting
- Elemental reactions
- RPG enemies
- Champions
- Tactical enemy AI
- Skill tree UI
- Radial ability UI
- Combat HUD

However, the project is still under active development and some systems may require balancing, polishing, additional content, animation work, multiplayer testing and further optimization.

Expect bugs.  
Expect balance problems.  
Expect some abilities to be completely ridiculous.

That's part of the fun.™

---

# 🗺️ Roadmap

Potential future development includes:

- [ ] More skill branches
- [ ] More hybrid branches
- [ ] More ultimate abilities
- [ ] More weapon-specific abilities
- [ ] Additional magic elements
- [ ] More elemental reactions
- [ ] More enemy archetypes
- [ ] More champion affixes
- [ ] Additional tactical behaviors
- [ ] More visual effects
- [ ] Better animations
- [ ] Expanded sound design
- [ ] More progression content
- [ ] Multiplayer balancing
- [ ] Configuration options
- [ ] CurseForge release
- [ ] Documentation/wiki

---

# 📜 License

See [`LICENSE.txt`](LICENSE.txt) for the current license.

---

# ❤️ Credits

Created by **[Your Name]**

Built with:

- Minecraft
- Minecraft Forge
- Java
- Gradle
- JUnit
- Mockito

---

## 💭 Philosophy

> **Don't just give the player better gear.**
>
> **Give them a reason to become better.**

ModRPG is built around the idea that progression should be something the player **experiences through gameplay**.

Fight to become a better fighter.

Run to become faster.

Cast to become a better mage.

Survive to become harder to kill.

And eventually...

**become something that vanilla Minecraft was never designed to contain.**
