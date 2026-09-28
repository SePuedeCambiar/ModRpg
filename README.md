## 1. Especificaciones de Runtime y Dependencias

* **Plataforma:** Minecraft Java Edition.
* **Versión de Minecraft:** `1.20.1`.
* **Cargador de Módulos (ModLoader):** Minecraft Forge `47.4.10` (Rango FML: `[47,)`).
* **Toolchain / JDK:** Java 17 LTS (Compatibilidad de bytecode: nivel 17).
* **Mapeos de Símbolos:** Mojang Official Mappings (`1.20.1`).
* **Identificador de Espacio de Nombres (`modid`):** `modrpg`.
* **Canal de Red:** `modrpg:main` (Protocolo de Red: Versión `"8"`).

---

## 2. Arquitectura de Persistencia y Modelo de Estado (`PlayerSkills`)

El estado del jugador se gestiona de forma desacoplada mediante la API de Capabilities de Minecraft Forge (`PlayerSkillsProvider.PLAYER_SKILLS`), persistido de manera determinista en formato NBT mediante `INBTSerializable<CompoundTag>`.

### 2.1 Variables de Estado Persistente

```
TAG_Compound {
    "BranchLevels": TAG_Compound { (ResourceLocation) -> TAG_Int },
    "UnlockedNodes": TAG_List[TAG_String],
    "PracticeCounters": TAG_Compound { (ResourceLocation) -> TAG_Int },
    "Cooldowns": TAG_Compound { (ResourceLocation) -> TAG_Int },
    "EquippedSkills": TAG_List[TAG_String],
    "ActiveToggles": TAG_List[TAG_String],
    "SpellMemory": TAG_List [
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

### 2.2 Variables de Estado Transitorio (No Persistidas)
* `dashIFrameTicks: int`: Contador decremental de frames de invulnerabilidad absoluta en tiempo de ejecución.
* `togglesForceDeactivated: boolean`: Bandera de notificación al cliente cuando una postura activa se extingue por drenaje total de la reserva de maná.

### 2.3 Jerarquía de Especialización y Topes de Nivel (Level Caps)
El sistema impone restricciones de progresión de ramas (`maxLevel`) dependientes de la asignación de votos en memoria:

| Clasificación de Rama | Asignación Máxima | Condición de Activación |
| :--- | :---: | :--- |
| **Rama Básica** | Nivel $20$ | Estado por defecto para todas las ramas no selladas. |
| **Rama Secundaria** | Nivel $50$ | Asignación permanente del segundo voto tras superar nivel 20. |
| **Rama Principal (Maestría)** | Nivel $100$ | Asignación permanente del primer voto tras superar nivel 20. |

* **Protocolo de Reseteo (`respec`):** Invocar `/rpg respec` anula los punteros `primaryBranch` y `secondaryBranch`, y trunca cualquier rama que exceda el nivel $20$ de vuelta a dicho tope de forma irreversible sin reembolso de experiencia.

### 2.4 Reserva y Cinética de Maná
* **Capacidad Máxima ($M_{\text{max}}$):**
  $$M_{\text{max}} = 100.0 + (L_{\text{magic}} \times 2.0)$$
* **Tasa de Regeneración Pasiva ($R$ por segundo):**
  $$R = 2.0 \times (1.0 + L_{\text{magic}} \times 0.05)$$
* **Regeneración por Tick ($\Delta M_{\text{tick}}$):** $\frac{R}{20}$.
* **Drenaje de Posturas Activas:** Cada tick del servidor deduce $\frac{\text{sustainCost}}{20}$. Si $M_{\text{current}} < \Delta M_{\text{drain}}$, las posturas se apagan de forma automática forzada.

---

## 3. Modelos Matemáticos de Progresión

### 3.1 Costo de Ascenso por Nivel de Experiencia
Para avanzar de un nivel $L$ a $L+1$ en cualquier rama, el costo $C(L)$ en niveles enteros de experiencia de Minecraft se rige por:

$$C(L) = \begin{cases} 
1, & \text{si } L \le 0 \\
100, & \text{si } L \ge 99 \\
\max\left(1, \operatorname{round}\left(1.0 + \left(\frac{L}{99}\right)^{1.6} \times 99.0\right)\right), & \text{si } 0 < L < 99 
\end{cases}$$

### 3.2 Requisito de Práctica Universal
El acumulador de práctica de la rama respectiva debe satisfacer la siguiente cota inferior para permitir la transacción:

$$P \ge K(L+1) = (L + 1) \times 3$$

### 3.3 Hitos de Ascensión (Combat Gates)
La progresión queda bloqueada si el contador acumulativo `modrpg:elite_kills` no satisface los umbrales de rango:

* **Iniciado ($25 \to 26$):** Requiere $\ge 3$ bajas de tipo Campeón o Élite.
* **Maestro ($50 \to 51$):** Requiere $\ge 10$ bajas de tipo Campeón o Élite.
* **Gran Maestro ($75 \to 76$):** Requiere $\ge 25$ bajas de tipo Campeón o Élite.

### 3.4 Fórmulas de Modificación de Atributos Físicos

* **Daño Cuerpo a Cuerpo Aditivo (`Attributes.ATTACK_DAMAGE`):**
  $$D_{\text{bonus}} = D_{\text{base}} \times (L_{\text{melee}} \times 0.02)$$
  *(Lineal estricto: $+200\%$ a Nivel 100; factor neto $\times 3.0$ sobre el daño base).*

* **Velocidad de Movimiento Aditiva (`Attributes.MOVEMENT_SPEED`):**
  $$V_{\text{bonus}} = \left(\frac{L_{\text{mobility}}}{100}\right)^{1.5} \times 0.08$$

* **Factor de Daño Recibido (Rama Defensa):**
  $$F_{\text{defensa}} = \max\left(0.60, 1.0 - \left(\frac{L_{\text{defense}}}{100}\right)^{1.4} \times 0.40\right)$$
  *(Atenuación máxima pasiva: $40\%$ a Nivel 100).*

* **Multiplicador de Proyectiles (`AbstractArrow`):**
  $$M_{\text{flecha}} = 1.0 + \left(\frac{L_{\text{ranged}}}{100}\right)^{1.5} \times 2.5$$

* **Altura de Paso (`ForgeMod.STEP_HEIGHT_ADDITION`):**
  $$H_{\text{adicional}} = \sum h_i, \quad h_i = 0.5 \text{ para cada nodo adquirido en } \{\text{LightStep, StepBoost20, StepBoostMelee3}\}$$

---

## 4. Motor de Magia Modular

Los hechizos son estructuras de datos combinatorias evaluadas dinámicamente según la tupla $(\text{Elemento}, \text{Forma}, \text{Cadencia}, \text{Nivel de Poder})$.

### 4.1 Fórmulas de Evaluación

$$\text{Daño} = \text{Base}_{\text{elem}} \times \text{Mod}_{\text{forma}}^{\text{dmg}} \times \text{Mod}_{\text{cadencia}}^{\text{dmg}} \times (1.0 + (\text{Power} - 1) \times 0.15)$$

$$\text{Coste Maná} = \text{Base}_{\text{elem}}^{\text{mana}} \times \text{Mod}_{\text{forma}}^{\text{mana}} \times \text{Mod}_{\text{cadencia}}^{\text{mana}}$$

$$\text{Cooldown (Ticks)} = \max\left(5, \operatorname{int}\left(\text{Base}_{\text{elem}}^{\text{cd}} \times \text{Mod}_{\text{forma}}^{\text{cd}} \times \text{Mod}_{\text{cadencia}}^{\text{cd}}\right)\right)$$

### 4.2 Matriz de Componentes

| Categoría | Identificador | Daño Base / Mult. | Maná Base / Mult. | CD Base / Mult. | Efecto Primario |
| :--- | :--- | :---: | :---: | :---: | :--- |
| **Elemento** | `FIRE` | $8.0$ | $18.0$ | $40\text{ t}$ | Incendia objetivo ($4\text{ s}$). |
| | `LIGHTNING`| $7.0$ | $22.0$ | $50\text{ t}$ | Salto de arco a $1$ entidad adyacente ($50\%$ daño). |
| | `FROST` | $6.0$ | $16.0$ | $40\text{ t}$ | Lentitud II ($3\text{ s}$). |
| | `VOID` | $9.0$ | $26.0$ | $60\text{ t}$ | Lifesteal ($20\%$ del daño transferido al ejecutor). |
| | `HOLY` | $7.0$ | $20.0$ | $45\text{ t}$ | $+75\%$ daño extra contra no-muertos. |
| **Forma** | `PROJECTILE` | $1.0\times$ | $1.0\times$ | $1.0\times$ | Balística rectilínea estándar. |
| | `BEAM` | $0.9\times$ | $1.15\times$ | $0.8\times$ | Escaneo instantáneo en $16\text{ m}$. |
| | `GROUND_AOE` | $1.3\times$ | $1.35\times$ | $1.4\times$ | Runa cilíndrica $6\text{ m} \times 3\text{ m}$ a $5\text{ m}$ de distancia. |
| | `SELF_AURA` | $0.8\times$ | $1.1\times$ | $1.2\times$ | Detonación esférica de $4\text{ m}$ alrededor del lanzador. |
| | `TOUCH` | $1.4\times$ | $0.8\times$ | $0.7\times$ | Impacto frontal a corta distancia ($2.5\text{ m}$). |
| **Cadencia** | `RAPID_FIRE` | $0.35\times$ | $0.35\times$ | $0.15\times$ | Ráfaga continua. |
| | `BALANCED` | $1.0\times$ | $1.0\times$ | $1.0\times$ | Despliegue estándar. |
| | `HEAVY_BURST`| $2.6\times$ | $2.2\times$ | $3.2\times$ | Detonación de alto impacto. |

### 4.3 Sistema de Reacciones Elementales en Cadena
El impacto elemental almacena una etiqueta temporal en la víctima (`modrpg_primer_elem`) con una vida útil de $100\text{ ticks}$ ($5\text{ s}$). Si se aplica un elemento opuesto durante esta ventana, se consume el estado y se detona una reacción:

* **Superconductor (`FROST` + `LIGHTNING`):** Explosión de área ($5\text{ m}$) que inflige $14.0$ de daño de vacío y aplica debilidad II por $6\text{ s}$.
* **Colapso Gravitatorio (`FIRE` + `VOID`):** Vórtice cinético ($6\text{ m}$) que atrae a las entidades hacia el centro con vector de aceleración forzada, $12.0$ de daño mágico e ignición durante $5\text{ s}$.
* **Pira Purificadora (`FIRE` + `HOLY`):** Detonación sagrada que inflige daño crítico amplificado ($\times 2.0$ si el objetivo es no-muerto), quema al agresor durante $8\text{ s}$ y regenera $6.0\text{ PV}$ al lanzador.
* **Fragilidad Abisal (`FROST` + `VOID`):** Aplica Lentitud V por $4\text{ s}$ e inyecta la etiqueta `modrpg_brittle_ice`. El próximo ataque físico directo contra la entidad consume la etiqueta e inflige $+50\%$ de daño plano adicional.

---

## 5. Inteligencia Artificial Táctica y Escuadrones (F.E.A.R. Engine)

Los monstruos compatibles (`Skeleton`, `Zombie`, `Spider`, `Witch`, etc.) son interceptados durante `EntityJoinLevelEvent` por `EnemyRpgManager` para ser categorizados e integrados en una jerarquía táctica orientada a grupos.

```
       [SquadCoordinator]
               │
      ┌────────┴────────┐
      ▼                 ▼
[Token System]   [Peel Coordinator]
      │                 │
      ├─ Casting Token  └─ Threat Detection (dist < 5m)
      │  (Max 1 Caster)    └─ Vanguard Intercept Force
      │
      └─ Flank Positioning (60°-90° Crossfire Vectors)
```

### 5.1 Arquetipos de Combate

* **Cryo Sniper:** Posicionamiento a $15\text{ m}$, retroceso defensivo (kiting) si la distancia es inferior a $7\text{ m}$. Dispara proyectiles de hielo.
* **Flame Juggernaut:** Agresión frontal pura ($2.5\text{ m}$), resistencia al empuje $+0.4$, vida base $+15.0\text{ PV}$. Ejecuta auras de fuego a quemarropa.
* **Crypt Necromancer:** Mantiene rango de $12\text{ m}$. Si el objetivo se aproxima a menos de $6\text{ m}$, retrocede mediante impulso de repulsión y despliega un zombi siervo.
* **Void Weaver:** Rango medio ($6.5\text{ m}$), ejecuta rayos instantáneos de vacío.
* **Storm Evoker:** Rango medio ($11\text{ m}$), ráfagas eléctricas continuas. Autocuración de emergencia ($8.0\text{ PV}$) si su salud cae por debajo del $40\%$.

### 5.2 Coordinación de Escuadrones (`SquadCoordinator`)
* **Tokens de Concurrencia de Disparo:** Para mitigar la saturación de proyectiles, los lanzadores de un escuadrón deben solicitar un `CastingToken`. Solo una entidad por escuadrón puede canalizar hechizos a la vez durante una ventana de $40\text{ ticks}$.
* **Maniobra de Cobertura y Rescate (Peeling):** Si una unidad a distancia es acorralada ($d < 5\text{ m}$), emite una señal de auxilio (`peelRequestedBy`). Las unidades de vanguardia (`Flame Juggernaut`) anulan su navegación actual y ejecutan una carga en línea recta con multiplicador de velocidad $\times 1.40$ para interceptar al jugador.
* **Fuego Cruzado (Crossfire Flanking):** Las unidades de apoyo calculan vectores de desplazamiento lateral ortogonales al vector jugador-tanque para situarse a ángulos de $60^\circ - 90^\circ$.
* **Ruptura de Moral:** Si el mob designado como líder comandante muere, todos los miembros del escuadrón en un radio de $20\text{ m}$ sufren pánico, interrupción total de navegación (`stop()`), lentitud III y debilidad II durante $60\text{ ticks}$.

### 5.3 Afijos de Élite / Campeones (`ChampionAffix`)
Los campeones reciben $+35\%$ de salud máxima base y una propiedad intrínseca:
1. `COMMANDER`: Otorga un aura de $12\text{ m}$ que confiere velocidad y resistencia a los aliados cada segundo.
2. `RUNIC_SHIELD`: Cancela todo proyectil o flecha que impacte en su semicírculo frontal.
3. `VAMPIRIC`: Cura al mob el $25\%$ del daño infligido al jugador.
4. `MANA_BURN`: Drena $20.0$ puntos de maná del jugador por golpe físico asestado.

### 5.4 Sistema de Telegrafeado e Interrupción (Stagger)
* **Ataque Pesado / Imbloqueable (Partículas Rojas):** Inmune a interrupciones.
* **Ataque Regular Canalizado (Partículas Amarillas):** Asigna temporalmente `modrpg_interruptible_charge`. Si el jugador impacta con un ataque físico con carga de cooldown $\ge 85\%$, el casteo se interrumpe y la entidad entra en estado de aturdimiento (`modrpg_staggered`) durante $40\text{ ticks}$ ($2\text{ s}$), recibiendo $+30\%$ de daño crítico entrante de cualquier fuente.

---

## 6. Subsistema de Invocaciones (`MinionHelper`)

* **Identificación:** Toda entidad invocada recibe las etiquetas `modrpg_minion` y `modrpg_owner_<UUID>`.
* **Tiempo de Vida:** La persistencia se valida mediante la clave entera `modrpg_lifespan` decrementada en `LivingTickEvent`. Al alcanzar $0$, la entidad se descarta (`discard()`).
* **Matriz de Fuego Amigo:** Se anula de forma absoluta cualquier `LivingAttackEvent` si el atacante y la víctima comparten la misma clave de propietario o vínculo invocador-criatura.
* **Redirección de Agresión:** Al infligir daño el jugador, todas las criaturas aliadas en un radio de $16\text{ m}$ conmutan su objetivo de ataque hacia la víctima impactada de forma inmediata.

---

## 7. Protocolo de Red (`modrpg:main`)

Canal bidireccional registrado mediante `NetworkRegistry.newSimpleChannel`.

| ID | Paquete | Dirección | Datos de Carga (Payload) | Lógica de Procesamiento |
| :---: | :--- | :---: | :--- | :--- |
| `0` | `PacketCastSkill` | C $\to$ S | `ResourceLocation skillId` | Valida nodo aprendido, cooldown restante y deduce maná en servidor antes de despachar `onExecuteActive()`. |
| `1` | `PacketSyncSkillsToClient` | S $\to$ C | Estructura completa de mapas, niveles, conjuntos, maná y arreglos de hechizos. | Reemplaza atómicamente el estado local de la capability del cliente en el hilo de renderizado. |
| `2` | `PacketUpgradeSkill` | C $\to$ S | `String branch` | Valida economía, ascensión y práctica. Deduce niveles de XP e incrementa nivel de rama en servidor. |
| `3` | `PacketUnlockNode` | C $\to$ S | `ResourceLocation nodeId` | Valida prerrequisitos de grafo y costos asociados; muta la capability marcando el nodo como adquirido. |
| `4` | `PacketSyncMana` | S $\to$ C | `float currentMana`, `float maxMana` | Actualiza exclusivamente los medidores de maná para estabilidad de HUD a alta frecuencia. |
| `5` | `PacketEquipSkill` | C $\to$ S | `ResourceLocation skillId` | Modifica el arreglo lineal `equippedSkills` (capacidad máxima: 8 ranuras). |
| `6` | `PacketSelectSkill` | C $\to$ S | `ResourceLocation skillId` | Asigna el puntero `selectedSkill` para ejecución inmediata mediante la tecla contextual `[R]`. |
| `7` | `PacketTogglePassive` | C $\to$ S | `ResourceLocation skillId` | Conmuta el estado de activación en el conjunto `activeToggles` y valida viabilidad de reserva de maná. |
| `8` | `PacketSaveCraftedSpell` | C $\to$ S | `int slotIndex`, `CompoundTag spellTag` | Deserializa la tupla de magia modular y la almacena en el índice respectivo $[0, 3]$ del jugador. |

---

## 8. Catálogo Completo de Nodos de Habilidad

### 8.1 Rama Cuerpo a Cuerpo (`modrpg:melee`)
* `melee_step_boost_3` (`PASSIVE_STAT`): Requiere Melee 3. Concede $+0.5\text{ m}$ a la altura de paso.
* `melee_double_attack` (`ACTIVE_ABILITY`): Requiere Melee 4. Si la barra de ataque está al $\ge 92\%$, encadena un segundo golpe al $80\%$ de daño sin respetar los marcos de invulnerabilidad estándar.
* `melee_unarmed_style` (`PASSIVE_STAT`): Requiere Melee 4, `double_attack`. Puños limpios $+10\%$ de daño; armas reducen su daño en $-5\%$.
* `melee_weapon_mastery` (`PASSIVE_STAT`): Requiere Melee 8, `double_attack`. $+15\%$ de daño con armas; $15\%$ de probabilidad de consumir durabilidad doble.
* `melee_leg_trip` (`ACTIVE_ABILITY`, CD: $8\text{ s}$): Requiere Melee 6, `unarmed_style`. Barrido frontal de $3.5\text{ m}$. Aplica lentitud IV por $3\text{ s}$ y frena inercia enemiga.
* `melee_wide_sweep` (`ACTIVE_ABILITY`, CD: $5\text{ s}$): Requiere Melee 12, `weapon_mastery`. Golpe horizontal de $180^\circ$ en radio de $4\text{ m}$ por $1.25\times$ del daño base.
* `melee_ether_dual_sword` (`ACTIVE_ABILITY`): Requiere Melee 5, Magia 6, `double_attack`. Golpe secundario de mano izquierda que inflige el $75\%$ del daño como daño de tipo mágico directo.
* `melee_berserker_stance` (`PASSIVE_TOGGLE`): Requiere Melee 8, `double_attack`. Postura conmutable. Drena $4.5$ de maná/s. Incrementa daño infligido $+30\%$, incrementa daño recibido $+15\%$.
* `melee_heavy_tornado` (`ACTIVE_ABILITY`, CD: $8\text{ s}`): Requiere Melee 10, $20$ bajas CaC, `double_attack`. Ataque circular en $5.5\text{ m}$ con elevación forzada vertical ($Y = +0.45$) y $+50\%$ de daño.
* `melee_vital_cleave` (`ACTIVE_ABILITY`, CD dinámico: $10\text{ s} - 20\text{ s}$, Coste: $15$ Maná): Requiere Melee 15, `heavy_tornado`. Inflige el $10\%$ de la vida actual del objetivo.
* `melee_megacut` (`ACTIVE_ABILITY`, CD: $20\text{ s}$): Requiere Melee 50, $100$ bajas CaC, `heavy_tornado`. Onda de choque lineal de $12\text{ m}$ que perfora entidades infligiendo $3.0\times$ daño.
* `melee_ultracut` (`ULTIMATE`, CD: $10\text{ min}$ tras impacto): Requiere Melee 100, $250$ bajas CaC, `megacut`. Carga la espada para un impacto único de $+500\%$ de daño crítico directo con detonación colateral del $50\%$ en radio de $6\text{ m}$.

### 8.2 Rama Arquería (`modrpg:ranged`)
* `ranged_tailwind` (`PASSIVE_STAT`): Requiere Ranged 5. Velocidad de flecha incrementada en $+80\%$.
* `ranged_rapid_fire` (`ACTIVE_ABILITY`, CD: $15\text{ s}$): Requiere Ranged 12, `tailwind`. Dispara ráfaga inmediata de 6 flechas a velocidad máxima sin tensar arco.
* `ranged_homing_arrow` (`ACTIVE_ABILITY`, CD: $10\text{ s}$ base): Requiere Ranged 16, `tailwind`. Rastrea entidades en $25\text{ m}$ y dispara proyectil guiado. Penalización de $+10\text{ s}$ de cooldown en caso de fallo (Whiff).
* `ranged_crossbow_artillery` (`ACTIVE_ABILITY`): Requiere Ranged 20, $30$ bajas flecha, `tailwind`. Convierte cohetes de ballesta en detonaciones sónicas que añaden $+15.0 + (L_{\text{ranged}} \times 0.5)$ de daño.
* `ranged_hypersonic` (`ACTIVE_ABILITY`): Requiere Ranged 50, `tailwind`. Disparos agachado adquieren aceleración $\times 1.5$, remueven caída balística (`NoGravity`) y penetran hasta 5 entidades.

### 8.3 Sinergias Híbridas (`modrpg:hybrid`)
* `hybrid_hunter` (`HYBRID_SYNERGY`): Requiere Melee 25, Ranged 25, `double_attack`, `tailwind`. Las flechas marcan objetivos (`Glowing`); ataques CaC consumen la marca desatando $+150\%$ de daño de vacío.
* `hybrid_arrow_propulsion` (`HYBRID_SYNERGY`, CD: $3\text{ s}$): Requiere Melee 35, Ranged 30, `hybrid_hunter`. Disparar a los pies ($\text{pitch} > 55^\circ$) genera una propulsión vertical ($Y = 1.35$) y caída lenta por $4\text{ s}$.
* `hybrid_sword_quiver` (`HYBRID_SYNERGY`): Requiere Melee 70, Ranged 50, `hybrid_hunter`. Suma $+1.5\times$ del atributo `ATTACK_DAMAGE` del jugador al impacto de cualquier flecha.
* `hybrid_combined_ultimate` (`ULTIMATE`): Requiere Melee 100, Ranged 70, `ultracut`, `sword_quiver`. Al desatar Ultracorte, genera una lluvia orbital de proyectiles mágicos en radio de $7\text{ m}$ infligiendo el $40\%$ del daño base del corte.

### 8.4 Rama Magia (`modrpg:magic`)
* `magic_fireball` (`ACTIVE_ABILITY`, CD: $5\text{ s}$, Coste: $20$ Maná): Requiere Magia 3. Raycast penetrante de $10\text{ m}$ que inflige $8.0 + (L_{\text{magic}} \times 0.4)$ de daño de fuego.
* `magic_healing_aura` (`ACTIVE_ABILITY`, CD: $12\text{ s}$, Coste: $35$ Maná): Requiere Magia 6, `fireball`. Restaura $6.0 + (L_{\text{magic}} \times 0.2)\text{ PV}$, remueve efectos alterados y otorga Regeneración II por $5\text{ s}$.
* `magic_necrotic_drain` (`PASSIVE_STAT`): Requiere Magia 15, `fireball`. El daño mágico infligido transfiere el $15\%$ en forma de curación directa al lanzador.
* `magic_earth_tune` (`ACTIVE_ABILITY`, CD: $10\text{ s}$): Requiere Magia 8, `fireball`. Estando sobre tierra, roca o arena, consume saturación de comida para regenerar $40.0$ puntos de maná.
* `magic_counter_attack` (`ACTIVE_ABILITY`, CD: $12\text{ s}$, Coste: $25$ Maná): Requiere Magia 12, `fireball`. Abre ventana de guardia de $1.5\text{ s}$. Anula el daño recibido y desata un contraataque mágico de $18.0$ de daño sobre un máximo de 2 atacantes.
* `magic_lightning_chain` (`ACTIVE_ABILITY`, CD: $7\text{ s}$, Coste: $30$ Maná): Requiere Magia 20, `fireball`. Cadena de relámpagos que salta hasta 3 entidades en un radio de $8\text{ m}$ infligiendo $10.0 + (L_{\text{magic}} \times 0.3)$ de daño.
* `magic_summon_zombies` (`ACTIVE_ABILITY`, CD: $20\text{ s}$, Coste: $45$ Maná): Requiere Magia 10, `fireball`. Invoca 5 zombis infantes protegidos con cascos de hierro durante $20\text{ s}$.
* `magic_summon_skeletons` (`ACTIVE_ABILITY`, CD: $25\text{ s}$, Coste: $50$ Maná): Requiere Magia 15, `summon_zombies`. Invoca 2 esqueletos arqueros aliados por $25\text{ s}$.
* `magic_bee_swarm` (`ACTIVE_ABILITY`, CD: $12\text{ s}$, Coste: $35$ Maná): Requiere Magia 12, `fireball`. Fija un enjambre de 4 abejas hostiles contra la entidad apuntada durante $8\text{ s}$.
* `magic_summon_wolves` (`ACTIVE_ABILITY`, CD: $18\text{ s}$, Coste: $40$ Maná): Requiere Magia 16, `bee_swarm`. Invoca 3 lobos domesticados aliados durante $15\text{ s}$.
* `custom_spell_1` a `custom_spell_4` (`ACTIVE_ABILITY`): Ranuras modulares ejecutables vinculadas a la memoria de hechizos.

### 8.5 Rama Movilidad (`modrpg:mobility`)
* `mobility_light_step` (`PASSIVE_STAT`): Requiere Movilidad 2. $+0.5\text{ m}$ de altura de paso.
* `mobility_dash` (`ACTIVE_ABILITY`, CD: $3\text{ s}$): Requiere Movilidad 10, `light_step`. Impulso horizontal de vector $1.5$ con $15\text{ ticks}$ ($0.75\text{ s}$) de frames de invulnerabilidad absoluta.
* `mobility_air_jump` (`ACTIVE_ABILITY`, CD: $4\text{ s}$): Requiere Movilidad 20, `light_step`. Impulso vertical $Y = 0.95$ en el aire con supresión total del daño de caída.
* `mobility_flurry_of_strikes` (`ACTIVE_ABILITY`, CD: $12\text{ s}$): Requiere Movilidad 10, `dash`. Salto frontal cinético y Velocidad III durante $10\text{ s}$.
* `mobility_impact_jump` (`ACTIVE_ABILITY`, CD: $15\text{ s}$): Requiere Movilidad 15, `air_jump`. Lanzamiento orbital vertical ($Y = 1.65$). Al colisionar contra el suelo, detona un impacto sísmico de $150.0$ de daño en radio de $5.5\text{ m}$ con vector de empuje de factor $1.8$.
* `mobility_step_boost_20` (`PASSIVE_STAT`): Requiere Movilidad 20, `light_step`. $+0.5\text{ m}$ de altura de paso adicional.

### 8.6 Rama Defensa (`modrpg:defense`)
* `defense_stone_skin` (`PASSIVE_STAT`): Requiere Defensa 3. Mitiga pasivamente un $20\%$ del daño entrante total.
* `defense_push_and_wear` (`PASSIVE_STAT`): Requiere Defensa 8, `stone_skin`. Los golpes aplican empuje masivo de factor $1.5$ y erosionan $5$ puntos de durabilidad al arma sostenida por la víctima.
* `defense_iron_strength` (`ACTIVE_ABILITY`, CD: $30\text{ s}$): Requiere Defensa 14, `stone_skin`. Anula por completo todo daño recibido durante $12\text{ s}$; cada golpe interceptado cura $+2.5\text{ PV}$.
* `defense_iron_fortress` (`ACTIVE_ABILITY`, CD: $30\text{ s}$): Requiere Defensa 20, `iron_strength`. Otorga Resistencia III ($60\%$ mitigación neta vanilla) y Absorción II durante $10\text{ s}$.

---

## 9. Controles e Interfaces de Usuario (HUD)

### 9.1 Asignación de Teclado
* `[K]`: Apertura de la interfaz de navegación del grafo de habilidades (`SkillTreeScreen`).
* `[Z]`: Apertura del menú radial contextual (`RadialMenuScreen`).
* `[O]`: Acceso a la interfaz de ensamblaje modular de magia (`SpellCraftingScreen`).
* `[R]`: Ejecución instantánea de la habilidad actualmente seleccionada en el puntero activo.
* Atajos Directos Opcionales: `[V]` (Tornado), `[B]` (Megacorte), `[G]` (Dash), `[X]` (Fireball), `[C]` (Cura).

### 9.2 Capas de Renderizado en Pantalla (Overlays)
* `ManaOverlay`: Ubicado sobre la barra de alimentos del jugador. Renderiza nivel numérico, tasa neta de drenaje por posturas activas, ranura de habilidad activa seleccionada (`[R]`) y badges de posturas activadas.
* `SkillCooldownOverlay`: Situado a la izquierda del HUD primario. Proyecta de manera secuencial los iconos sombreados de todas las habilidades en recarga y el remanente en segundos.
* `ChampionOverlay`: Barra de jefe contextual en el sector superior activada cuando el cursor enfoca a un monstruo de tipo Campeón o Lanzador Táctico. Expone barra de vida numérica, afijo de élite, elemento activo de cebado y estado de Rompe-Postura (`STAGGERED`).

---

## 10. Interfaz de Línea de Comandos (`/rpg`)

| Sintaxis del Comando | Nivel de Permiso | Descripción Técnica |
| :--- | :---: | :--- |
| `/rpg stats` | $0$ (Público) | Vuelca en la consola del jugador la totalidad de ramas, asignación de votos, métricas de práctica, contadores y reserva de maná. |
| `/rpg respec` | $0$ (Público) | Revoca los votos de Rama Principal y Secundaria; restablece cualquier rama superior a nivel 20 de vuelta al límite de 20. |
| `/rpg upgrade <branch>` | $0$ (Público) | Ejecuta el intento de compra del siguiente nivel de la rama parametrizada deduciendo XP y validando hitos. |
| `/rpg addlevel <branch> <amount>` | $2$ (Operador) | Inyecta niveles arbitrarios ($1-100$) forzando el recalculo de atributos y sincronización de red sin costo de recursos. |
| `/rpg unlock <skill_id>` | $2$ (Operador) | Fuerza la adquisición del nodo omitiendo de forma exhaustiva validaciones de prerrequisitos, ramas o práctica. |
