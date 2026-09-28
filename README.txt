***

# Especificación Técnica de Arquitectura e Implementación: ModRPG

## 1. Entorno de Ejecución y Runtime

* **Plataforma:** Minecraft Java Edition
* **Versión de Runtime:** 1.20.1
* **Cargador de Módulos:** Minecraft Forge `47.4.10+` (FML Specification `[47,)`)
* **Toolchain / JDK:** OpenJDK 17 LTS (Lenguaje fuente / Bytecode objetivo: Java 17)
* **Mapeos de Símbolos:** Mojang Official Mappings (`1.20.1`)
* **Identificador de Módulo (`modid`):** `modrpg`
* **Protocolo de Comunicación en Red:** `modrpg:main` v8

---

## 2. Arquitectura General del Sistema

El módulo se estructura bajo un patrón desacoplado guiado por eventos (Event-Driven Architecture) apoyado en el bus unificado de Forge (`MinecraftForge.EVENT_BUS`), sincronizado mediante un canal bidireccional determinista y persistido a través del sistema de `Capability` de Forge adjunto a la entidad `ServerPlayer`.

```
┌────────────────────────────────────────────────────────────────────────┐
│                          CAPA DE PRESENTACIÓN                          │
│  SkillTreeScreen │ RadialMenuScreen │ SpellCraftingScreen │ Overlays   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Paquetes de Red (ModMessages v8)
┌───────────────────────────────────▼────────────────────────────────────┐
│                           CAPA DE TRANSPORTE                           │
│     9 Paquetes Bidireccionales (C2S / S2C) sobre SimpleChannel         │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Mutación atómica y validaciones
┌───────────────────────────────────▼────────────────────────────────────┐
│                        CAPA DE DOMINIO Y DATOS                         │
│   PlayerSkills (Capability)      │ SkillRegistry / SkillEconomy         │
│   Modular Magic Engine           │ ElementalReactionManager            │
│   SquadCoordinator (F.E.A.R. AI) │ TacticalCasterGoal / ChampionAffix  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Intercepción de eventos de bajo nivel
┌───────────────────────────────────▼────────────────────────────────────┐
│                           CAPA DE INTEGRACIÓN                          │
│   ModEvents (LivingHurt, LivingAttack, PlayerTick, LivingFall, etc.)    │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Modelo de Datos y Persistencia (`PlayerSkills`)

Vinculada a cada instancia de `net.minecraft.world.entity.player.Player` mediante `PlayerSkillsProvider`. Implementa `INBTSerializable<CompoundTag>`.

### 3.1 Estructuras de Datos en Memoria
* `branchLevels: Map<ResourceLocation, Integer>`: Nivel actual por rama $[0, 100]$.
* `unlockedNodes: Set<ResourceLocation>`: Identificadores de habilidades activas/pasivas aprendidas.
* `practiceCounters: Map<ResourceLocation, Integer>`: Métricas acumulativas (muertes CaC, flechas disparadas, bloques corridos, bloqueos, kills élite).
* `cooldowns: Map<ResourceLocation, Integer>`: Enfriamientos activos medidos en ticks ($1\text{ s} = 20\text{ ticks}$).
* `equippedSkills: List<ResourceLocation>`: Vector acotado a $N_{\text{max}} = 8$ ranuras para la rueda radial.
* `primaryBranch: ResourceLocation` / `secondaryBranch: ResourceLocation`: Asignaciones fijas de especialización.
* `activeToggles: Set<ResourceLocation>`: Registro de posturas activadas con drenaje periódico de maná.
* `spellMemory: CraftedSpell[4]`: Ranuras de memoria para hechizos modulares customizados.
* `currentMana: float` / `maxMana: float`: Escalares del recurso mágico.
* `dashIFrameTicks: int`: Contador de cuadros de inmunidad absoluta transitoria.

### 3.2 Esquema de Persistencia NBT
```
TAG_Compound {
    "BranchLevels": TAG_Compound { (String: ResourceLocation) -> TAG_Int },
    "UnlockedNodes": TAG_List[TAG_String],
    "PracticeCounters": TAG_Compound { (String: ResourceLocation) -> TAG_Int },
    "Cooldowns": TAG_Compound { (String: ResourceLocation) -> TAG_Int },
    "EquippedSkills": TAG_List[TAG_String],
    "ActiveToggles": TAG_List[TAG_String],
    "SpellMemory": TAG_List[
        TAG_Compound {
            "Slot": TAG_Int,
            "Name": TAG_String,
            "Element": TAG_String,
            "Shape": TAG_String,
            "Timing": TAG_String,
            "Power": TAG_Int
        }
    ],
    "SelectedSkill": TAG_String,
    "PrimaryBranch": TAG_String,
    "SecondaryBranch": TAG_String,
    "CurrentMana": TAG_Float,
    "UltimateCharged": TAG_Byte
}
```

### 3.3 Ciclo de Vida y Clonación de Entidad
Bajo el evento `PlayerEvent.Clone`, los datos se transfieren directamente desde la entidad previa invalidada (`reviveCaps()`) a la nueva entidad. El contador `dashIFrameTicks` se restablece a `0`. Los niveles de ramas superiores al tope base son recortados únicamente si el jugador ejecuta explícitamente `/rpg respec`.

---

## 4. Dinámica de Progresión y Modelos Matemáticos

### 4.1 Coste de Experiencia por Nivel
El coste $C(L)$ en niveles de experiencia de Minecraft requerido para ascender de $L$ a $L+1$ se define según la función:

$$C(L) = \begin{cases} 
1 & \text{si } L \le 0 \\
100 & \text{si } L \ge 99 \\
\max\left(1, \operatorname{round}\left(1.0 + \left(\frac{L}{99}\right)^{1.6} \times 99.0\right)\right) & \text{si } 0 < L < 99 
\end{cases}$$

### 4.2 Requisito de Práctica Universal
El acumulador de práctica específico de la rama $P$ debe satisfacer la condición lineal previa al desembolso de experiencia:

$$P \ge K(L+1) = (L + 1) \times 3$$

### 4.3 Fórmulas de Atributos Pasivos

* **Daño Cuerpo a Cuerpo (`Attributes.ATTACK_DAMAGE`):**
  $$D_{\text{bonus}}(L_{\text{melee}}) = D_{\text{base}} \times (L_{\text{melee}} \times 0.02)$$
  *Escalado lineal estricto de $+2\%$ por nivel ($+200\%$ a nivel 100).*

* **Velocidad de Movimiento (`Attributes.MOVEMENT_SPEED`):**
  $$V_{\text{bonus}}(L_{\text{mobility}}) = \left(\frac{L_{\text{mobility}}}{100}\right)^{1.5} \times 0.08$$

* **Factor de Mitigación Defensiva:**
  $$F_{\text{defensa}}(L_{\text{defense}}) = \max\left(0.60, 1.0 - \left(\frac{L_{\text{defense}}}{100}\right)^{1.4} \times 0.40\right)$$
  *Atenuación escalar del daño entrante antes del cálculo de armadura (Mitigación máxima: $40\%$).*

* **Escalado de Proyectiles (`AbstractArrow`):**
  $$M_{\text{distancia}}(L_{\text{ranged}}) = 1.0 + \left(\frac{L_{\text{ranged}}}{100}\right)^{1.5} \times 2.5$$

* **Capacidad y Regeneración de Maná:**
  $$M_{\text{max}} = 100.0 + (L_{\text{magic}} \times 2.0)$$
  $$R_{\text{mana}} = 2.0 \times \left(1.0 + L_{\text{magic}} \times 0.05\right) \quad [\text{puntos/segundo}]$$
  $$\Delta M_{\text{tick}} = \frac{R_{\text{mana}}}{20} \quad [\text{por tick de servidor}]$$

* **Altura de Paso (`ForgeMod.STEP_HEIGHT_ADDITION`):**
  $$H_{\text{adicional}} = \sum_{k} h_k, \quad h_k = 0.5 \text{ para } k \in \{\text{LightStep, StepBoostMobility20, StepBoostMelee3}\}$$
  *Rango de adición: $[0.0, 1.5]\text{ m}$. Altura máxima alcanzable: $0.6 + 1.5 = 2.1\text{ m}$.*

---

## 5. Sistema de Especialización y Pruebas de Ascensión

Para prevenir la hiperinflación simultánea de todas las ramas a nivel 100, se impone una matriz de especialización estricta por personaje:

```
                      [Nivel 1 al 20]
              (Todas las 5 ramas accesibles)
                           │
             ¿Intento de ascenso a Nivel 21?
             ┌─────────────┴─────────────┐
             ▼                           ▼
    Rama Principal               Rama Secundaria
   (Sellado Permanente)        (Sellado Permanente)
     Límite: Nivel 100           Límite: Nivel 50
             │                           │
  Desbloquea Definitivas        Acceso a Sinergias
  (Ultracorte / Tormentas)           Híbridas
```

* **Tope Base:** Toda rama no designada tiene un límite duro fijado en **Nivel 20**.
* **Rama Principal:** Permite ascender hasta **Nivel 100**. Solo puede elegirse una.
* **Rama Secundaria:** Permite ascender hasta **Nivel 50**. Solo puede elegirse una.
* **Comando `/rpg respec`:** Disuelve los sellos asignados y trunca hacia abajo los niveles de rama que superen 20, devolviendo los puntos a la escala base sin devolver experiencia gastada.

### Pruebas de Combate de Ascensión (Hit Milestone Checks)
El ascenso a rangos superiores exige la eliminación verificada de monstruos élite (`modrpg:elite_kills`):
1. **Nivel 25 $\to$ 26 (Prueba del Iniciado):** Requiere $\ge 3$ Monstruos Campeones / Casters abatidos.
2. **Nivel 50 $\to$ 51 (Prueba del Maestro):** Requiere $\ge 10$ Monstruos Campeones abatidos.
3. **Nivel 75 $\to$ 76 (Prueba del Gran Maestro):** Requiere $\ge 25$ Monstruos Campeones abatidos.

---

## 6. Motor de Magia Modular y Reacciones Elementales

El subsistema modular desacopla la lógica de lanzamiento en tres dimensiones paramétricas:

$$\text{Daño} = \text{Base}(\text{Elemento}) \times \text{Mod}(\text{Forma}) \times \text{Mod}(\text{Cadencia}) \times [1.0 + (\text{Power} - 1) \times 0.15]$$
$$\text{Coste Maná} = \text{BaseMana}(\text{Elemento}) \times \text{ModMana}(\text{Forma}) \times \text{ModMana}(\text{Cadencia})$$
$$\text{Cooldown [ticks]} = \max\left(5, \text{BaseCD}(\text{Elemento}) \times \text{ModCD}(\text{Forma}) \times \text{ModCD}(\text{Cadencia})\right)$$

### 6.1 Catálogo de Parámetros Modulares

* **Elementos (`SpellElement`):**
  * `FIRE`: Base Dmg: 8.0, Maná: 18.0, CD: 40t. Aplica ignición.
  * `LIGHTNING`: Base Dmg: 7.0, Maná: 22.0, CD: 50t. Arco secundario a entidad cercana.
  * `FROST`: Base Dmg: 6.0, Maná: 16.0, CD: 40t. Aplica `Slowness III` (60t).
  * `VOID`: Base Dmg: 9.0, Maná: 26.0, CD: 60t. Vampirismo (cura 20% del daño infligido).
  * `HOLY`: Base Dmg: 7.0, Maná: 20.0, CD: 45t. Daño crítico a no-muertos y pulsos de autosanación.
* **Formas (`SpellShape`):**
  * `PROJECTILE`: Multiplicador $\times 1.00$. Entidad física `MagicProjectileEntity`.
  * `BEAM`: Multiplicador $\times 0.90$. Escaneo de rayo instantáneo en 16 bloques.
  * `GROUND_AOE`: Multiplicador $\times 1.30$. Runa de impacto volumétrico ($6 \times 3 \times 6\text{ m}$).
  * `SELF_AURA`: Multiplicador $\times 0.80$. Explosión radial centrada en el ejecutor ($8\text{ m}$).
  * `TOUCH`: Multiplicador $\times 1.40$. Impacto frontal concentrado a corto alcance ($2.5\text{ m}$).
* **Cadencia / Timing (`SpellTiming`):**
  * `RAPID_FIRE`: Daño $\times 0.35$, Maná $\times 0.35$, CD $\times 0.15$ (~6 ticks por casteo).
  * `BALANCED`: Factores neutrales $\times 1.00$.
  * `HEAVY_BURST`: Daño $\times 2.60$, Maná $\times 2.20$, CD $\times 3.20$.

### 6.2 Matriz de Reacciones Elementales en Cadena (`ElementalReactionManager`)
Al impactar, la entidad recibe un cebador (`Primer`) que persiste durante 100 ticks (5 s). La colisión de un elemento secundario distinto desencadena una reacción inmediata:

| Elemento Base | Elemento Detonador | Nombre de Reacción | Efecto Físico / Mecánico |
| :---: | :---: | :---: | :--- |
| **Hielo** | **Rayo** | **Superconductor** | Detonación sónica en radio de $5\text{ m}$. Inflige 14.0 de daño mágico plano y aplica `Weakness II` durante $120\text{ ticks}$. |
| **Fuego** | **Vacío** | **Colapso Gravitatorio** | Genera un vórtice gravitacional en radio de $6\text{ m}$. Atrae a todos los enemigos al epicentro, quema por 5s e inflige 12.0 de daño. |
| **Fuego** | **Sagrado** | **Pira Purificadora** | Duplica el daño base si el objetivo es no-muerto, incinera por 8s y emite una onda sanadora que restaura $+6\text{ PV}$ al ejecutor. |
| **Hielo** | **Vacío** | **Fragilidad Abisal** | Inmoviliza al objetivo (`Slowness V`, 80t) e inyecta la etiqueta `modrpg_brittle_ice`. El siguiente impacto físico directo inflige un **$+50\%$ de daño neto**. |

---

## 7. Inteligencia Artificial Táctica y Escuadrones (Modelo F.E.A.R.)

Las entidades hostiles parametrizadas por `EnemyRpgManager` operan bajo una estructura de mando coordinada en red (`SquadCoordinator`).

```
                    ┌────────────────────────────┐
                    │    LÍDER COMANDANTE ÉLITE  │
                    │   (Aura Velocidad/Defensa) │
                    └─────────────┬──────────────┘
                                  │
          ┌───────────────────────┴───────────────────────┐
          ▼                                               ▼
┌───────────────────────────┐                   ┌───────────────────────────┐
│     VANGUARDIA BRUTA      │ ◄──[Petición Peel]│      CASTERS A DISTANCIA  │
│  (Intercepción / Bloqueo) │                   │  (Requieren Token de Tiro)│
└───────────────────────────┘                   └───────────────────────────┘
```

### 7.1 Gestión de Tokens de Disparo (Anti-Spam)
Para evitar la saturación arbitraria de proyectiles en pantalla, el escuadrón mantiene un único `castingTokenHolder`. Un mob solo puede canalizar habilidades si el token está libre o le pertenece, reteniéndolo durante un máximo de $40\text{ ticks}$ (2 s).

### 7.2 Protocolo de Rescate (*Peeling*)
Si una entidad de rango o caster recibe aproximación del jugador a menos de $5\text{ m}$, emite una señal de auxilio (`requestPeel`). Las unidades de vanguardia frontal (`isAggressiveRush`) anulan temporalmente sus objetivos y cargan directamente contra el agresor con multiplicador de velocidad $\times 1.40$ y rugidos disuasorios.

### 7.3 Flanqueo Trigonométrico Cruzado
Los tiradores y casters no atacan en línea recta. Calculan vectores de posición lateral a $60^\circ - 90^\circ$ respecto a la trayectoria frontal del jugador:

$$\vec{P}_{\text{flanco}} = \vec{P}_{\text{target}} - (\vec{F} \times 10.0) + (\vec{F}_{\perp} \times 6.0)$$

### 7.4 Ruptura de Moral (*Morale Break*)
La eliminación del Líder Comandante activa `triggerMoraleBreak`: emite el sonido de cuerno de asalto (`SoundEvents.RAID_HORN`) y aplica a todos los miembros sobrevivientes del escuadrón en un radio de $20\text{ m}$ los efectos `Slowness III` y `Weakness II` durante $60\text{ ticks}$ (3 s), interrumpiendo totalmente su navegación.

### 7.5 Afijos de Campeones (`ChampionAffix`)
Los monstruos élite pueden generarse con uno de los siguientes modificadores permanentes:
* `COMMANDER`: Casco dorado, resistencia a empuje $+0.3$, emite pulso de aura en $12\text{ m}$ que otorga `Speed I` y `Resistance I` a sus aliados cada segundo.
* `RUNIC_SHIELD`: Porta un escudo secundario; intercepta y anula el 100% de flechas y proyectiles recibidos dentro de su cono de visión frontal ($\cos \theta > 0.2$).
* `VAMPIRIC`: Daño físico incrementado en $+20\%$; regenera salud equivalente al $25\%$ del daño neto causado.
* `MANA_BURN`: Porta un fragmento de amatista; cada impacto exitoso sobre un jugador drena $20.0$ puntos de su reserva de maná.

---

## 8. Mecánica de Telegrafeado, Interrupción y Postura (*Stagger*)

El objetivo `TacticalCasterGoal` ejecuta un sistema de telegrafeado visible y contrarrestable mediante dos estados de canalización:

1. **Canalización Telegrafeada Amarilla (`TAG_INTERRUPTIBLE`):**
   * **Señal:** Chispas eléctricas brillantes (`ELECTRIC_SPARK`) y sonido de baliza.
   * **Vulnerabilidad:** Si el jugador asesta un golpe con escala de ataque cargada $\ge 0.85$, la canalización se corta inmediatamente.
   * **Penalización de Postura Rota (`TAG_STAGGERED`):** El monstruo queda inmóvil durante $40\text{ ticks}$ (2 s), emite partículas de impacto crítico y entra en vulnerabilidad absoluta: **todo golpe recibido durante este estado inflige un $+30\%$ de daño crítico adicional**.
2. **Canalización Telegrafeada Roja / Imbloqueable:**
   * **Señal:** Partículas de fuego volcánico (`FLAME`), humo espeso y sonido de latido de Warden.
   * **Comportamiento:** No puede ser interrumpida físicamente; obliga al jugador a esquivar mediante *Dash* o romper línea de visión.

---

## 9. Catálogo Exhaustivo de Ramas y Habilidades

```
[ÁRBOL COMPLETO DE HABILIDADES]
├── CUERPO A CUERPO (MELEE)
│   ├── StepBoostMelee3 [Pasiva: +0.5m paso]
│   ├── DoubleAttack [Evento: Doble golpe al 80%]
│   ├── UnarmedStyle [Pasiva: +10% puños / -5% armas]
│   ├── WeaponMastery [Pasiva: +15% armas / desgaste durabilidad]
│   ├── LegTrip [Activa: Tropezón, derribo y Slowness IV]
│   ├── WideSweep [Activa: Barrido frontal 180° plano]
│   ├── BerserkerStance [Toggle: +30% daño, +15% recibido, -4.5 maná/s]
│   ├── HeavyTornado [Activa: Giro masivo 5.5m y elevación vertical]
│   ├── VitalCleave [Activa: Daño 10% vida enemiga / CD dinámico]
│   ├── Megacut [Semidefinitiva: Proyección cortante de 12 bloques]
│   └── Ultracut [DEFINITIVA: +500% daño crítico en área / 10m CD]
│
├── ARQUERÍA (RANGED)
│   ├── Tailwind [Pasiva: +80% velocidad de flecha]
│   ├── RapidFire [Activa: Ráfaga inmediata de 6 flechas frontales]
│   ├── HomingArrow [Activa: Flecha teledirigida en 25m]
│   ├── CrossbowArtillery [Pasiva: Cohetes causan explosiones sónicas]
│   └── HypersonicArrow [Pasiva: Disparo agachado perfora 5 mobs sin gravedad]
│
├── SINERGIAS HÍBRIDAS
│   ├── HybridHunter [Sinergia: Flecha marca -> Golpe CaC detona +150%]
│   ├── ArrowPropulsion [Sinergia: Disparar al piso propulsa hacia arriba]
│   ├── SwordQuiver [Sinergia: Flechas suman 1.5x daño CaC del jugador]
│   └── CombinedUltimate [DEFINITIVA HÍBRIDA: Ultracorte invoca lluvia orbital]
│
├── MAGIA Y ESPECIFICACIÓN ELEMENTAL
│   ├── CustomSpellSlots [Ranuras 1 a 4 para Magia Modular crafteada]
│   ├── Fireball [Activa: Piroclasto volumétrico lineal]
│   ├── HealingAura [Activa: Cura instantánea, remueve veneno/wither]
│   ├── NecroticDrain [Pasiva: Cura pasiva 15% del daño infligido]
│   ├── EarthTune [Activa: Canaliza el suelo, cambia comida por maná]
│   ├── CounterAttack [Activa: Bloqueo de 1.5s -> Detona contraataque mágico]
│   ├── LightningChain [Activa: Arco eléctrico encadenado entre 3 entidades]
│   ├── SummonZombies [Invocación: 5 infantes blindados aliados por 20s]
│   ├── SummonSkeletons [Invocación: 2 arqueros fantasmales por 25s]
│   ├── BeeSwarm [Invocación: 4 abejas hostiles fijadas al objetivo]
│   └── SummonWolves [Invocación: 3 lobos espectrales domados por 15s]
│
├── MOVILIDAD
│   ├── LightStep [Pasiva: +0.5m altura de paso]
│   ├── Dash [Activa: Impulso horizontal instantáneo con 15 ticks de i-frames]
│   ├── AirJump [Activa: Salto vertical sin daño de impacto de caída]
│   ├── FlurryOfStrikes [Activa: Velocidad III por 10s + Salto kinético]
│   ├── ImpactJump [Activa: Lanzamiento vertical -> Ground slam de 150 dmg]
│   └── StepBoostMobility20 [Pasiva: +0.5m altura de paso]
│
└── DEFENSA
    ├── StoneSkin [Pasiva: Mitigación plana -20% daño recibido]
    ├── PushAndWear [Pasiva: Empuje aumentado y daño a durabilidad enemiga]
    ├── IronStrength [Activa: 12s de invulnerabilidad, cada golpe cura 2.5 PV]
    └── IronFortress [Activa: Resistencia III y Absorción II por 10s]
```

---

## 10. Subsistema de Esbirros e Invocaciones (`MinionHelper`)

* **Identificación Atómica:** Los esbirros generados portan las etiquetas `modrpg_minion` y `modrpg_owner_<UUID>`.
* **Tiempo de Vida Finito (`modrpg_lifespan`):** Controlado en `LivingEvent.LivingTickEvent`. Al expirar el contador de ticks, la entidad se desvanece de forma limpia mediante `.discard()`.
* **Fuego Amigo Inviolable:** `LivingAttackEvent` intercepta y cancela de manera irrevocable cualquier interacción dañina entre el jugador y sus esbirros o entre esbirros que compartan el mismo propietario.
* **Retesteo Dinámico de Agresión:** Al momento en que el jugador inflige daño a cualquier entidad enemiga, se ejecuta `redirectMinionsTarget()`, reescribiendo la IA de combate de todos los esbirros en un radio de $16\text{ m}$ para enfocarse en la víctima.

---

## 11. Protocolo de Red (`modrpg:main` v8)

Canal bidireccional registrado mediante `NetworkRegistry.newSimpleChannel`.

| ID | Clase Paquete | Flujo | Payload | Descripción Funcional |
| :---: | :--- | :---: | :--- | :--- |
| `0` | `PacketCastSkill` | C $\to$ S | `ResourceLocation skillId` | Valida prerrequisitos, descuenta maná, asigna cooldown y dispara `onExecuteActive()`. |
| `1` | `PacketSyncSkillsToClient` | S $\to$ C | Mapas completos de ramas, nodos, cooldowns, práctica, loadout, maná, posturas, memoria de hechizos y especializaciones | Reemplaza atómicamente la capability del lado cliente para renderizado exacto. |
| `2` | `PacketUpgradeSkill` | C $\to$ S | `String branch` | Valida y ejecuta el desembolso de niveles de XP y práctica para ascender de nivel. |
| `3` | `PacketUnlockNode` | C $\to$ S | `ResourceLocation nodeId` | Evalúa `SkillNode.canUnlock()` consumiendo prerrequisitos e integrando el nodo al grafo. |
| `4` | `PacketSyncMana` | S $\to$ C | `float currentMana`, `float maxMana` | Sincronización continua de la reserva de maná sin sobrecargar el bus con otros datos. |
| `5` | `PacketEquipSkill` | C $\to$ S | `ResourceLocation skillId` | Alterna el equipamiento de habilidades activas/definitivas en el vector de la rueda radial ($N \le 8$). |
| `6` | `PacketSelectSkill` | C $\to$ S | `ResourceLocation skillId` | Marca una habilidad equipada como objetivo del acceso rápido de ejecución (`R`). |
| `7` | `PacketTogglePassive` | C $\to$ S | `ResourceLocation skillId` | Activa o desactiva posturas sostenidas (`PASSIVE_TOGGLE`) evaluando suficiencia de maná. |
| `8` | `PacketSaveCraftedSpell` | C $\to$ S | `int slotIndex`, `CompoundTag spellTag` | Escribe un hechizo modular diseñado en la memoria permanente del jugador ($0 \le \text{slot} \le 3$). |

---

## 12. Interfaces de Usuario y Elementos de HUD

### 12.1 Árbol de Habilidades (`SkillTreeScreen` - Tecla `K`)
* Renderizado matricial con traslación 2D infinita (`scrollX`, `scrollY`) y zoom afín continuo $\in [0.55, 1.8]$.
* Grafo vectorial con cálculo de rotación polar para el trazado de aristas entre nodos padre e hijos.
* Pestañas de aislamiento inferior por rama con enmascaramiento por canal alfa ($0\text{xFF}$ para la rama en foco, $0\text{x44}$ para nodos atenuados).
* Botón contextual de ascenso validando en tiempo real las Pruebas de Ascensión, límites de especialización y niveles de XP requeridos.

### 12.2 Rueda de Selección Radial (`RadialMenuScreen` - Tecla `Z`)
* Disposición polar de radio fijo ($R = 80\text{ px}$):
  $$\theta_i = \left(\frac{2\pi}{N}\right) \cdot i - \frac{\pi}{2}$$
* Soporte nativo para alternar estados de posturas (`PASSIVE_TOGGLE`) mediante bordes cromáticos dinámicos (Verde encendido, Gris apagado).
* Validación visual de cooldowns activos (overlay oscuro y segundos restantes) y advertencia por falta de maná.

### 12.3 Altar de Creación de Hechizos (`SpellCraftingScreen` - Tecla `O`)
* Selector visual de combinación elemental, forma y modificador de cadencia.
* Previsualización analítica en vivo: calcula daño estimado proyectado con atributos del jugador, coste en puntos de maná y tiempo de recarga exacto.
* Guardado en cualquiera de las 4 ranuras de memoria accesibles mediante los nodos `custom_spell_1` al `4`.

### 12.4 Heads-Up Display (HUD Overlays)
* **`ManaOverlay`:** Renderizado relativo sobre la barra de armadura/comida. Incluye barra de maná azul, lectura numérica en tiempo real con tasa de drenaje de posturas activas $\text{[-X.X/s]}$, badges de posturas activadas y casillero de la habilidad lista para disparo con tecla `R`.
* **`SkillCooldownOverlay`:** Barra lateral inferior izquierda que proyecta dinámicamente cada habilidad en enfriamiento con temporizador decremental.
* **`ChampionOverlay`:** Marco de combate superior estilo barra de jefe que se activa al fijar la retícula sobre un Caster o Monstruo Campeón; muestra barra de salud proporcional, nombre del arquetipo, afijos activos, debilidad por postura rota e indicadores del elemento cebador (`Primer`).

---

## 13. Mapeo de Teclas por Defecto

| Tecla | Acción Vinculada | Identificador Interno |
| :---: | :--- | :--- |
| `K` | Abrir Árbol de Habilidades | `key.modrpg.open_skills` |
| `Z` | Abrir Rueda Radial de Selección | `key.modrpg.radial_menu` |
| `O` | Abrir Altar de Creación de Hechizos | `key.modrpg.spell_crafter` |
| `R` | Ejecutar Habilidad Activa Seleccionada | `key.modrpg.cast_selected` |
| `V` | Atajo Rápido: Torbellino Ultrapesado | `key.modrpg.spin_attack` |
| `B` | Atajo Rápido: Megacorte Frontal | `key.modrpg.megacut` |
| `G` | Atajo Rápido: Embestida Evasiva (Dash) | `key.modrpg.dash` |
| `X` | Atajo Rápido: Piroclasto Elemental | `key.modrpg.fireball` |
| `C` | Atajo Rápido: Aura de Sanación | `key.modrpg.heal` |

---

## 14. Subsistema de Comandos de Servidor (`/rpg`)

Todos los comandos se estructuran bajo el nodo `/rpg`:

* `/rpg stats`
  * **Permiso:** Nivel 0 (Público).
  * **Salida:** Imprime el estado del personaje: votos de especialización (Principal/Secundaria), niveles de las 5 ramas, métricas de combate acumuladas, maná actual y tasa de regeneración por segundo.
* `/rpg respec`
  * **Permiso:** Nivel 0 (Público).
  * **Salida:** Reinicia los votos de Rama Principal y Secundaria, recortando al límite base de 20 cualquier rama superior para permitir una reasignación de especialización.
* `/rpg upgrade <branch>`
  * **Permiso:** Nivel 0 (Público).
  * **Argumentos:** `branch` (`melee`, `ranged`, `mobility`, `magic`, `defense`).
  * **Salida:** Ejecuta la compra legítima de nivel validando pruebas de ascensión, práctica y descontando niveles de XP de Minecraft.
* `/rpg addlevel <branch> <amount>`
  * **Permiso:** Nivel 2 (Administración / Testing).
  * **Argumentos:** `branch` (String), `amount` (Entero entre 1 y 100).
  * **Salida:** Incrementa arbitrariamente el nivel de la rama omitiendo validaciones económicas y sincroniza atributos físicos.
* `/rpg unlock <skill>`
  * **Permiso:** Nivel 2 (Administración / Testing).
  * **Argumentos:** `skill` (Identificador registrado en `SkillRegistry`).
  * **Salida:** Desbloquea de forma forzada cualquier nodo del árbol ignorando prerrequisitos o niveles de rama.
