# Especificación Técnica de Arquitectura e Implementación: ModRPG

## 1. Entorno de Ejecución y Dependencias

* **Plataforma:** Minecraft Java Edition
* **Versión de Runtime:** 1.20.1
* **Cargador de Módulos:** Minecraft Forge `47.4.10+` (Especificación FML `[47,)`)
* **Toolchain / Java Development Kit:** OpenJDK 17 LTS (Lenguaje fuente / Bytecode objetivo: Nivel 17)
* **Mapeos de Símbolos:** Mojang Official Mappings (`1.20.1`)
* **Identificador de Módulo (`modid`):** `modrpg`
* **Protocolo de Comunicación en Red:** `modrpg:main` v5

---

## 2. Arquitectura del Sistema

El sistema implementa una arquitectura orientada a eventos, desacoplada mediante el canal de eventos de Forge (`MinecraftForge.EVENT_BUS`) y persistida mediante la API de Capabilities de Forge. 

```
┌────────────────────────────────────────────────────────┐
│                   Capa de Presentación                 │
│  SkillTreeScreen │ RadialMenuScreen │ Overlays (HUD)   │
└───────────────────────────┬────────────────────────────┘
                            │ Serialización ByteBuf (Forge NetworkRegistry)
┌───────────────────────────▼────────────────────────────┐
│                    Capa de Transporte                  │
│       ModMessages (SimpleChannel Canal v5, 6 Paquetes) │
└───────────────────────────┬────────────────────────────┘
                            │ Despacho I/O al hilo del servidor
┌───────────────────────────▼────────────────────────────┐
│               Lógica de Dominio y Datos                │
│    PlayerSkills (Capability) ───► SkillRegistry        │
│    SkillProgression          ───► SkillEconomy         │
│    MinionHelper              ───► SkillAttributes      │
└───────────────────────────┬────────────────────────────┘
                            │ Pipeline de eventos de bajo nivel
┌───────────────────────────▼────────────────────────────┐
│                     Hook Engine                        │
│   ModEvents (LivingHurt, LivingAttack, PlayerTick...)  │
└────────────────────────────────────────────────────────┘
```

---

## 3. Modelo de Datos y Persistencia

### 3.1 Capability: `PlayerSkills`
Vinculada a instancias de `net.minecraft.world.entity.player.Player` mediante `PlayerSkillsProvider`. Implementa la interfaz `INBTSerializable<CompoundTag>`.

#### Estructuras de Datos en Memoria
* `branchLevels: Map<ResourceLocation, Integer>`: Nivel actual por rama $[0, 100]$.
* `unlockedNodes: Set<ResourceLocation>`: Identificadores de habilidades adquiridas.
* `practiceCounters: Map<ResourceLocation, Integer>`: Contadores de métricas operacionales acumuladas.
* `cooldowns: Map<ResourceLocation, Integer>`: Tiempos de enfriamiento restantes medidos en ticks ($1\text{ s} = 20\text{ ticks}$).
* `equippedSkills: List<ResourceLocation>`: Vector dinámico con capacidad máxima fija $N_{\text{max}} = 8$ para habilidades activas/definitivas.
* `currentMana: float` / `maxMana: float`: Reserva escalar de recurso mágico.
* `ultimateCharged: boolean`: Estado booleano del acumulador de ataque crítico final.
* `dashIFrameTicks: int`: Contador decremental de cuadros de invulnerabilidad activa.

#### Estructura NBT de Persistencia
```
TAG_Compound {
    "BranchLevels": TAG_Compound { (String: ResourceLocation) -> TAG_Int },
    "UnlockedNodes": TAG_List[TAG_String],
    "PracticeCounters": TAG_Compound { (String: ResourceLocation) -> TAG_Int },
    "Cooldowns": TAG_Compound { (String: ResourceLocation) -> TAG_Int },
    "EquippedSkills": TAG_List[TAG_String],
    "CurrentMana": TAG_Float,
    "UltimateCharged": TAG_Byte (boolean)
}
```

#### Ciclo de Vida y Clonación de Entidad
En eventos de muerte o cambio dimensional (`PlayerEvent.Clone`), los datos se replican mediante copia profunda directa desde la entidad original (`reviveCaps()`) a la nueva entidad instanciada antes de invocar `invalidateCaps()`. El estado transitorio `dashIFrameTicks` se restablece a `0`.

---

## 4. Modelos Matemáticos y Mecánicas de Progresión

### 4.1 Coste de Ascenso por Nivel de Experiencia
El costo $C(L)$ en niveles de experiencia de Minecraft para transicionar de un nivel $L$ a $L+1$ en cualquier rama está gobernado por:

$$C(L) = \begin{cases} 
1 & \text{si } L \le 0 \\
100 & \text{si } L \ge 99 \\
\max\left(1, \operatorname{round}\left(1.0 + \left(\frac{L}{99}\right)^{1.6} \times 99.0\right)\right) & \text{si } 0 < L < 99 
\end{cases}$$

### 4.2 Requisito de Práctica Universal
Para habilitar la adquisición del nivel $L+1$, el acumulador de práctica específico de la rama $P$ debe satisfacer:

$$P \ge K(L+1) = (L + 1) \times 3$$

### 4.3 Escalado de Atributos Físicos y Pasivos

#### Daño Cuerpo a Cuerpo (`Attributes.ATTACK_DAMAGE`)
Se aplica un `AttributeModifier` aditivo transitorio:

$$D_{\text{bonus}}(L_{\text{melee}}) = D_{\text{base}} \times (L_{\text{melee}} \times 0.02)$$

*Incremento estricto del $+2\%$ lineal del daño base por nivel ($+200\%$ adicional a nivel 100; multiplicador neto $\times 3.0$).*

#### Velocidad de Movimiento (`Attributes.MOVEMENT_SPEED`)
Modificador aditivo transitorio:

$$V_{\text{bonus}}(L_{\text{mobility}}) = \left(\frac{L_{\text{mobility}}}{100}\right)^{1.5} \times 0.08$$

#### Factor de Mitigación de Daño Defensivo
Atenuación escalar del daño entrante antes de armadura:

$$F_{\text{defensa}}(L_{\text{defense}}) = \max\left(0.60, 1.0 - \left(\frac{L_{\text{defense}}}{100}\right)^{1.4} \times 0.40\right)$$

*Mitigación máxima: $40\%$ a nivel 100 ($F = 0.60$).*

#### Bono de Daño a Distancia
Multiplicador escalar de daño para entidades `AbstractArrow`:

$$M_{\text{distancia}}(L_{\text{ranged}}) = 1.0 + \left(\frac{L_{\text{ranged}}}{100}\right)^{1.5} \times 2.5$$

#### Maná Máximo y Regeneración
* **Capacidad Máxima:** $M_{\text{max}} = 100.0 + (L_{\text{magic}} \times 2.0)$
* **Tasa de Regeneración por Segundo:** $R_{\text{mana}} = 2.0 \times \left(1.0 + L_{\text{magic}} \times 0.05\right)$
* **Regeneración Aplicada por Tick ($20\text{ Hz}$):** $\Delta M_{\text{tick}} = \frac{R_{\text{mana}}}{20}$

#### Altura de Paso (`ForgeMod.STEP_HEIGHT_ADDITION`)
Modificador aditivo acumulativo condicionado al desbloqueo de nodos:

$$H_{\text{adicional}} = \sum_{k} h_k, \quad h_k = 0.5 \text{ para } k \in \{\text{LightStep, StepBoostMobility20, StepBoostMelee3}\}$$

*Rango de adición posible: $[0.0, 1.5]\text{ bloques}$. Altura máxima alcanzable: $0.6 + 1.5 = 2.1\text{ bloques}$.*

---

## 5. Protocolo de Red (`modrpg:main`)

Canal bidireccional implementado mediante `SimpleChannel` de Forge. Versión del protocolo: `"5"`.

| ID | Clase Paquete | Dirección | Carga Útil (Payload) | Descripción / Lógica de Procesamiento |
| :--- | :--- | :---: | :--- | :--- |
| `0` | `PacketCastSkill` | C $\to$ S | `ResourceLocation skillId` | Valida nodo, verifica desbloqueo, cooldown y deduce maná. Despacha `onExecuteActive()`. |
| `1` | `PacketSyncSkillsToClient` | S $\to$ C | Mapas serializados: niveles, nodos, contadores, cooldowns, booleano de definitiva, lista de loadout | Reemplaza estructuras de datos en la capability del cliente en el hilo de renderizado. |
| `2` | `PacketUpgradeSkill` | C $\to$ S | `String branch` | Valida XP de Minecraft y práctica. Deduce XP y asciende nivel en `SkillEconomy`. |
| `3` | `PacketUnlockNode` | C $\to$ S | `ResourceLocation nodeId` | Evalúa `SkillNode.canUnlock()`. Deduce requisitos y muta estado persistente. |
| `4` | `PacketSyncMana` | S $\to$ C | `float currentMana`, `float maxMana` | Sincronización continua de la barra de maná cada 20 ticks o bajo consumo. |
| `5` | `PacketEquipSkill` | C $\to$ S | `ResourceLocation skillId` | Modifica el vector `equippedSkills` (asigna o remueve ranura, límite de 8). |

---

## 6. Registro de Ramas y Catálogo de Nodos

### 6.1 Ramas Base (`SkillBranch`)
* `modrpg:melee`: CaC (XP Base req.: 10, Contador: `modrpg:melee_kills`)
* `modrpg:ranged`: Arquería (XP Base req.: 5, Contador: `modrpg:ranged_kills`)
* `modrpg:magic`: Magia (XP Base req.: 5, Contador: `modrpg:magic_casts`)
* `modrpg:defense`: Defensa (XP Base req.: 8, Contador: `modrpg:damage_blocked`)
* `modrpg:mobility`: Movilidad (XP Base req.: 10, Contador: `modrpg:distance_run`)

---

### 6.2 Especificación de Nodos (`SkillNode`)

#### Rama Melee
* `modrpg:melee_step_boost_3`
  * **Tipo:** `PASSIVE_STAT`
  * **Requisito:** Rama Melee $\ge 3$
  * **Efecto:** Otorga $+0.5\text{ bloques}$ de altura de paso mediante `SkillAttributes`.
* `modrpg:melee_double_attack`
  * **Tipo:** `ACTIVE_ABILITY` (Gatillada por evento)
  * **Requisito:** Rama Melee $\ge 4$
  * **Efecto:** Si la fuerza de ataque es $\ge 0.92$, aplica un golpe secundario recursivo con el $80\%$ del daño recalculando frames de invulnerabilidad.
* `modrpg:melee_unarmed_style`
  * **Tipo:** `PASSIVE_STAT`
  * **Requisito:** Rama Melee $\ge 4$, Nodo Previo: `double_attack`
  * **Efecto:** Ataques con mano vacía causan $+10\%$ de daño; ataques armados sufren penalización del $-5\%$.
* `modrpg:melee_weapon_mastery`
  * **Tipo:** `PASSIVE_STAT`
  * **Requisito:** Rama Melee $\ge 8$, Nodo Previo: `double_attack`
  * **Efecto:** Otorga $+15\%$ de daño con `TieredItem`. $15\%$ de probabilidad por impacto de consumir $+1$ de daño adicional a la durabilidad del arma.
* `modrpg:melee_leg_trip`
  * **Tipo:** `ACTIVE_ABILITY`
  * **Requisito:** Rama Melee $\ge 6$, Nodo Previo: `unarmed_style`
  * **Cooldown:** $160\text{ ticks}$ ($8\text{ s}$)
  * **Efecto:** AABB $3.5 \times 1.0 \times 3.5$. Inflige $1.0\times$ de daño base, anula inercia y aplica `Slowdown IV` durante $60\text{ ticks}$.
* `modrpg:melee_wide_sweep`
  * **Tipo:** `ACTIVE_ABILITY`
  * **Requisito:** Rama Melee $\ge 12$, Nodo Previo: `weapon_mastery`
  * **Cooldown:** $100\text{ ticks}$ ($5\text{ s}$)
  * **Efecto:** AABB $4.0 \times 1.0 \times 4.0$. Inflige $1.25\times$ daño base a todos los objetivos en un barrido frontal plano.
* `modrpg:melee_ether_dual_sword`
  * **Tipo:** `ACTIVE_ABILITY` (Gatillada por evento)
  * **Requisito:** Rama Melee $\ge 5$, Rama Magic $\ge 6$, Nodo Previo: `double_attack`
  * **Efecto:** Al impactar con espada en mano principal, asesta un impacto secundario en mano secundaria por el $75\%$ de daño convertido a tipo `DamageSource.magic()`.
* `modrpg:melee_heavy_tornado`
  * **Tipo:** `ACTIVE_ABILITY`
  * **Requisito:** Rama Melee $\ge 10$, Bajas CaC $\ge 20$, Nodo Previo: `double_attack`
  * **Cooldown:** $160\text{ ticks}$ ($8\text{ s}$)
  * **Efecto:** Barrido radial de $5.5\text{ m}$. Aplica $(1.5 \times D_{\text{base}}) \times (1.0 + L_{\text{melee}} \times 0.02)$ con vector de elevación vertical $Y = +0.45$.
* `modrpg:melee_megacut`
  * **Tipo:** `ACTIVE_ABILITY`
  * **Requisito:** Rama Melee $\ge 50$, Bajas CaC $\ge 100$, Nodo Previo: `heavy_tornado`
  * **Cooldown:** $400\text{ ticks}$ ($20\text{ s}$)
  * **Efecto:** Proyección lineal de rayo de $12\text{ m}$. Inflige $3.0\times D_{\text{base}}$ en AABB extendido a todo objetivo cuyo vector de proyección satisfaga $d \le 1.5\text{ m}$.
* `modrpg:melee_ultracut`
  * **Tipo:** `ULTIMATE`
  * **Requisito:** Rama Melee $\ge 100$, Bajas CaC $\ge 250$, Nodo Previo: `megacut`
  * **Cooldown:** $12000\text{ ticks}$ ($10\text{ min}$)
  * **Efecto:** Carga el siguiente ataque físico para infligir $+500\%$ de daño crítico directo ($5.0\times$) con detonación de daño colateral en área ($6.0\text{ m}$) por el $50\%$ del daño neto resultante.

---

#### Rama Arquería (Ranged)
* `modrpg:ranged_tailwind`
  * **Tipo:** `PASSIVE_STAT`
  * **Requisito:** Rama Ranged $\ge 5$
  * **Efecto:** Multiplica el vector de velocidad inicial de `AbstractArrow` por $\times 1.8$.
* `modrpg:ranged_rapid_fire`
  * **Tipo:** `ACTIVE_ABILITY`
  * **Requisito:** Rama Ranged $\ge 12$, Nodo Previo: `tailwind`
  * **Cooldown:** $300\text{ ticks}$ ($15\text{ s}$)
  * **Efecto:** Emite de forma instantánea 6 entidades `Arrow` secuenciales con dispersión de trayectoria e incremento de daño base $\times 1.2$.
* `modrpg:ranged_homing_arrow`
  * **Tipo:** `ACTIVE_ABILITY`
  * **Requisito:** Rama Ranged $\ge 16$, Nodo Previo: `tailwind`
  * **Cooldown:** $200\text{ ticks}$ ($10\text{ s}$)
  * **Efecto:** Escanea entidades en un radio de $25\text{ m}$. Dispara una flecha con vector normalizado hacia el objetivo detectado. Si no hay entidades, aplica penalización asignando un cooldown de $400\text{ ticks}$.
* `modrpg:ranged_crossbow_artillery`
  * **Tipo:** `ACTIVE_ABILITY` (Gatillada por evento)
  * **Requisito:** Rama Ranged $\ge 20$, Bajas Ranged $\ge 30$, Nodo Previo: `tailwind`
  * **Efecto:** Cohetes disparados con ballesta provocan detonaciones sónicas y suman $+15.0 + (L_{\text{ranged}} \times 0.5)$ de daño plano adicional.
* `modrpg:ranged_hypersonic`
  * **Tipo:** `ACTIVE_ABILITY` (Gatillada por evento)
  * **Requisito:** Rama Ranged $\ge 50$, Nodo Previo: `tailwind`
  * **Efecto:** Disparar agachado (`shift`) incrementa la velocidad $\times 1.5$, anula gravedad (`NoGravity: true`) y asigna penetración estricta de 5 entidades (`PierceLevel: 5`).

---

#### Rama Magia y Elementos
* `modrpg:magic_fireball`
  * **Tipo:** `ACTIVE_ABILITY`
  * **Requisito:** Rama Magic $\ge 3$
  * **Coste de Maná:** $20.0$
  * **Cooldown:** $100\text{ ticks}$ ($5\text{ s}$)
  * **Efecto:** Raycast de muestreo volumétrico de 10 bloques. Aplica $8.0 + (L_{\text{magic}} \times 0.4)$ de daño mágico e ignición durante $5 + \lfloor L_{\text{magic}} / 10 \rfloor\text{ segundos}$.
* `modrpg:magic_healing_aura`
  * **Tipo:** `ACTIVE_ABILITY`
  * **Requisito:** Rama Magic $\ge 6$, Nodo Previo: `fireball`
  * **Coste de Maná:** $35.0$
  * **Cooldown:** $240\text{ ticks}$ ($12\text{ s}$)
  * **Efecto:** Regeneración instantánea de $6.0 + (L_{\text{magic}} \times 0.2)\text{ PV}$, remueve veneno, wither y lentitud; otorga `Regeneration II` durante $100\text{ ticks}$.
* `modrpg:magic_necrotic_drain`
  * **Tipo:** `PASSIVE_STAT`
  * **Requisito:** Rama Magic $\ge 15$, Nodo Previo: `fireball`
  * **Efecto:** Drena salud pasivamente al infligir daño; cura al ejecutor por $\max(1.0, D_{\text{infligido}} \times 0.15)\text{ PV}$.
* `modrpg:magic_earth_tune`
  * **Tipo:** `ACTIVE_ABILITY`
  * **Requisito:** Rama Magic $\ge 8$, Nodo Previo: `fireball`
  * **Cooldown:** $200\text{ ticks}$ ($10\text{ s}$)
  * **Efecto:** Requiere apoyo sobre tags `BASE_STONE_OVERWORLD`, `DIRT` o `SAND`. Genera $4.0$ puntos de agotamiento de comida y restaura $+40.0$ de maná.
* `modrpg:magic_counter_attack`
  * **Tipo:** `ACTIVE_ABILITY`
  * **Requisito:** Rama Magic $\ge 12$, Nodo Previo: `fireball`
  * **Coste de Maná:** $25.0$
  * **Cooldown:** $240\text{ ticks}$ ($12\text{ s}$)
  * **Efecto:** Activa bandera durante $30\text{ ticks}$ ($1.5\text{ s}$). Si recibe daño, lo anula por completo y desata un contragolpe sónico de $18.0$ de daño mágico a un máximo de 2 objetivos en $6.0\text{ m}$.
* `modrpg:magic_lightning_chain`
  * **Tipo:** `ACTIVE_ABILITY`
  * **Requisito:** Rama Magic $\ge 20$, Nodo Previo: `fireball`
  * **Coste de Maná:** $30.0$
  * **Cooldown:** $140\text{ ticks}$ ($7\text{ s}$)
  * **Efecto:** Localiza entidades en un radio de $8.0\text{ m}$ e inflige $10.0 + (L_{\text{magic}} \times 0.3)$ de daño mágico a hasta 3 entidades no aliadas.
* `modrpg:magic_summon_zombies`
  * **Tipo:** `ACTIVE_ABILITY`
  * **Requisito:** Rama Magic $\ge 10$, Nodo Previo: `fireball`
  * **Coste de Maná:** $45.0$
  * **Cooldown:** $400\text{ ticks}$ ($20\text{ s}$)
  * **Efecto:** Invoca 5 zombies infantes (`isBaby: true`) protegidos con cascos de hierro durante $400\text{ ticks}$ ($20\text{ s}$).
* `modrpg:magic_summon_skeletons`
  * **Tipo:** `ACTIVE_ABILITY`
  * **Requisito:** Rama Magic $\ge 15$, Nodo Previo: `summon_zombies`
  * **Coste de Maná:** $50.0$
  * **Cooldown:** $500\text{ ticks}$ ($25\text{ s}$)
  * **Efecto:** Invoca 2 esqueletos arqueros equipados con cascos y arcos durante $500\text{ ticks}$ ($25\text{ s}$).
* `modrpg:magic_bee_swarm`
  * **Tipo:** `ACTIVE_ABILITY`
  * **Requisito:** Rama Magic $\ge 12$, Nodo Previo: `fireball`
  * **Coste de Maná:** $35.0$
  * **Cooldown:** $240\text{ ticks}$ ($12\text{ s}$)
  * **Efecto:** Raycast cónico de retícula ($\cos \theta \ge 0.70$ hasta $16\text{ m}$). Genera 4 abejas hostiles persistentes fijadas exclusivamente contra el objetivo seleccionado durante $160\text{ ticks}$ ($8\text{ s}$).
* `modrpg:magic_summon_wolves`
  * **Tipo:** `ACTIVE_ABILITY`
  * **Requisito:** Rama Magic $\ge 16$, Nodo Previo: `bee_swarm`
  * **Coste de Maná:** $40.0$
  * **Cooldown:** $360\text{ ticks}$ ($18\text{ s}$)
  * **Efecto:** Invoca 3 lobos domesticados vinculados al jugador durante $300\text{ ticks}$ ($15\text{ s}$).

---

#### Rama Movilidad (Mobility)
* `modrpg:mobility_light_step`
  * **Tipo:** `PASSIVE_STAT`
  * **Requisito:** Rama Mobility $\ge 2$
  * **Efecto:** Otorga $+0.5\text{ bloques}$ de altura de paso mediante `SkillAttributes`.
* `modrpg:mobility_dash`
  * **Tipo:** `ACTIVE_ABILITY`
  * **Requisito:** Rama Mobility $\ge 10$, Nodo Previo: `light_step`
  * **Cooldown:** $60\text{ ticks}$ ($3\text{ s}$)
  * **Efecto:** Aplica vector horizontal directo $\vec{V}_{\text{horiz}} \times 1.5$ ($Y = 0.15$), sincroniza la posición vía `ClientboundSetEntityMotionPacket` y asigna $15\text{ ticks}$ ($0.75\text{ s}$) de invulnerabilidad estricta (`dashIFrameTicks`).
* `modrpg:mobility_air_jump`
  * **Tipo:** `ACTIVE_ABILITY`
  * **Requisito:** Rama Mobility $\ge 20$, Nodo Previo: `light_step`
  * **Cooldown:** $80\text{ ticks}$ ($4\text{ s}$)
  * **Efecto:** Resetea la distancia de caída acumulada, asigna inmunidad al siguiente impacto (`modrpg_air_jump_safe`) e induce vector vertical instantáneo $Y = 0.95$ conservando el $115\%$ del momento horizontal previo.
* `modrpg:mobility_flurry_of_strikes`
  * **Tipo:** `ACTIVE_ABILITY`
  * **Requisito:** Rama Mobility $\ge 10$, Nodo Previo: `dash`
  * **Cooldown:** $240\text{ ticks}$ ($12\text{ s}$)
  * **Efecto:** Aplica `Movement Speed III` durante $200\text{ ticks}$ ($10\text{ s}$) y genera un salto cinético hacia la dirección de la mirada ($\vec{V}_{\text{look}} \times 1.3$, $Y = 1.10$).
* `modrpg:mobility_impact_jump`
  * **Tipo:** `ACTIVE_ABILITY`
  * **Requisito:** Rama Mobility $\ge 15$, Nodo Previo: `air_jump`
  * **Cooldown:** $300\text{ ticks}$ ($15\text{ s}$)
  * **Efecto:** Impulsa verticalmente al jugador ($Y = 1.65$). Etiqueta con `modrpg_ground_slam_active`. Al colisionar contra el suelo (`LivingFallEvent`), anula el daño de caída y detona una onda de choque sísmica de $150.0\text{ de daño}$ plano en un radio de $5.5\text{ m}$ con vector de empuje repulsor $1.8$.
* `modrpg:mobility_step_boost_20`
  * **Tipo:** `PASSIVE_STAT`
  * **Requisito:** Rama Mobility $\ge 20$, Nodo Previo: `light_step`
  * **Efecto:** Otorga $+0.5\text{ bloques}$ de altura de paso mediante `SkillAttributes`.

---

#### Rama Defensa (Defense)
* `modrpg:defense_stone_skin`
  * **Tipo:** `PASSIVE_STAT`
  * **Requisito:** Rama Defense $\ge 3$
  * **Efecto:** Reduce el daño final recibido un $20\%$ ($D_{\text{recibido}} \times 0.80$).
* `modrpg:defense_push_and_wear`
  * **Tipo:** `PASSIVE_STAT`
  * **Requisito:** Rama Defense $\ge 8$, Nodo Previo: `stone_skin`
  * **Efecto:** Ataques directos aplican empuje forzado de factor $1.5$ y restan $5$ puntos de durabilidad inmediata al objeto en la mano principal de la entidad alcanzada.
* `modrpg:defense_iron_strength`
  * **Tipo:** `ACTIVE_ABILITY`
  * **Requisito:** Rama Defense $\ge 14$, Nodo Previo: `stone_skin`
  * **Cooldown:** $600\text{ ticks}$ ($30\text{ s}$)
  * **Efecto:** Asigna la etiqueta `modrpg_iron_strength_active` durante $240\text{ ticks}$ ($12\text{ s}$). Todo daño entrante es interceptado y cancelado en `LivingAttackEvent`, curando a la entidad jugadora $+2.5\text{ PV}$ por impacto recibido.
* `modrpg:defense_iron_fortress`
  * **Tipo:** `ACTIVE_ABILITY`
  * **Requisito:** Rama Defense $\ge 20$, Nodo Previo: `iron_strength`
  * **Cooldown:** $600\text{ ticks}$ ($30\text{ s}$)
  * **Efecto:** Otorga `Resistance III` ($60\%$ de mitigación vanilla) y `Absorption II` durante $200\text{ ticks}$ ($10\text{ s}$).

---

#### Sinergias Híbridas
* `modrpg:hybrid_hunter`
  * **Tipo:** `HYBRID_SYNERGY`
  * **Requisitos:** Rama Melee $\ge 25$, Rama Ranged $\ge 25$, Nodos Previos: `double_attack`, `tailwind`
  * **Efecto:** Flechas marcan a la entidad con `modrpg_hunter_mark` y brillo (`Glowing`). Ataques cuerpo a cuerpo subsecuentes consumen la marca e incrementan el daño resultante en un $+150\%$ ($2.5\times$).
* `modrpg:hybrid_arrow_propulsion`
  * **Tipo:** `HYBRID_SYNERGY`
  * **Requisitos:** Rama Melee $\ge 35$, Rama Ranged $\ge 30$, Nodo Previo: `hybrid_hunter`
  * **Cooldown:** $60\text{ ticks}$ ($3\text{ s}$)
  * **Efecto:** Disparar flechas con inclinación vertical hacia abajo ($\text{pitch} > 55^\circ$) genera una propulsión acrobática inversa ($Y = 1.35$) y otorga `Slow Falling` durante $80\text{ ticks}$.
* `modrpg:hybrid_sword_quiver`
  * **Tipo:** `HYBRID_SYNERGY`
  * **Requisitos:** Rama Melee $\ge 70$, Rama Ranged $\ge 50$, Nodo Previo: `hybrid_hunter`
  * **Efecto:** Flechas disparadas portan la etiqueta `modrpg_sword_arrow`. Al impactar, calculan y suman $+1.5\times$ del atributo `ATTACK_DAMAGE` CaC del jugador como daño plano.
* `modrpg:hybrid_combined_ultimate`
  * **Tipo:** `ULTIMATE`
  * **Requisitos:** Rama Melee $\ge 100$, Rama Ranged $\ge 70$, Nodos Previos: `ultracut`, `sword_quiver`
  * **Efecto:** Detonar el `Ultracorte Final` desencadena un bombardeo orbital de proyectiles mágicos en un radio de $7.0\text{ m}$ que infligen el $40\%$ del daño total del Ultracorte como daño tipo `DamageSource.magic()`.

---

## 7. Subsistema de Esbirros e Invocaciones (`MinionHelper`)

* **Identificación:** Las entidades generadas reciben las etiquetas `modrpg_minion` y `modrpg_owner_<UUID>`.
* **Tiempo de Vida (`modrpg_lifespan`):** Controlado en `LivingEvent.LivingTickEvent`. Si el contador en su `PersistentData` llega a cero, se invocan partículas de evaporación y la entidad se elimina de forma limpia mediante `.discard()`.
* **Matriz de Fuego Amigo:** `LivingAttackEvent` verifica si la víctima y el atacante comparten identificador de propietario o relación amo-esbirro. De existir correlación, el evento se cancela de forma irrevocable.
* **Redirección de Agresión:** Al momento en que el jugador inflige daño a cualquier entidad (`LivingHurtEvent`), se ejecuta `redirectMinionsTarget()`, reasignando la IA de ataque de todos los esbirros en un radio de $16\text{ m}$ hacia el objetivo impactado.

---

## 8. Pipeline de Eventos e Interceptores (`ModEvents`)

```
Flujo de eventos procesado por ciclo de ejecución:

LivingAttackEvent
├── 1. Intercepción de Fuego Amigo (MinionHelper.areAllies) -> cancela evento
├── 2. Validación de Inmunidad IronStrength -> cancela daño y cura 2.5f
└── 3. Validación de Dash i-Frames -> cancela daño si dashIFrameTicks > 0

LivingHurtEvent
├── 1. Redirección de objetivos de esbirros al agresor
├── 2. Escalado por Rama Ranged (AbstractArrow)
├── 3. Despacho polimórfico de SkillNode.onLivingHurt (Atacante)
├── 4. Mitigación por Rama Defensa (F_defensa)
└── 5. Despacho polimórfico de SkillNode.onLivingHurt (Víctima)

LivingFallEvent
├── Caso A: Salto Sísmico (TAG_GROUND_SLAM) -> Inmunidad a caída + 150 de daño AoE
└── Caso B: Salto de Viento (AIR_JUMP_SAFE_TAG) -> Inmunidad a caída

EntityJoinLevelEvent
└── Verificación de flechas instanciadas -> Inyección de modificaciones de trayectoria
```

---

## 9. Capa de Presentación e Interfaces de Usuario

### 9.1 Árbol de Habilidades (`SkillTreeScreen`)
* **Matriz de Transformación 2D:** Soporta desplazamiento continuo (`scrollX`, `scrollY`) y escalado afín interactivo $\text{zoom} \in [0.55, 1.8]$.
* **Renderizado de Grafo:** Renderizado de líneas de conexión entre nodos padre e hijos calculando matrices de rotación en el eje Z mediante `Axis.ZP.rotationDegrees()` sobre el `PoseStack`.
* **Interacción:** 
  * Clic Izquierdo ($button = 0$): Despacha `PacketUnlockNode` (intento de compra).
  * Clic Derecho ($button = 1$): Despacha `PacketEquipSkill` (alterna asignación en loadout).
* **Filtrado:** Pestañas de aislamiento de ramas en la zona inferior que aplican máscara alfa ($0\text{xFF}$ para rama seleccionada, $0\text{x44}$ para nodos descartados).

### 9.2 Menú Radial (`RadialMenuScreen`)
* **Distribución Geométrica:** Mapeo polar equidistante para $N$ habilidades equipadas ($N \le 8$):

$$\theta_i = \left(\frac{2\pi}{N}\right) \cdot i - \frac{\pi}{2}$$

$$X_i = X_{\text{centro}} + R \cdot \cos(\theta_i), \quad Y_i = Y_{\text{centro}} + R \cdot \sin(\theta_i) \quad (R = 80\text{ px})$$

* **Indicador Visual de Estado:**
  * **Rojo (`0xFFAA2222`):** Enfriamiento activo.
  * **Azul (`0xFF3366BB`):** Maná insuficiente.
  * **Dorado (`0xFFDAA520`) / Blanco (`0xFFFFFFFF`):** Habilidad disponible / En foco (`hovered`).

### 9.3 Overlays HUD
* **`ManaOverlay`:** Renderizado relativo sobre la barra de salud/comida (`VanillaGuiOverlay.PLAYER_HEALTH`). Dibuja la barra de maná azul (`0xFF00AAFF`) y el valor numérico truncado.
* **`SkillCooldownOverlay`:** Anclado a la izquierda sobre la barra de acceso rápido (`VanillaGuiOverlay.HOTBAR`). Muestra la pila de habilidades actualmente en enfriamiento con renderizado de icono, capa sombreada alfa y remanente en segundos ($t / 20$).

---

## 10. Subsistema de Comandos (`RpgCommands`)

Todos los comandos se articulan bajo la raíz `/rpg`.

* `/rpg stats`
  * **Nivel de Permiso:** `0` (Todos los jugadores).
  * **Salida:** Imprime niveles de ramas, contadores de práctica y listado de identificadores de habilidades desbloqueadas.
* `/rpg upgrade <branch>`
  * **Nivel de Permiso:** `0`.
  * **Argumentos:** `<branch>` (`String`, autocompletado con ramas registradas).
  * **Operación:** Valida precondiciones y transacciona el ascenso de nivel descontando niveles de XP de Minecraft.
* `/rpg addlevel <branch> <amount>`
  * **Nivel de Permiso:** `2` (Operadores / Depuración).
  * **Argumentos:** `<branch>` (`String`), `<amount>` (`int` $[1, 100]$).
  * **Operación:** Incrementa arbitrariamente el nivel de la rama especificada, recalcula los modificadores de atributos y sincroniza al cliente de forma forzada.
* `/rpg unlock <skill>`
  * **Nivel de Permiso:** `2`.
  * **Argumentos:** `<skill>` (`String`, autocompletado con nodos de `SkillRegistry`).
  * **Operación:** Concede el nodo ignorando completamente los requerimientos de nivel, coste o prerrequisitos de grafo.