```markdown
# Especificación Técnica de Arquitectura e Implementación: ModRPG

## 1. Entorno de Ejecución y Dependencias

* **Plataforma:** Minecraft Java Edition
* **Versión de Runtime:** 1.20.1
* **Cargador de Módulos:** Minecraft Forge `47.4.10+` (Especificación FML `[47,)`)
* **Toolchain / JDK:** OpenJDK 17 LTS (Compatibilidad de bytecode: Java 17)
* **Mapeos de Símbolos:** Mojang Official Mappings (`1.20.1`)
* **Identificador de Módulo (`modid`):** `modrpg`
* **Protocolo de Red:** `modrpg:main` v8
* **Entorno de Pruebas Unitarias:** JUnit 5 (`5.10.2`) / Mockito (`5.11.0`)

---

## 2. Arquitectura General del Sistema

El módulo implementa un modelo desacoplado orientado a eventos, coordinado a través del bus común de Forge (`MinecraftForge.EVENT_BUS`), el bus de ciclo de vida del mod (`FMLJavaModLoadingContext`), una capa de transporte de red binaria (`SimpleChannel`) y la API de Capabilities de Forge para la persistencia del estado en entidades vivas.

```
┌────────────────────────────────────────────────────────────────────────┐
│                          CAPA DE PRESENTACIÓN                          │
│  SkillTreeScreen │ RadialMenuScreen │ SpellCraftingScreen │ Overlays   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Serialización FriendlyByteBuf
┌───────────────────────────────────▼────────────────────────────────────┐
│                           CAPA DE TRANSPORTE                           │
│           ModMessages (SimpleChannel v8 - 9 Paquetes Registrados)      │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ EnqueueWork al hilo del servidor
┌───────────────────────────────────▼────────────────────────────────────┐
│                       LÓGICA DE DOMINIO Y DATOS                        │
│    PlayerSkills (Capability)      ───► SkillRegistry / Nodes           │
│    SkillProgression / Economy     ───► SkillAttributes (Modifiers)     │
│    CraftedSpell (Modular Magic)   ───► ElementalReactionManager        │
│    SquadCoordinator (F.E.A.R. AI) ───► EnemyRpgManager                 │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Pipeline de eventos de bajo nivel
┌───────────────────────────────────▼────────────────────────────────────┐
│                           PIPELINE DE HOOKS                            │
│    ModEvents (LivingHurt, LivingAttack, PlayerTick, LivingFall...)     │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Persistencia de Datos (`PlayerSkills`)

Vinculada a cada instancia de `ServerPlayer` mediante `PlayerSkillsProvider`. Implementa la interfaz `INBTSerializable<CompoundTag>`.

### 3.1 Estructuras de Datos en Memoria
* `branchLevels: Map<ResourceLocation, Integer>`: Nivel actual por rama $[0, 100]$.
* `unlockedNodes: Set<ResourceLocation>`: Identificadores de habilidades adquiridas.
* `practiceCounters: Map<ResourceLocation, Integer>`: Métricas acumulativas de combate y desplazamiento.
* `cooldowns: Map<ResourceLocation, Integer>`: Tiempos de recarga restantes medidos en ticks ($20\text{ ticks} = 1\text{ s}$).
* `equippedSkills: List<ResourceLocation>`: Vector de habilidades asignadas al menú radial ($N_{\text{max}} = 8$).
* `primaryBranch: ResourceLocation` / `secondaryBranch: ResourceLocation`: Especializaciones selladas por el jugador.
* `activeToggles: Set<ResourceLocation>`: Conjunto de posturas activas de drenaje sostenido.
* `spellMemory: CraftedSpell[4]`: Memoria estática para hechizos personalizados del motor modular.
* `selectedSkill: ResourceLocation`: Habilidad activa asignada al disparador manual (`[R]`).
* `currentMana: float` / `maxMana: float`: Reserva energética escalar.
* `ultimateCharged: boolean`: Acumulador booleano de impacto crítico definitivo.
* `dashIFrameTicks: int`: Contador decremental de invulnerabilidad absoluta por esquiva.

### 3.2 Esquema NBT
```
TAG_Compound {
    "BranchLevels": TAG_Compound { (String: ResourceLocation) -> TAG_Int },
    "UnlockedNodes": TAG_List[TAG_String],
    "PracticeCounters": TAG_Compound { (String: ResourceLocation) -> TAG_Int },
    "Cooldowns": TAG_Compound { (String: ResourceLocation) -> TAG_Int },
    "EquippedSkills": TAG_List[TAG_String],
    "ActiveToggles": TAG_List[TAG_String],
    "SpellMemory": TAG_List[TAG_Compound { "Slot": TAG_Int, "Name": TAG_String, ... }],
    "SelectedSkill": TAG_String,
    "PrimaryBranch": TAG_String,
    "SecondaryBranch": TAG_String,
    "CurrentMana": TAG_Float,
    "UltimateCharged": TAG_Byte
}
```

### 3.3 Ciclo de Vida y Persistencia
En eventos `PlayerEvent.Clone`, los datos se copian de la entidad muerta mediante `reviveCaps()` hacia la nueva instancia antes de ejecutar `invalidateCaps()`. El estado transitorio `dashIFrameTicks` se inicializa forzosamente en `0`.

---

## 4. Modelos Matemáticos y Mecánicas de Progresión

### 4.1 Coste de Nivel de Experiencia
Para ascender de un nivel $L$ a $L+1$ en cualquier rama:

$$C(L) = \begin{cases} 
1 & \text{si } L \le 0 \\
100 & \text{si } L \ge 99 \\
\max\left(1, \operatorname{round}\left(1.0 + \left(\frac{L}{99}\right)^{1.6} \times 99.0\right)\right) & \text{si } 0 < L < 99 
\end{cases}$$

### 4.2 Requisito de Práctica Universal
El acumulador de práctica específico de la rama $P$ debe satisfacer:

$$P \ge K(L+1) = (L + 1) \times 3$$

### 4.3 Límites de Especialización (Caps) y Respec
* **Ramas Básicas:** Bloqueadas en Nivel 20 si no han sido selladas como especialización.
* **Rama Secundaria:** Requiere sellado al superar Nivel 20; tope máximo fijado en Nivel 50.
* **Rama Principal (Maestría):** Requiere sellado al superar Nivel 20; permite alcanzar Nivel 100 y acceder a las Habilidades Definitivas.
* **Reinicio (`/rpg respec`):** Desvincula ambas especializaciones y recorta todas las ramas que superen el nivel 20 de vuelta a 20.

### 4.4 Pruebas de Ascensión de Combate
El paso entre niveles clave requiere la ejecución de pruebas contra monstruos élite (`modrpg:elite_kills`):
* **Nivel 25 $\to$ 26 (Prueba del Iniciado):** Requiere $\ge 3$ monstruos Campeones/Élites eliminados.
* **Nivel 50 $\to$ 51 (Prueba del Maestro):** Requiere $\ge 10$ monstruos Campeones/Élites eliminados.
* **Nivel 75 $\to$ 76 (Prueba del Gran Maestro):** Requiere $\ge 25$ monstruos Campeones/Élites eliminados.

### 4.5 Modificadores de Atributos Físicos

#### Daño Cuerpo a Cuerpo (`Attributes.ATTACK_DAMAGE`)
Incremento aditivo transitorio:

$$D_{\text{bonus}}(L_{\text{melee}}) = D_{\text{base}} \times (L_{\text{melee}} \times 0.02)$$

*Escalado estrictamente lineal: $+2\%$ de daño base por nivel ($+200\%$ a nivel 100, multiplicador neto $\times 3.0$).*

#### Velocidad de Movimiento (`Attributes.MOVEMENT_SPEED`)
Modificador aditivo transitorio:

$$V_{\text{bonus}}(L_{\text{mobility}}) = \left(\frac{L_{\text{mobility}}}{100}\right)^{1.5} \times 0.08$$

#### Mitigación de Daño Defensivo
Atenuación escalar del daño entrante antes de armadura:

$$F_{\text{defensa}}(L_{\text{defense}}) = \max\left(0.60, 1.0 - \left(\frac{L_{\text{defense}}}{100}\right)^{1.4} \times 0.40\right)$$

*Mitigación máxima: $40\%$ a nivel 100 ($F = 0.60$).*

#### Daño de Proyectiles (`AbstractArrow`)
Multiplicador escalar de daño directo:

$$M_{\text{distancia}}(L_{\text{ranged}}) = 1.0 + \left(\frac{L_{\text{ranged}}}{100}\right)^{1.5} \times 2.5$$

#### Reserva y Regeneración de Maná
* **Capacidad Máxima:** $M_{\text{max}} = 100.0 + (L_{\text{magic}} \times 2.0)$
* **Tasa de Regeneración:** $R_{\text{mana}} = 2.0 \times (1.0 + L_{\text{magic}} \times 0.05)\text{ puntos/segundo}$
* **Regeneración por Tick:** $\Delta M_{\text{tick}} = \frac{R_{\text{mana}}}{20}$

#### Altura de Paso (`ForgeMod.STEP_HEIGHT_ADDITION`)
Modificador aditivo acumulativo condicionado al estado de los nodos:

$$H_{\text{adicional}} = \sum_{k} h_k, \quad h_k = 0.5 \text{ para } k \in \{\text{LightStep, StepBoostMobility20, StepBoostMelee3}\}$$

*Rango de adición: $[0.0, 1.5]\text{ bloques}$. Altura máxima resultante: $0.6 + 1.5 = 2.1\text{ bloques}$ (permite remontar 2 bloques sin saltar).*

---

## 5. Catálogo de Habilidades por Rama

### 5.1 Cuerpo a Cuerpo (`modrpg:melee`)
* `modrpg:melee_step_boost_3`: Pasiva. $+0.5\text{ m}$ de altura de paso. Req: Nvl 3.
* `modrpg:melee_double_attack`: Pasiva/Activa. Con barra de carga $\ge 92\%$, asesta un segundo impacto recursivo al $80\%$ del daño neto. Req: Nvl 4.
* `modrpg:melee_unarmed_style`: Pasiva. $+10\%$ daño sin armas equipadas; $-5\%$ de daño si porta armas. Req: Nvl 4, `double_attack`.
* `modrpg:melee_weapon_mastery`: Pasiva. $+15\%$ daño con `TieredItem`. $15\%$ de probabilidad de consumir $+1$ durabilidad extra. Req: Nvl 8, `double_attack`.
* `modrpg:melee_leg_trip`: Activa. AABB $3.5 \times 1.0 \times 3.5$. Inflige $1.0\times D_{\text{base}}$, anula inercia y aplica Lentitud IV ($60\text{ ticks}$). Cooldown: $8\text{ s}$. Req: Nvl 6, `unarmed_style`.
* `modrpg:melee_wide_sweep`: Activa. Barrido horizontal de $180^\circ$ en radio de $4.0\text{ m}$. Aplica $1.25\times D_{\text{base}}$ y repulsión. Cooldown: $5\text{ s}$. Req: Nvl 12, `weapon_mastery`.
* `modrpg:melee_ether_dual_sword`: Pasiva/Activa. Ataques con espada invocan una hoja espectral en mano secundaria infligiendo $+75\%$ de daño de tipo mágico indirecto. Req: Nvl 5 Melee, Nvl 6 Magic, `double_attack`.
* `modrpg:melee_berserker_stance`: Pasiva Conmutable (Toggle). Otorga $+30\%$ de daño infligido pero aumenta el daño recibido un $+15\%$. Drena $4.5\text{ maná/s}$. Req: Nvl 8, `double_attack`.
* `modrpg:melee_heavy_tornado`: Activa. Torbellino radial de $5.5\text{ m}$. Inflige $(1.5 \times D_{\text{base}}) \times (1.0 + L_{\text{melee}} \times 0.02)$ y propulsa a los enemigos verticalmente ($Y = +0.45$). Cooldown: $8\text{ s}$. Req: Nvl 10, 20 Bajas CaC, `double_attack`.
* `modrpg:melee_vital_cleave`: Activa. Estocada que inflige el $10\%$ de la salud actual del enemigo. Cooldown dinámico: $CD = \min(400, 200 + \lfloor D_{\text{causado}} \times 4.0 \rfloor)\text{ ticks}$. Coste: $15\text{ maná}$. Req: Nvl 15, `heavy_tornado`.
* `modrpg:melee_megacut`: Activa. Proyección frontal de $12\text{ m}$. Inflige $3.0\times D_{\text{base}}$ en AABB cónico ($r \le 1.5\text{ m}$). Cooldown: $20\text{ s}$. Req: Nvl 50, 100 Bajas CaC, `heavy_tornado`.
* `modrpg:melee_ultracut`: Definitiva (Ultimate). Carga el filo. El próximo ataque físico produce un $+500\%$ de daño crítico ($5.0\times$) con onda expansiva del $50\%$ en $6.0\text{ m}$. Cooldown: $10\text{ min}$ tras el impacto. Req: Nvl 100, 250 Bajas CaC, `megacut`.

### 5.2 Arquería (`modrpg:ranged`)
* `modrpg:ranged_tailwind`: Pasiva. Vector de velocidad de flechas multiplicado por $\times 1.8$. Req: Nvl 5.
* `modrpg:ranged_rapid_fire`: Activa. Dispara instantáneamente 6 proyectiles con dispersión y bonificación de daño base $\times 1.2$. Cooldown: $15\text{ s}$. Req: Nvl 12, `tailwind`.
* `modrpg:ranged_homing_arrow`: Activa. Localiza objetivos en $25\text{ m}$ y dispara una flecha teledirigida con daño $\times 1.8$. Si no hay objetivos, impone $20\text{ s}$ de cooldown de penalización. Cooldown base: $10\text{ s}$. Req: Nvl 16, `tailwind`.
* `modrpg:ranged_crossbow_artillery`: Pasiva. Cohetes disparados con ballestas detonan ondas sónicas infligiendo $+15.0 + (L_{\text{ranged}} \times 0.5)$ de daño adicional. Req: Nvl 20, 30 Bajas Ranged, `tailwind`.
* `modrpg:ranged_hypersonic`: Pasiva. Flechas disparadas agachado (`Shift`) viajan a velocidad $\times 1.5$, sin gravedad (`NoGravity`) y con penetración de 5 entidades. Req: Nvl 50, `tailwind`.

### 5.3 Magia e Invocación (`modrpg:magic`)
* `modrpg:magic_fireball`: Activa. Proyección de fuego en 10 bloques. Inflige $8.0 + (L_{\text{magic}} \times 0.4)$ de daño mágico e ignición. Coste: $20\text{ maná}$. Cooldown: $5\text{ s}$. Req: Nvl 3.
* `modrpg:magic_healing_aura`: Activa. Restaura $6.0 + (L_{\text{magic}} \times 0.2)\text{ PV}$, limpia estados alterados y confiere Regeneración II por $5\text{ s}$. Coste: $35\text{ maná}$. Cooldown: $12\text{ s}$. Req: Nvl 6, `fireball`.
* `modrpg:magic_necrotic_drain`: Pasiva. Cura al usuario el $\max(1.0, D_{\text{infligido}} \times 0.15)\text{ PV}$ al impactar. Req: Nvl 15, `fireball`.
* `modrpg:magic_earth_tune`: Activa. Sintoniza con bloques de tierra/piedra/arena. Consume $4.0$ puntos de saturación de hambre y restaura $40.0\text{ de maná}$. Cooldown: $10\text{ s}$. Req: Nvl 8, `fireball`.
* `modrpg:magic_counter_attack`: Activa. Postura defensiva por $1.5\text{ s}$. Anula el daño entrante y contraataca a 2 enemigos con $18.0$ de daño mágico. Coste: $25\text{ maná}$. Cooldown: $12\text{ s}$. Req: Nvl 12, `fireball`.
* `modrpg:magic_lightning_chain`: Activa. Descarga un arco voltaico que salta entre 3 enemigos en un radio de $8.0\text{ m}$ infligiendo $10.0 + (L_{\text{magic}} \times 0.3)$ de daño. Coste: $30\text{ maná}$. Cooldown: $7\text{ s}$. Req: Nvl 20, `fireball`.
* `modrpg:magic_summon_zombies`: Activa. Invoca 5 zombies infantes blindados durante $20\text{ s}$. Coste: $45\text{ maná}$. Cooldown: $20\text{ s}$. Req: Nvl 10, `fireball`.
* `modrpg:magic_summon_skeletons`: Activa. Invoca 2 esqueletos arqueros con cascos durante $25\text{ s}$. Coste: $50\text{ maná}$. Cooldown: $25\text{ s}$. Req: Nvl 15, `summon_zombies`.
* `modrpg:magic_bee_swarm`: Activa. Asigna 4 abejas enfurecidas contra el objetivo apuntado ($\cos \theta \ge 0.70$) durante $8\text{ s}$. Coste: $35\text{ maná}$. Cooldown: $12\text{ s}$. Req: Nvl 12, `fireball`.
* `modrpg:magic_summon_wolves`: Activa. Invoca 3 lobos espectrales domesticados durante $15\text{ s}$. Coste: $40\text{ maná}$. Cooldown: $18\text{ s}$. Req: Nvl 16, `bee_swarm`.
* `modrpg:custom_spell_[1-4]`: Activas. Disparan los hechizos modulares guardados en las ranuras de memoria 1 a 4. Req: Nvl 1, 10, 25 y 50 respectivamente.

### 5.4 Movilidad (`modrpg:mobility`)
* `modrpg:mobility_light_step`: Pasiva. $+0.5\text{ m}$ de altura de paso. Req: Nvl 2.
* `modrpg:mobility_dash`: Activa. Impulso horizontal $\vec{V} \times 1.5$ e invulnerabilidad absoluta durante $15\text{ ticks}$ ($0.75\text{ s}$). Cooldown: $3\text{ s}$. Req: Nvl 10, `light_step`.
* `modrpg:mobility_air_jump`: Activa. Impulso vertical $Y = +0.95$ conservando momento e inmunidad al siguiente daño de caída. Cooldown: $4\text{ s}$. Req: Nvl 20, `light_step`.
* `modrpg:mobility_flurry_of_strikes`: Activa. Velocidad III por $10\text{ s}$ e impulso hacia adelante ($Y = 1.10$). Cooldown: $12\text{ s}$. Req: Nvl 10, `dash`.
* `modrpg:mobility_impact_jump`: Activa. Impulso vertical $Y = 1.65$. Al impactar contra el suelo detona una explosión de $150.0\text{ de daño}$ en $5.5\text{ m}$ con repulsión de $1.8$. Cooldown: $15\text{ s}$. Req: Nvl 15, `air_jump`.
* `modrpg:mobility_step_boost_20`: Pasiva. $+0.5\text{ m}$ de altura de paso. Req: Nvl 20, `light_step`.

### 5.5 Defensa (`modrpg:defense`)
* `modrpg:defense_stone_skin`: Pasiva. Mitiga el daño entrante final en un $20\%$. Req: Nvl 3.
* `modrpg:defense_push_and_wear`: Pasiva. Ataques aplican empuje $1.5$ y dañan $5$ puntos de durabilidad del arma enemiga. Req: Nvl 8, `stone_skin`.
* `modrpg:defense_iron_strength`: Activa. Durante $12\text{ s}$, los ataques entrantes se anulan y curan $+2.5\text{ PV}$ al jugador por golpe recibido. Cooldown: $30\text{ s}$. Req: Nvl 14, `stone_skin`.
* `modrpg:defense_iron_fortress`: Activa. Confiere Resistencia III y Absorción II durante $10\text{ s}$. Cooldown: $30\text{ s}$. Req: Nvl 20, `iron_strength`.

### 5.6 Sinergias Híbridas
* `modrpg:hybrid_hunter`: Pasiva. Flechas marcan objetivos (`Glowing`). Remates cuerpo a cuerpo consumen la marca e infligen $+150\%$ de daño de vacío ($2.5\times$). Req: Nvl 25 Melee, Nvl 25 Ranged.
* `modrpg:hybrid_arrow_propulsion`: Pasiva. Disparar al suelo con inclinación $> 55^\circ$ impulsa al jugador por el aire ($Y = 1.35$) con Caída Lenta por $4\text{ s}$. Cooldown: $3\text{ s}$. Req: Nvl 35 Melee, Nvl 30 Ranged, `hybrid_hunter`.
* `modrpg:hybrid_sword_quiver`: Pasiva. Las flechas suman $+1.5\times$ del atributo `ATTACK_DAMAGE` CaC del usuario como daño plano adicional. Req: Nvl 70 Melee, Nvl 50 Ranged, `hybrid_hunter`.
* `modrpg:hybrid_combined_ultimate`: Definitiva Combinada. Desatar el Ultracorte Final genera un bombardeo orbital en $7.0\text{ m}$ que inflige el $40\%$ del daño resultante como daño mágico indirecto. Req: Nvl 100 Melee, Nvl 70 Ranged, `ultracut`, `sword_quiver`.

---

## 6. Motor de Magia Modular

Los hechizos son configurados en el Altar de Creación (`SpellCraftingScreen`) e instanciados como objetos inmutables `CraftedSpell`.

### 6.1 Parámetros de Configuración
1. **Elementos (`SpellElement`):**
   * `FIRE`: Daño base: $8.0$ | Maná base: $18.0$ | CD base: $40\text{ ticks}$ | Aplica ignición ($4\text{ s}$).
   * `LIGHTNING`: Daño base: $7.0$ | Maná base: $22.0$ | CD base: $50\text{ ticks}$ | Encadena a una segunda víctima al $50\%$.
   * `FROST`: Daño base: $6.0$ | Maná base: $16.0$ | CD base: $40\text{ ticks}$ | Aplica Lentitud II ($3\text{ s}$).
   * `VOID`: Daño base: $9.0$ | Maná base: $26.0$ | CD base: $60\text{ ticks}$ | Drena el $20\%$ del daño como salud al lanzador.
   * `HOLY`: Daño base: $7.0$ | Maná base: $20.0$ | CD base: $45\text{ ticks}$ | Cura al lanzador en forma de Aura y daña $+75\%$ a no-muertos.
2. **Formas (`SpellShape`):**
   * `PROJECTILE`: $1.0\times$ Daño | $1.0\times$ Maná | $1.0\times$ CD (Dispara `MagicProjectileEntity`).
   * `BEAM`: $0.9\times$ Daño | $1.15\times$ Maná | $0.8\times$ CD (Rayo instantáneo continuo de $16\text{ m}$).
   * `GROUND_AOE`: $1.3\times$ Daño | $1.35\times$ Maná | $1.4\times$ CD (Runa en suelo de $6.0 \times 3.0 \times 6.0\text{ m}$).
   * `SELF_AURA`: $0.8\times$ Daño | $1.1\times$ Maná | $1.2\times$ CD (Estallido radial de $4.0\text{ m}$).
   * `TOUCH`: $1.4\times$ Daño | $0.8\times$ Maná | $0.7\times$ CD (Contacto frontal cuerpo a cuerpo).
3. **Cadencias (`SpellTiming`):**
   * `RAPID_FIRE`: $0.35\times$ Daño | $0.35\times$ Maná | $0.15\times$ CD (Ráfagas ultrarrápidas de $\sim 6\text{ ticks}$).
   * `BALANCED`: $1.0\times$ Daño | $1.0\times$ Maná | $1.0\times$ CD (Equilibrado estándar).
   * `HEAVY_BURST`: $2.6\times$ Daño | $2.2\times$ Maná | $3.2\times$ CD (Detonación masiva lenta).

### 6.2 Fórmulas de Cálculo
* **Daño:** $D = D_{\text{base}} \times M_{\text{forma}} \times M_{\text{cadencia}} \times [1.0 + (P - 1) \times 0.15]$
* **Coste de Maná:** $M = M_{\text{base}} \times M_{\text{forma}} \times M_{\text{cadencia}}$
* **Cooldown:** $CD = \max(5, \lfloor CD_{\text{base}} \times M_{\text{forma}} \times M_{\text{cadencia}} \rfloor)$

---

## 7. Matriz de Reacciones Elementales

El subsistema `ElementalReactionManager` rastrea cebados elementales (`modrpg_primer_elem`) en entidades durante $100\text{ ticks}$ ($5\text{ s}$). Si un elemento opuesto impacta antes de su decaimiento, detona una reacción:

| Reacción | Elementos | Efecto Mecánico |
| :--- | :---: | :--- |
| **Superconductor** | Hielo + Rayo | Detonación sónica en radio de $5.0\text{ m}$. Inflige $14.0$ de daño y aplica Debilidad II por $6\text{ s}$ (-4 ataque y vulnerabilidad física). |
| **Colapso Gravitatorio** | Fuego + Vacío | Vórtice en radio de $6.0\text{ m}$. Succiona a los enemigos hacia el centro con vector $0.85$, inflige $12.0$ de daño e ignición por $5\text{ s}$. |
| **Pira Purificadora** | Fuego + Sagrado | Detonación que cura $+6.0\text{ PV}$ al lanzador, inflige ignición por $8\text{ s}$ y causa daño duplicado contra objetivos no-muertos. |
| **Fragilidad Abisal** | Hielo + Vacío | Aplica Lentitud V por $4\text{ s}$ y etiqueta con `modrpg_brittle_ice`. **El siguiente impacto físico directo rompe el hielo e inflige un $+50\%$ de daño.** |

---

## 8. Sistema de Postura, Telegrafeado e Interrupción (Stagger)

Las entidades con IA avanzada (`TacticalCasterGoal`) canalizan habilidades con señales visuales y sonoras explícitas:

* **Telegrafeado Amarillo (`modrpg_interruptible_charge`):**
  * Señal: Partículas eléctricas y sonido de baliza ambiental. Canalización de $20\text{ ticks}$.
  * **Mecánica de Interrupción:** Si un jugador impacta a la entidad con un ataque cargado ($\ge 85\%$ de fuerza de ataque), se gatilla `interruptCaster()`.
  * **Efecto de Rotura de Postura:** La entidad recibe `modrpg_staggered` por $40\text{ ticks}$ ($2\text{ s}$), se detiene su navegación, se cancela su lanzamiento, libera su token de escuadrón y **recibe un $+30\%$ de daño adicional**.
* **Telegrafeado Rojo Inbloqueable:**
  * Señal: Partículas de fuego y latido de Warden. Canalización de $25\text{ ticks}$.
  * Inmune a interrupción. Exige esquiva obligatoria con `Dash` o bloqueo direccional.

---

## 9. Inteligencia Artificial Enemiga, Escuadrones F.E.A.R. y Campeones

### 9.1 Arquetipos Tácticos (`EnemyArchetype`)
Los monstruos generados son inicializados de forma probabilística según la dificultad regional y el nivel promedio de los jugadores cercanos:
* `CRYO_SNIPER` (Esqueletos): Distancia preferida $15\text{ m}$, retrocede si el jugador se acerca a $< 7\text{ m}$. Magia de Hielo en Proyectil.
* `FLAME_JUGGERNAUT` (Zombies): Asalto frontal agresivo sin kiting, $+15\text{ PV}$, $+0.4$ resistencia a empuje. Magia de Fuego en Aura.
* `CRYPT_NECROMANCER` (Esqueletos/Wither): Distancia $12\text{ m}$. Magia de Vacío en Runa de Área. Invoca sirvientes zombi y realiza saltos evasivos.
* `VOID_WEAVER` (Arañas): Distancia de acoso $6.5\text{ m}$. Magia de Vacío en Rayo instantáneo.
* `STORM_EVOKER` (Brujas): Distancia $11\text{ m}$. Proyectiles eléctricos en ráfaga rápida y autocuración al caer a $< 40\%$ de vida.

### 9.2 Coordinador de Escuadrones (`SquadCoordinator`)
* **Tokens de Disparo:** Los casters de un mismo escuadrón deben reservar el `castingToken` (retención máxima de $40\text{ ticks}$) para evitar saturar al jugador con múltiples proyectiles simultáneos.
* **Maniobras de Peeling (Rescate):** Si un caster a distancia es acosado a $< 5\text{ m}$, emite una señal de auxilio (`requestPeel`). La vanguardia frontal (`FLAME_JUGGERNAUT`) interrumpe su curso actual y carga a velocidad $1.4\times$ contra el agresor.
* **Flanqueo Cruzado:** Los casters buscan posiciones angulares desplazadas entre $60^\circ$ y $90^\circ$ respecto a la línea de visión del jugador con los tanques.
* **Ruptura de Moral:** Si el Líder Comandante del escuadrón es abatido, se reproduce un cuerno de asalto (`RAID_HORN`), los miembros supervivientes quedan inmovilizados, aturdidos por pánico durante $3\text{ s}$ (Lentitud III, Debilidad II) y su navegación se reinicia.

### 9.3 Afijos de Campeones (`ChampionAffix`)
Los líderes de escuadrón pueden ascender a Campeones ($+35\%$ de salud, nombre visible y prefijo):
* `COMMANDER` (`[Comandante]`): Casco dorado, $+0.3$ resistencia a empuje. Emite un aura cada segundo que otorga Velocidad I y Resistencia I a los monstruos aliados en un radio de $12\text{ m}$.
* `RUNIC_SHIELD` (`[Escudo Rúnico]`): Porta escudo en mano secundaria. Bloquea y desvía todo proyectil o flecha frontal que impacte dentro de su cono de visión ($\vec{L} \cdot \vec{P} > 0.2$).
* `VAMPIRIC` (`[Vampírico]`): $+20\%$ daño base. Cura el $25\%$ del daño infligido al atacar.
* `MANA_BURN` (`[Quemador de Maná]`): Porta fragmento de amatista. Drena $20.0$ puntos de maná del jugador por cada golpe asestado.

---

## 10. Subsistema de Esbirros e Invocaciones (`MinionHelper`)

* **Identificación:** Esbirros marcados con `modrpg_minion` y `modrpg_owner_<UUID>`.
* **Tiempo de Vida:** Decrementado por tick en `LivingTickEvent` (`modrpg_lifespan`). Al llegar a 0, la entidad se desvanece de forma limpia vía `.discard()`.
* **Protección de Fuego Amigo:** `LivingAttackEvent` verifica la correlación entre dueño y esbirros o entre esbirros del mismo dueño, cancelando el daño irrevocablemente.
* **Redirección de Agresión:** Al infligir daño el jugador (`LivingHurtEvent`), todos los esbirros en $16.0\text{ m}$ reorientan su IA hacia el objetivo atacado.

---

## 11. Capa de Presentación, GUIs, HUD y Mapeo de Controles

### 11.1 Mapeo de Controles (`KeyBinding`)
* `[Z]` (`key.modrpg.radial_menu`): Abre la Rueda Radial interactiva.
* `[K]` (`key.modrpg.open_skills`): Abre la interfaz visual del Árbol de Habilidades.
* `[O]` (`key.modrpg.spell_crafter`): Abre el Altar de Creación de Hechizos Modulares.
* `[R]` (`key.modrpg.cast_selected`): Ejecuta la habilidad activa o definitiva seleccionada.
* `[V]` / `[B]` / `[G]` / `[X]` / `[C]`: Atajos directos para Torbellino, Megacorte, Dash, Piroclasto y Curación.

### 11.2 Pantalla del Árbol de Habilidades (`SkillTreeScreen`)
* Desplazamiento de cámara libre mediante arrastre y zoom geométrico interactivo $[0.55\times, 1.8\times]$.
* Renderizado de líneas de unión padre-hijo rotadas en el eje Z mediante matrices de `PoseStack`.
* Filtrado dinámico por ramas con máscara de atenuación alfa ($0\text{xFF}$ activa, $0\text{x44}$ atenuada).
* Clic Izquierdo: Compra de nodo / Clic Derecho: Asignación o desasignación en la rueda radial.

### 11.3 Menú Radial (`RadialMenuScreen`)
* Distribución polar de ranuras: $\theta_i = \frac{2\pi}{N} \cdot i - \frac{\pi}{2}$, $R = 80\text{ px}$.
* Soporte para selección de activas y alternancia de estado para posturas (`PASSIVE_TOGGLE`).

### 11.4 HUD Overlays
* `HUD_MANA`: Barra azul situada sobre los muslos de comida. Informa el valor numérico, el drenaje neto por segundo de posturas activas, las insignias de posturas encendidas y el casillero de la habilidad asignada a `[R]`.
* `HUD_SKILLS`: Pila visual de iconos en la esquina inferior izquierda con sombra y cuenta regresiva en segundos para habilidades en cooldown.
* `HUD_CHAMPION`: Barra superior de objetivo para Campeones y Casters enfocados en la mira. Muestra nombre completo, vida numérica, borde distintivo (dorado para campeones, púrpura para casters, parpadeo blanco para postura rota) y alertas de estado (elemento cebado o aturdimiento activo).

---

## 12. Protocolo de Red (`modrpg:main`)

Canal bidireccional registrado mediante `NetworkRegistry.newSimpleChannel`.

| ID | Clase | Dirección | Carga Útil (Payload) | Descripción / Lógica |
| :---: | :--- | :---: | :--- | :--- |
| `0` | `PacketCastSkill` | C $\to$ S | `ResourceLocation skillId` | Valida adquisición, cooldown y maná. Ejecuta `onExecuteActive()`. |
| `1` | `PacketSyncSkillsToClient` | S $\to$ C | Mapas completos, sets, spells NBT, especializaciones | Sincronización íntegra de la capability del jugador al hilo de renderizado del cliente. |
| `2` | `PacketUpgradeSkill` | C $\to$ S | `String branch` | Valida XP, práctica, especializaciones y pruebas de ascensión. Descuenta XP y asciende nivel. |
| `3` | `PacketUnlockNode` | C $\to$ S | `ResourceLocation nodeId` | Valida prerrequisitos de grafo, niveles y consumo. Desbloquea nodo. |
| `4` | `PacketSyncMana` | S $\to$ C | `float currentMana, float maxMana` | Sincronización continua de maná cada segundo o tras transacciones. |
| `5` | `PacketEquipSkill` | C $\to$ S | `ResourceLocation skillId` | Asigna o desequipa nodos de la rueda radial ($N_{\text{max}} = 8$). |
| `6` | `PacketSelectSkill` | C $\to$ S | `ResourceLocation skillId` | Asigna una habilidad activa al disparador rápido `[R]`. |
| `7` | `PacketTogglePassive` | C $\to$ S | `ResourceLocation skillId` | Alterna el encendido/apagado de posturas de drenaje sostenido. |
| `8` | `PacketSaveCraftedSpell` | C $\to$ S | `int slotIndex, CompoundTag spellNBT` | Guarda un hechizo modular en una de las 4 ranuras de memoria. |

---

## 13. Interfaz de Comandos (`/rpg`)

* `/rpg stats`: Imprime las especializaciones selladas, niveles por rama, métricas de práctica acumuladas y tasa de regeneración de maná. Permiso: `0`.
* `/rpg respec`: Reinicia los votos de especialización (principal y secundaria) y recorta los niveles de rama que superen el nivel 20 de vuelta al tope base (20). Permiso: `0`.
* `/rpg upgrade <branch>`: Asciende un nivel en la rama indicada descontando niveles de XP de Minecraft y validando requisitos de práctica. Permiso: `0`.
* `/rpg addlevel <branch> <amount>`: Agrega arbitrariamente niveles $[1, 100]$ a una rama y recalcula los modificadores de atributos. Permiso: `2` (Operador).
* `/rpg unlock <skill>`: Concede de forma forzada una habilidad ignorando requisitos de nivel, XP o prerrequisitos de grafo. Permiso: `2` (Operador).
```
