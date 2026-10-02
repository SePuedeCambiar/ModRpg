
```markdown
# ESPECIFICACIÓN TÉCNICA DE SISTEMAS: MODRPG (`modrpg`)
**Versión de Especificación:** 2.0.0-RELEASE  
**Entorno de Ejecución:** Minecraft Java Edition 1.20.1 — Minecraft Forge 47.4.10  

---

## 1. PARÁMETROS DE RUNTIME Y DEPENDENCIAS

* **Entorno de Ejecución:** Java Virtual Machine (JVM) 17 LTS (Bytecode target: 17).
* **Plataforma:** Minecraft Java Edition `1.20.1`.
* **Cargador de Módulos (ModLoader):** Minecraft Forge `47.4.10` (Rango FML: `[47,)`).
* **Mapeos de Símbolos:** Mojang Official Mappings (`1.20.1`).
* **Identificador de Espacio de Nombres (`modid`):** `modrpg`.
* **Protocolo de Red:** `modrpg:main` — Canal Bidireccional `SimpleChannel` (Versión `"8"`).
* **Presupuesto de Cómputo por Tick:** $\le 50.0\text{ ms}$ (Tasa nominal fija: $20.0\text{ TPS}$).

---

## 2. ARQUITECTURA DE PERSISTENCIA Y MODELO DE ESTADO (`PlayerSkills`)

El estado del jugador se gestiona de forma desacoplada mediante la API de Capabilities de Forge (`PlayerSkillsProvider.PLAYER_SKILLS`), serializado de forma determinista mediante `INBTSerializable<CompoundTag>`.

### 2.1 Esquema de Persistencia NBT del Jugador
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

### 2.2 Topes de Nivel por Especialización  (Level Caps)

| Clasificación de Rama | Límite Máximo | Regla de Asignación |
| :--- | :---: | :--- |
| **Rama Básica** | Nivel $20$ | Estado por defecto para toda rama sin voto sellado. |
| **Rama Secundaria** | Nivel $50$ | Asignación irreversible del segundo voto al superar Nivel 20. |
| **Rama Principal (Maestría)** | Nivel $100$ | Asignación irreversible del primer voto al superar Nivel 20. |

* **Protocolo de Reseteo (`/rpg respec`):** Anula `primaryBranch` y `secondaryBranch`. Trunca de forma determinista cualquier rama con nivel $> 20$ al valor base de $20$. No existe reembolso de experiencia ni de práctica invertida.

### 2.3 Cinética y Capacidad de Maná
* **Capacidad Máxima ($M_{\text{max}}$):**
  $$M_{\text{max}} = 100.0 + (L_{\text{magic}} \times 2.0)$$
* **Regeneración Pasiva por Segundo ($R$):**
  $$R = 2.0 \times (1.0 + L_{\text{magic}} \times 0.05)$$
* **Drenaje por Posturas Activas:** Evaluado en cada tick de servidor ($\Delta M_{\text{tick}} = \frac{\text{costeSustain}}{20}$). Si $M_{\text{current}} < \Delta M_{\text{tick}}$, se fuerza la desactivación inmediata del conjunto `activeToggles`.

---

## 3. MODELOS MATEMÁTICOS DE PROGRESIÓN

### 3.1 Costo de Nivel en Experiencia
$$C(L) = \begin{cases} 
1, & L \le 0 \\
100, & L \ge 99 \\
\max\left(1, \text{round}\left(1.0 + \left(\frac{L}{99}\right)^{1.6} \times 99.0\right)\right), & 0 < L < 99 
\end{cases}$$

### 3.2 Práctica Requerida
$$P_{\text{req}}(L+1) = (L + 1) \times 3$$

### 3.3 Hitos de Combate (Ascension Gates)
* **Iniciado ($25 \to 26$):** Requiere $\ge 3$ bajas registradas en `modrpg:elite_kills`.
* **Maestro ($50 \to 51$):** Requiere $\ge 10$ bajas registradas en `modrpg:elite_kills`.
* **Gran Maestro ($75 \to 76$):** Requiere $\ge 25$ bajas registradas en `modrpg:elite_kills`.

---

## 4. MOTOR DE MAGIA MODULAR

La instancia `CraftedSpell` procesa la tupla $(\text{Elemento}, \text{Forma}, \text{Cadencia}, \text{Nivel de Poder})$.

### 4.1 Fórmulas de Evaluación Dinámica
$$\text{Daño} = \text{Base}_{\text{elem}}^{\text{dmg}} \times \text{Mod}_{\text{forma}}^{\text{dmg}} \times \text{Mod}_{\text{cadencia}}^{\text{dmg}} \times (1.0 + (\text{Power} - 1) \times 0.15)$$
$$\text{Coste Maná} = \text{Base}_{\text{elem}}^{\text{mana}} \times \text{Mod}_{\text{forma}}^{\text{mana}} \times \text{Mod}_{\text{cadencia}}^{\text{mana}}$$
$$\text{Cooldown (Ticks)} = \max\left(5, \text{int}\left(\text{Base}_{\text{elem}}^{\text{cd}} \times \text{Mod}_{\text{forma}}^{\text{cd}} \times \text{Mod}_{\text{cadencia}}^{\text{cd}}\right)\right)$$

### 4.2 Matriz de Reacciones Elementales en Cadena
El cebado elemental almacena la etiqueta `modrpg_primer_elem` en la víctima durante $100\text{ ticks}$ ($5.0\text{ s}$). El impacto de un elemento secundario detona:
* **Superconductor (`FROST` + `LIGHTNING`):** Detonación esférica de $5\text{ m}$. Inflige $14.0$ de daño indirecto y aplica Debilidad II por $6\text{ s}$.
* **Colapso Gravitatorio (`FIRE` + `VOID`):** Vórtice cinético de $6\text{ m}$. Aplica vector de succión acelerada hacia el centro, $12.0$ de daño e ignición por $5\text{ s}$.
* **Pira Purificadora (`FIRE` + `HOLY`):** Detonación sagrada ($\times 2.0$ daño a no-muertos), ignición por $8\text{ s}$ y regenera $6.0\text{ HP}$ al lanzador.
* **Fragilidad Abisal (`FROST` + `VOID`):** Lentitud V por $4\text{ s}$ y asignación de etiqueta `modrpg_brittle_ice`. El siguiente impacto físico consume la etiqueta e inflige $+50\%$ de daño plano adicional.

---

## 5. ARQUITECTURA DE INTELIGENCIA ARTIFICIAL TRIPARTITA

El subsistema de IA hostil descarta la inflación artificial de puntos de salud o multiplicadores de daño. Opera a través de tres capas desacopladas:

```
                            SISTEMA TRIPARTITO DE IA

┌────────────────────────────────────────────────────────────────────────┐
│ TIER 3: CAPA ESTRATÉGICA ADAPTATIVA (NÉMESIS ENGINE)                  │
│  - Telemetría de hábitos del jugador: PlayerCombatProfiler             │
│  - Registro de capitanes persistentes en disco: NemesisSavedData       │
│  - Contramedidas reactivas (Anti-Aire, Rompe-Escudo, Silenciador)      │
│  - Generación de personalidades, títulos y diálogos contextuales       │
│  - Economía de hordas cerrada para 100 Días Hardcore: N = min(10, ...) │
├────────────────────────────────────────────────────────────────────────┤
│ TIER 2: CAPA DE RITMO Y TENSIÓN PSICOLÓGICA (ALIEN MACRO-DIRECTOR)     │
│  - Evaluación del Medidor de Estrés del jugador S(t) en [0.0, 1.0]     │
│  - Rastreo de huellas acústicas por vibración: AudioFootprintTracker   │
│  - Detección de ventanas de vulnerabilidad: PlayerVulnerabilityDetector│
│  - Máquina de 4 fases: REPRIEVE ➔ BUILD_UP ➔ AMBUSH_READY ➔ CLIMAX     │
│  - Sistema de Susurros espaciales y cumplimiento de las 3 Leyes Justas │
├────────────────────────────────────────────────────────────────────────┤
│ TIER 1: CAPA MICRO-TÁCTICA DE ESCUADRÓN (F.E.A.R. SQUAD ENGINE)        │
│  - Control de concurrencia mediante tokens con Lease Heartbeat         │
│  - Grafo espacial de coberturas por Time-Slicing y Dot Product         │
│  - Radio-Táctica: Barks auditivos, mensajes espaciales y subtítulos     │
│  - Telegrafiado visual por código de color (Amarillo / Rojo)           │
│  - Metas coordinadas: Peeling, Fuego de Supresión y Flanqueo 3D        │
└────────────────────────────────────────────────────────────────────────┘
```

---

### 5.1 TIER 1: CAPA MICRO-TÁCTICA DE ESCUADRÓN (F.E.A.R.)

#### A. Concurrencia de Ataque por Tokens (`SquadTacticalToken`)
Para erradicar la saturación caótica de proyectiles, las entidades deben poseer un token activo emitido por `SquadCoordinator.Squad`:
* `PEEL` (Prioridad 100, Lease: $60\text{ ticks}$): Asignado exclusivamente a la vanguardia ante peticiones de socorro.
* `PRIMARY_ATTACK` (Prioridad 50, Lease: $50\text{ ticks}$): Habilita canalizaciones pesadas o hechizos de área. Capacidad escalada: $\max(1, \lfloor \text{miembros} / 3 \rfloor)$.
* `SUPPRESSION` (Prioridad 10, Lease: $80\text{ ticks}$): Habilita ráfagas de hostigamiento ligero.
* **Mecanismo Drop-on-Disruption:** Si el portador muere, sufre aturdimiento (`STAGGERED`) o cae en pánico, el token ejecuta `release()` de forma síncrona en el mismo tick.

#### B. Grafo de Coberturas Optimizadas (`SquadCoverManager`)
* **Time-Slicing:** La evaluación espacial corre una sola vez cada $10\text{ ticks}$ ($0.5\text{ s}$) por escuadrón.
* **Filtro por Producto Escalar (Dot Product):** Dado el vector objetivo-muro $\vec{D} = \vec{W} - \vec{T}$, únicamente se evalúan las caras cardinales donde $\vec{N} \cdot \vec{D} > 0$, descartando el $75\%$ de bloques antes de emitir raycasts.
* **Reserva Exclusiva (`CoverNode`):** Cada posición de cobertura admite un único ocupante (`claimedBy`), impidiendo colisiones físicas entre aliados.

#### C. Radio-Táctica y Telegrafiado Sensorial (`SquadBarkManager` & `TelegraphVisualHelper`)
* **Avisos Espaciales:** Notificación en Action Bar y modulación de pitch en un radio de $\le 18\text{ m}$:
  * Flanqueo: *«§6[Escuadrón] §e¡Rodeando por el flanco!»* (`NOTE_BLOCK_SNARE` en pitch 1.75).
  * Supresión: *«§c[Vanguardia] §f¡Fijen al blanco! ¡No lo dejen asomar!»* (`RAID_HORN` en pitch 1.10).
  * Auxilio: *«§d[Hechicero] §c¡Me tienen acorralado! ¡A mí!»* (`GHAST_HURT` en pitch 1.55).
  * Intercepción: *«§6[Vanguardia] §4¡Atrás, insecto! ¡Enfócame a mí!»* (`RAVAGER_ROAR` en pitch 0.80).
  * Ruptura de Moral: *«§4§l¡LÍDER CAÍDO! §7¡El escuadrón entra en pánico!»* (`RAID_HORN` en pitch 0.60).
* **Telegrafiado de Canalización:**
  * **Halo Amarillo (Interrumpible):** Anillo de partículas `ELECTRIC_SPARK`. Si el jugador impacta con recarga $\ge 85\%$, aplica estado `STAGGERED` por $45\text{ ticks}$ ($+30\%$ daño crítico entrante).
  * **Aura Roja (Imbloqueable):** Fuego basal y latidos `WARDEN_HEARTBEAT`. Desactiva escudos; requiere evasión por *Dash*.

#### D. Metas Tácticas Especializadas
* `TacticalPeelGoal`: Si un aliado frágil a $< 5\text{ m}$ pide auxilio, la vanguardia toma `PEEL` token, esprinta a $\times 1.45$ y ejecuta un golpe de retroceso ($1.6$ knockback horizontal).
* `TacticalBoundingGoal`: El tirador adquiere `SUPPRESSION` token y dispara ráfagas de 3 flechas para fijar al jugador mientras los aliados avanzan de cobertura en cobertura.
* `TacticalFlankGoal`: Desplaza al flanqueador por un cono de $60^\circ - 90^\circ$. Si impacta desde el punto ciego del jugador ($\text{dot} < 0.25$), aplica daño crítico de emboscada.

---

### 5.2 TIER 2: CAPA DE RITMO Y TENSIÓN PSICOLÓGICA (ALIEN DIRECTOR)

#### A. Medidor de Estrés Continuo (`PlayerStressTracker`)
Evaluado cada $20\text{ ticks}$ ($1.0\text{ s}$):
$$S(t) = \text{clamp}\left( S(t-1) + \sum \Delta S_{\text{in}} - \Delta S_{\text{out}}, \; 0.0, \; 1.0 \right)$$

* **Entradas de Tensión ($\Delta S_{\text{in}}$):**
  * Salud $< 40\%$: $+0.04/\text{s}$.
  * Maná $< 20\%$: $+0.02/\text{s}$.
  * Mobs hostiles a $< 6\text{ m}$: $+0.04/\text{s}$.
  * Mobs hostiles a $< 15\text{ m}$: $+0.015/\text{s}$.
  * Nivel de luz $\le 4$: $+0.01/\text{s}$.
  * Profundidad $Y < 0$: $+0.015/\text{s}$.
  * Daño directo recibido: $+0.15$ instantáneo.
* **Alivio ($\Delta S_{\text{out}}$):**
  * Fuera de peligro: $-0.03/\text{s}$.
  * Zona segura ($\text{Luz} \ge 12$ sin enemigos): $-0.05/\text{s}$.

#### B. Huella Acústica y Detección de Vulnerabilidad (`AudioFootprintTracker`)
* **Radios de Propagación de Onda:**
  * Sigilo (`Shift`): $0.0\text{ m}$ (Inaudible).
  * Caminar: $6.0\text{ m}$.
  * Correr (`Sprint`): $14.0\text{ m}$.
  * Picar bloques con dureza $\ge 1.5$: $18.0\text{ m}$.
  * Conjurar magia modular: $24.0\text{ m}$.
  * Detonaciones: $35.0\text{ m}$.
* **Banderas de Vulnerabilidad Monitoreadas:**
  * `MINING_LOCK`: Picar de forma ininterrumpida por $\ge 2.0\text{ s}$.
  * `CONSUMING_ITEM`: Acción de comer o beber poción activa.
  * `MANA_EXHAUSTED`: Reserva de maná $< 15\%$.
  * `CORNERED_CHOKE`: $3$ o más caras cardinales bloqueadas por sólidos (Túnel 1x2 / Callejón).

#### C. Máquina de Estados del Director (`MacroDirectorManager`)
1. `REPRIEVE`: Tras un clímax o caída de líder, bloquea emboscadas e incursiones por $60 - 90\text{ s}$.
2. `BUILD_UP`: Emite susurros (`DirectorWhisper`) hacia el punto ciego del jugador ($12\text{ m}$ a la espalda) o hacia ecos acústicos. Mobs activan `StalkerLurkGoal`.
3. `AMBUSH_READY`: Disparado si $S \ge 0.70$ o surge una bandera de vulnerabilidad. El escuadrón converge a la salida del túnel (`CUTOFF_CHOKE`).
4. `CLIMAX`: Asalto abierto F.E.A.R. Retorna a `REPRIEVE` al morir los agresores o si $S \ge 0.95$ por tiempo excesivo (Piedad del Director).

#### D. Las 3 Leyes de la Emboscada Justa
1. **Pre-Aviso Sensorial:** $1.5\text{ s}$ ($30\text{ ticks}$) antes del impacto, cae grava del techo (`SoundEvents.GRAVEL_BREAK` en pitch 0.75 + partículas `FALLING_DUST`) con la alerta *«[Crujido sutil en el techo... Algo se aproxima]»*.
2. **Asedio Espacial:** Los enemigos bloquean el vector de escape; no se permite la aparición en el campo visual del jugador.
3. **Regla de Piedad (Fail-Safe):** Si el jugador cae a $\le 3\text{ corazones}$ ($6.0\text{ HP}$), la ofensiva se detiene $2.0\text{ s}$ mientras los mobs retroceden y emiten burlas, garantizando una ventana de contrajuego defensivo.

---

### 5.3 TIER 3: CAPA ESTRATÉGICA ADAPTATIVA (NÉMESIS ENGINE)

Diseñado para partidas **Hardcore de 100 Días**. Al no existir múltiples vidas para el jugador, el ascenso de un Némesis se rige por **encuentros no resueltos, daños críticos y escapes con vida**.

#### A. Perfilador Continuo del Jugador (`PlayerCombatProfiler`)
Monitorea la distribución del vector de combate $\vec{\Phi}_{\text{jugador}}$:
* `AIR_JUMPER`: Acciones de *Air Jump* o *Impact Jump* $\ge 25\%$ del total de eventos.
* `SHIELD_TURTLE`: Bloqueos exitosos con escudo $\ge 25\%$.
* `ELEMENTAL_MAGE`: Conjuros de magia modular $\ge 30\%$ (registra elemento primario).
* `SNIPER_KITER`: Daño con flechas $\ge 35\%$.
* `MELEE_BERSERKER`: Daño cuerpo a cuerpo directo $\ge 40\%$.

#### B. Cuadro de Honor y Persistencia (`NemesisSavedData`)
Persistido en `data/modrpg_nemesis.dat` del servidor. Cada `NemesisCaptain` almacena:
```
TAG_Compound {
"UUID": TAG_IntArray [UUID],
"Name": TAG_String,
"Title": TAG_String,
"CounterTrait": TAG_String,
"Prestige": TAG_Int,
"Day": TAG_Int,
"Status": TAG_String ("STALKING", "WAITING_REVENGE", "DEAD"),
"Scar": TAG_String ("FIRE", "FROST", "LIGHTNING", "NONE")
}
```

#### C. Contramedidas Tácticas (Cero Esponjas de Daño)
Los Capitanes Némesis poseen la misma salud base de un Campeón ($+35\%$), pero anulan el meta del jugador:

| Rasgo Némesis (`NemesisTrait`) | Contramedida al Estilo | Mecánica de Neutralización |
| :--- | :--- | :--- |
| `ANTI_AIR_GRAVITY` | `AIR_JUMPER` | Asigna `modrpg_grounded_tether` por $6\text{ s}$. Fuerza velocidad vertical $Y \le 0.0$. Anula *Air Jump* y *Ground Slam*. |
| `SHIELD_BREAKER` | `SHIELD_TURTLE` | Golpes pesados desactivan el escudo por $5.0\text{ s}$ (`disableShield(true)`). |
| `MANA_DRAINER` | `ELEMENTAL_MAGE` | Drena $25.0$ de maná por golpe. Disipa los cebados elementales a $1.0\text{ s}$ de duración máxima. |
| `PROJECTILE_DEFLECTOR` | `SNIPER_KITER` | Escudo cinético frontal que desvía el $100\%$ de flechas en cono de $180^\circ$. |

#### D. Meta de Retirada Táctica (`NemesisEscapeGoal`)
Si la salud del Capitán cae a $< 25\%$ de HP:
1. Detona una cortina de humo densa (`CAMPFIRE_COSY_SMOKE`) y aplica Ceguera por $2\text{ s}$ al jugador.
2. Emite la burla de escape: *«¡Esta herida la pagarás con tu vida cuando menos lo esperes!»*.
3. Esprinta a $\times 1.40$ en vector opuesto. Al romper la línea de visión por $4\text{ s}$ o alejarse a $> 26\text{ m}$, transmuta su estado a `WAITING_REVENGE`, suma $+25$ de prestigio y ejecuta `discard()` sin soltar botín.

#### E. Personalidad y Diálogos Procedurales (`NemesisPersonalityEngine`)
* **Construcción de Identidad:** Prefijos (*Vor, Az, Mala, Thru...*) + Sufijos (*gul, gar, kor, gash...*) acoplados al título del contra-rasgo (*«el Rompe-Cielos»*, *«el Hendidor de Acero»*, *«el Come-Hechizos»*).
* **Memoria de Cicatrices:** Si casi muere por fuego en el día 23, reaparece con el título *«el Calcinado»* y el diálogo: *«¿Creías que las llamas me consumirían en aquella cueva? ¡El fuego solo templó mi odio!»*.
* **Aviso Cinematográfico:** Entrada anunciada por cuerno de guerra grave, resonancia de campana, transmisión en chat carmesí y marco de jefe exclusivo en `ChampionOverlay`.

#### F. Escala Asintótica y Economía de Hordas en 100 Días (`NemesisHordeManager`)
$$N_{\text{horda}}(D) = \min\left(10, \; 3 + \left\lfloor \frac{D}{15} \right\rfloor\right)$$

* **Día 1 - 14:** Escuadra de **3 monstruos** (1 Tanque, 1 Tirador, 1 Flanqueador).
* **Día 15 - 29:** Escuadra de **4 monstruos** (Aparición del primer Capitán Némesis novato).
* **Día 30 - 44:** Escuadra de **5 monstruos**.
* **Día 45 - 74:** Escuadra de **6 a 7 monstruos** (Capitán Némesis veterano).
* **Día 75 - 100:** **Tope absoluto de 8 a 10 monstruos de élite** (El Gran Señor de la Guerra Némesis + 9 especialistas coordinados).
* **Supresión de Spawn Vanilla:** En un radio de $48\text{ m}$ alrededor de una incursión Némesis activa, se bloquea la generación aleatoria de monstruos vanilla para sostener $20.0\text{ TPS}$ continuos.
* **Recompensa de Derrota Definitiva:** $+25$ al acumulador `modrpg:elite_kills`, $8$ niveles directos de experiencia y entrega garantizada de lingote de netherite o manzana dorada encantada.

---

## 6. SUBSISTEMA DE INVOCACIONES (`MinionHelper`)

* **Identificación Formal:** Toda criatura aliada porta `modrpg_minion` y `modrpg_owner_<UUID>`.
* **Tiempo de Vida:** Decrementado en `LivingTickEvent` mediante `modrpg_lifespan`. Al alcanzar $0$, la entidad ejecuta `discard()`.
* **Protección contra Fuego Amigo:** Se anula de forma absoluta `LivingAttackEvent` si ambas entidades comparten el mismo propietario o vínculo invocador-esbirro.
* **Focalización Táctica:** Cualquier impacto infligido por el jugador redirecciona de forma inmediata la agresión de todos los esbirros en $16\text{ m}$ hacia el objetivo impactado.

---

## 7. PROTOCOLO DE RED (`modrpg:main`)

Canal bidireccional operado a través de `NetworkRegistry.newSimpleChannel`.

| ID | Paquete | Flujo | Payload | Acción de Procesamiento |
| :---: | :--- | :---: | :--- | :--- |
| `0` | `PacketCastSkill` | C $\to$ S | `ResourceLocation skillId` | Valida adquisición, cooldown y deduce maná en servidor antes de despachar `onExecuteActive()`. Emite onda acústica de $24\text{ m}$. |
| `1` | `PacketSyncSkillsToClient` | S $\to$ C | Mapas completos, sets, maná, memoria de hechizos y especializaciones. | Reemplaza de forma atómica el estado de la capability local en el hilo de renderizado del cliente. |
| `2` | `PacketUpgradeSkill` | C $\to$ S | `String branch` | Valida economía, hitos de ascensión y práctica. Deduce XP e incrementa nivel de rama. |
| `3` | `PacketUnlockNode` | C $\to$ S | `ResourceLocation nodeId` | Valida prerrequisitos del grafo y deduce costos asociados; desbloquea el nodo en la capability. |
| `4` | `PacketSyncMana` | S $\to$ C | `float currentMana`, `float maxMana` | Sincronización ligera de alta frecuencia para estabilización del medidor del HUD. |
| `5` | `PacketEquipSkill` | C $\to$ S | `ResourceLocation skillId` | Asigna la habilidad a la lista lineal `equippedSkills` (capacidad máxima: 8). |
| `6` | `PacketSelectSkill` | C $\to$ S | `ResourceLocation skillId` | Asigna el puntero contextual `selectedSkill` para ejecución inmediata con la tecla `[R]`. |
| `7` | `PacketTogglePassive` | C $\to$ S | `ResourceLocation skillId` | Conmuta estado en `activeToggles` y valida suficiencia de reserva de maná. |
| `8` | `PacketSaveCraftedSpell` | C $\to$ S | `int slotIndex`, `CompoundTag spellTag` | Deserializa la estructura de magia modular en la ranura $[0, 3]$ del jugador. |

---

## 8. CATÁLOGO COMPLETO DE NODOS DE HABILIDAD

### 8.1 Rama Cuerpo a Cuerpo (`modrpg:melee`)
* `melee_step_boost_3` (`PASSIVE_STAT`): Requiere CaC 3. $+0.5\text{ m}$ de altura de paso física.
* `melee_double_attack` (`ACTIVE_ABILITY`): Requiere CaC 4. Si la barra de ataque está al $\ge 92\%$, asesta un segundo golpe al $80\%$ de daño evadiendo la invulnerabilidad estándar.
* `melee_unarmed_style` (`PASSIVE_STAT`): Requiere CaC 4, `double_attack`. Puños limpios $+10\%$ de daño; armas equipadas $-5\%$.
* `melee_weapon_mastery` (`PASSIVE_STAT`): Requiere CaC 8, `double_attack`. Armas $+15\%$ de daño; $15\%$ probabilidad de degradación de durabilidad doble.
* `melee_leg_trip` (`ACTIVE_ABILITY`, CD: $8\text{ s}$): Requiere CaC 6, `unarmed_style`. Barrido frontal de $3.5\text{ m}$. Aplica Lentitud IV por $3\text{ s}$ y suprime inercia.
* `melee_wide_sweep` (`ACTIVE_ABILITY`, CD: $5\text{ s}$): Requiere CaC 12, `weapon_mastery`. Tajo horizontal de $180^\circ$ en $4\text{ m}$ por $1.25\times$ daño base.
* `melee_ether_dual_sword` (`ACTIVE_ABILITY`): Requiere CaC 5, Magia 6, `double_attack`. Corte mágico con mano secundaria por el $75\%$ del daño base.
* `melee_berserker_stance` (`PASSIVE_TOGGLE`): Requiere CaC 8, `double_attack`. Drena $4.5$ de maná/s. Daño infligido $+30\%$; daño recibido $+15\%$.
* `melee_heavy_tornado` (`ACTIVE_ABILITY`, CD: $8\text{ s}`): Requiere CaC 10, $20$ bajas CaC, `double_attack`. Giro de $5.5\text{ m}$ con elevación forzada vertical ($Y = +0.45$) y $+50\%$ de daño.
* `melee_vital_cleave` (`ACTIVE_ABILITY`, CD: $10 - 20\text{ s}$, Coste: $15$ Maná): Requiere CaC 15, `heavy_tornado`. Inflige el $10\%$ de la vida actual del objetivo.
* `melee_megacut` (`ACTIVE_ABILITY`, CD: $20\text{ s}`): Requiere CaC 50, $100$ bajas CaC, `heavy_tornado`. Onda penetrante de $12\text{ m}$ por $3.0\times$ daño.
* `melee_ultracut` (`ULTIMATE`, CD: $10\text{ min}$ tras impacto): Requiere CaC 100, $250$ bajas CaC, `megacut`. Impacto único de $+500\%$ de daño crítico con daño colateral del $50\%$ en radio de $6\text{ m}$.

### 8.2 Rama Arquería (`modrpg:ranged`)
* `ranged_tailwind` (`PASSIVE_STAT`): Requiere Arquería 5. Velocidad balística $+80\%$.
* `ranged_rapid_fire` (`ACTIVE_ABILITY`, CD: $15\text{ s}`): Requiere Arquería 12, `tailwind`. Ráfaga instantánea de 6 flechas a velocidad terminal sin tensar arco.
* `ranged_homing_arrow` (`ACTIVE_ABILITY`, CD: $10\text{ s}`): Requiere Arquería 16, `tailwind`. Proyectil guiado en radio de $25\text{ m}$. Penalización de $+10\text{ s}$ en caso de fallo (Whiff).
* `ranged_crossbow_artillery` (`ACTIVE_ABILITY`): Requiere Arquería 20, $30$ bajas proyectil, `tailwind`. Cohetes de ballesta infligen detonación sónica de $+15.0 + (L_{\text{ranged}} \times 0.5)$ de daño.
* `ranged_hypersonic` (`ACTIVE_ABILITY`): Requiere Arquería 50, `tailwind`. Disparos agachado adquieren velocidad $\times 1.5$, remueven gravedad y perforan 5 entidades.

### 8.3 Sinergias Híbridas (`modrpg:hybrid`)
* `hybrid_hunter` (`HYBRID_SYNERGY`): Requiere CaC 25, Arquería 25. Flechas marcan objetivos (`Glowing`); impactos cuerpo a cuerpo detonan la marca infligiendo $+150\%$ de daño de vacío.
* `hybrid_arrow_propulsion` (`HYBRID_SYNERGY`, CD: $3\text{ s}`): Requiere CaC 35, Arquería 30. Disparar a los pies ($\text{pitch} > 55^\circ$) genera vector vertical $Y = 1.35$ y caída lenta por $4\text{ s}$.
* `hybrid_sword_quiver` (`HYBRID_SYNERGY`): Requiere CaC 70, Arquería 50. Suma $+1.5\times$ de `ATTACK_DAMAGE` al impacto de cualquier proyectil balístico.
* `hybrid_combined_ultimate` (`ULTIMATE`): Requiere CaC 100, Arquería 70. Desatar el Ultracorte provoca una lluvia orbital de proyectiles mágicos en $7\text{ m}$ por el $40\%$ del daño base.

### 8.4 Rama Magia (`modrpg:magic`)
* `magic_fireball` (`ACTIVE_ABILITY`, CD: $5\text{ s}$, Coste: $20$ Maná): Requiere Magia 3. Raycast penetrante de $10\text{ m}$ por $8.0 + (L_{\text{magic}} \times 0.4)$ de daño de fuego.
* `magic_healing_aura` (`ACTIVE_ABILITY`, CD: $12\text{ s}$, Coste: $35$ Maná): Requiere Magia 6, `fireball`. Restaura $6.0 + (L_{\text{magic}} \times 0.2)\text{ HP}$, limpia debilidades y otorga Regeneración II por $5\text{ s}$.
* `magic_necrotic_drain` (`PASSIVE_STAT`): Requiere Magia 15, `fireball`. Transfiere el $15\%$ del daño mágico causado en forma de curación directa.
* `magic_earth_tune` (`ACTIVE_ABILITY`, CD: $10\text{ s}`): Requiere Magia 8, `fireball`. Sobre tierra, roca o arena, consume saturación para restaurar $40.0$ de maná.
* `magic_counter_attack` (`ACTIVE_ABILITY`, CD: $12\text{ s}$, Coste: $25$ Maná): Requiere Magia 12, `fireball`. Guardia de $1.5\text{ s}$. Anula el daño entrante y contraataca por $18.0$ a un máximo de 2 agresores.
* `magic_lightning_chain` (`ACTIVE_ABILITY`, CD: $7\text{ s}$, Coste: $30$ Maná): Requiere Magia 20, `fireball`. Relámpago que salta hasta 3 entidades en $8\text{ m}$ por $10.0 + (L_{\text{magic}} \times 0.3)$ de daño.
* `magic_summon_zombies` (`ACTIVE_ABILITY`, CD: $20\text{ s}$, Coste: $45$ Maná): Requiere Magia 10, `fireball`. Invoca 5 zombis infantes con casco durante $20\text{ s}$.
* `magic_summon_skeletons` (`ACTIVE_ABILITY`, CD: $25\text{ s}$, Coste: $50$ Maná): Requiere Magia 15, `summon_zombies`. Invoca 2 esqueletos arqueros con casco por $25\text{ s}$.
* `magic_bee_swarm` (`ACTIVE_ABILITY`, CD: $12\text{ s}$, Coste: $35$ Maná): Requiere Magia 12, `fireball`. Enjambre de 4 abejas hostiles contra el objetivo apuntado por $8\text{ s}$.
* `magic_summon_wolves` (`ACTIVE_ABILITY`, CD: $18\text{ s}$, Coste: $40$ Maná): Requiere Magia 16, `bee_swarm`. Invoca 3 lobos domésticos leales por $15\text{ s}$.
* `custom_spell_1` a `custom_spell_4` (`ACTIVE_ABILITY`): Ranuras ejecutables vinculadas a la memoria de hechizos modulares.

### 8.5 Rama Movilidad (`modrpg:mobility`)
* `mobility_light_step` (`PASSIVE_STAT`): Requiere Movilidad 2. $+0.5\text{ m}$ de altura de paso física.
* `mobility_dash` (`ACTIVE_ABILITY`, CD: $3\text{ s}`): Requiere Movilidad 10, `light_step`. Vector horizontal de factor $1.5$ con $15\text{ ticks}$ ($0.75\text{ s}$) de invulnerabilidad absoluta.
* `mobility_air_jump` (`ACTIVE_ABILITY`, CD: $4\text{ s}`): Requiere Movilidad 20, `light_step`. Impulso vertical $Y = 0.95$ con supresión total del daño de caída.
* `mobility_flurry_of_strikes` (`ACTIVE_ABILITY`, CD: $12\text{ s}`): Requiere Movilidad 10, `dash`. Salto frontal cinético y Velocidad III por $10\text{ s}$.
* `mobility_impact_jump` (`ACTIVE_ABILITY`, CD: $15\text{ s}`): Requiere Movilidad 15, `air_jump`. Elevación $Y = 1.65$. Al colisionar contra el suelo, detona onda de $150.0$ de daño en $5.5\text{ m}$ con retroceso de $1.8$.
* `mobility_step_boost_20` (`PASSIVE_STAT`): Requiere Movilidad 20, `light_step`. $+0.5\text{ m}$ de altura de paso adicional.

### 8.6 Rama Defensa (`modrpg:defense`)
* `defense_stone_skin` (`PASSIVE_STAT`): Requiere Defensa 3. Mitigación pasiva plana del $20\%$ a todo daño entrante.
* `defense_push_and_wear` (`PASSIVE_STAT`): Requiere Defensa 8, `stone_skin`. Empuje masivo de factor $1.5$ y erosiona $5$ puntos de durabilidad al arma sostenida por la víctima.
* `defense_iron_strength` (`ACTIVE_ABILITY`, CD: $30\text{ s}`): Requiere Defensa 14, `stone_skin`. Anula todo daño entrante por $12\text{ s}$; cada golpe interceptado regenera $+2.5\text{ HP}$.
* `defense_iron_fortress` (`ACTIVE_ABILITY`, CD: $30\text{ s}`): Requiere Defensa 20, `iron_strength`. Otorga Resistencia III ($60\%$ mitigación vanilla neta) y Absorción II por $10\text{ s}$.

---

## 9. CONTROLES E INTERFACES DE USUARIO (HUD)

### 9.1 Asignación de Teclado
* `[K]`: Visualizador del grafo de habilidades (`SkillTreeScreen`).
* `[Z]`: Apertura del menú radial contextual (`RadialMenuScreen`).
* `[O]`: Interfaz de ensamblaje de magia modular (`SpellCraftingScreen`).
* `[R]`: Ejecución instantánea de la habilidad apuntada por `selectedSkill`.
* Atajos Directos Opcionales: `[V]` (Tornado), `[B]` (Megacorte), `[G]` (Dash), `[X]` (Fireball), `[C]` (Cura).

### 9.2 Capas de Renderizado en Pantalla (Overlays)
* `ManaOverlay`: Sobre la barra de hambre. Renderiza reserva numérica de maná, tasa de drenaje neta de posturas activas, casillero de habilidad rápida `[R]` y badges de estados activos.
* `SkillCooldownOverlay`: Lateral inferior izquierdo. Proyecta iconos sombreados de habilidades en recarga con conteo regresivo en segundos.
* `ChampionOverlay`: Barra de jefe contextual en el sector superior activada al enfocar monstruos élite o de escuadrón:
  * **Caster / Élite común:** Marco púrpura o dorado con puntos numéricos de vida y badge de cebado elemental.
  * **Capitán Némesis:** Marco doble carmesí sangriento (`0xFFFF2222`), prefijo `§4§l[NÉMESIS]`, barra de vida roja oscura y badge de contramedida adaptada (`§c[⚔ Contra: Acróbata Aéreo]`, `§c[⚔ Contra: Portador de Escudo]`, etc.).
  * **Indicador de Aturdimiento:** Si la postura está rota, el marco parpadea en blanco/dorado con la alerta `§e§l⚡ ¡POSTURA ROTA! (+30% Daño) ⚡`.

---

## 10. INTERFAZ DE LÍNEA DE COMANDOS (`/rpg`)

| Comando | Nivel de Permiso | Descripción Técnica |
| :--- | :---: | :--- |
| `/rpg stats` | $0$ (Público) | Vuelca en la consola del jugador: especializaciones selladas, niveles por rama, contadores de práctica, reserva de maná, **Medidor de Estrés $S(t)$ con badge de fase**, **Vulnerabilidades tácticas activas**, **Estilo de Combate dominante perfilado** y el **Censo de Capitanes Némesis en el mundo**. |
| `/rpg respec` | $0$ (Público) | Revoca votos de Rama Principal y Secundaria; trunca de forma determinista cualquier rama con nivel superior a 20 de regreso al tope base de 20. |
| `/rpg upgrade <branch>` | $0$ (Público) | Ejecuta la transacción de compra del siguiente nivel de la rama especificada, deduciendo niveles de XP y validando hitos de ascensión. |
| `/rpg addlevel <branch> <amount>` | $2$ (Operador) | Inyecta niveles arbitrarios ($1-100$) forzando el recálculo inmediato de modificadores de atributos y sincronización de red. |
| `/rpg unlock <skill_id>` | $2$ (Operador) | Fuerza la adquisición del nodo de habilidad especificado, omitiendo validaciones de prerrequisitos, ramas o acumuladores de práctica. |
```