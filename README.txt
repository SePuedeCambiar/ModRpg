- **Persistencia (`PlayerSkillsProvider`):** Adjunta la `Capability<PlayerSkills>` a cada `Player`. Guarda niveles de rama, nodos aprendidos, práctica, cooldowns, loadout, maná y estados transitorios en formato NBT estructurado.
- **Clonación tras Muerte (`PlayerEvent.Clone`):** El estado se transfiere limpiamente sin pérdida de progresión cuando el jugador reaparece.

---

## 🎮 Controles y Atajos de Teclado

| Tecla | Función | Descripción |
| :---: | :--- | :--- |
| **`K`** | **Abrir Árbol de Habilidades** | Abre la pantalla interactiva de progresión con navegación 2D. |
| **`Z`** | **Rueda Radial de Habilidades** | Abre la rueda de acceso rápido para castear habilidades equipadas. |
| **`G`** | **Embestida Evasiva (Dash)** | Impulso horizontal con 15 ticks (0.75s) de inmunidad (*i-frames*). |
| **`R`** | **Ultracorte Final** | Carga la habilidad definitiva CaC (+500% de daño y detonación). |
| **`V`** | **Torbellino Ultrapesado** | Ataque giratorio masivo de 5.5m con elevación vertical. |
| **`B`** | **Megacorte** | Onda cortante de 12 bloques que atraviesa enemigos. |
| **`X`** | **Piroclasto Elemental** | Ráfaga cónica de fuego arcano frontal. |
| **`C`** | **Aura de Sanación** | Cura vida, limpia efectos negativos y otorga Regeneración. |

*Nota: Los controles pueden reasignarse en el menú de Configuración de Controles de Minecraft (Categoría `ModRpg`).*

---

## 📐 Economía, Curvas y Fórmulas Matemáticas

### 1. Coste de Niveles de Experiencia (Vanilla XP)
El coste en niveles de XP para subir una rama desde su nivel actual $L \in [0, 99]$ hasta $L+1$ se calcula con una curva polinómica exponencial suave al inicio y exigente al final:

$$C(L) = \max\left(1, \text{round}\left(1.0 + \left(\frac{L}{99}\right)^{1.6} \times 99.0\right)\right)$$

- **Nivel 0:** Cuesta **1 nivel** de XP.
- **Nivel 10:** Cuesta **3 a 5 niveles** de XP.
- **Nivel 50:** Cuesta **~36 niveles** de XP.
- **Nivel 99 $\to$ 100:** Cuesta **100 niveles** exactos de XP.

### 2. Práctica de Combate Requerida
Para ascender al nivel $L+1$ de cualquier rama, se requiere acumular puntos de práctica universales (bajas, daño mitigado, distancia):

$$P(L+1) = (L+1) \times 3$$

| Siguiente Nivel ($L+1$) | Práctica Requerida |
| :---: | :---: |
| 1 | 3 puntos |
| 10 | 30 puntos |
| 50 | 150 puntos |
| 100 | 300 puntos |

### 3. Escalado de Atributos Físicos
- **Daño Cuerpo a Cuerpo (`Attributes.ATTACK_DAMAGE`):** Escalado lineal estricto de $+2\%$ por nivel sobre el daño base del jugador. A nivel 100 se obtiene $+200\%$ adicional ($3\times$ el daño total).
  $$D_{\text{bonus}}(L_{\text{melee}}) = \text{baseAttack} \times (L_{\text{melee}} \times 0.02)$$
- **Velocidad de Movimiento (`Attributes.MOVEMENT_SPEED`):**
  $$V_{\text{bonus}}(L_{\text{mobility}}) = \left(\frac{L_{\text{mobility}}}{100}\right)^{1.5} \times 0.08$$
- **Mitigación Defensiva Pasiva:** Factor multiplicador aplicado al daño recibido (reduce hasta un 40% a nivel 100):
  $$\text{Factor}(L_{\text{defense}}) = \max\left(0.60, 1.0 - \left(\frac{L_{\text{defense}}}{100}\right)^{1.4} \times 0.40\right)$$
- **Altura de Paso (`ForgeMod.STEP_HEIGHT_ADDITION`):** Acumulación aditiva de $+0.5$ bloques por cada nodo desbloqueado (`LightStep`, `StepBoostMobility20`, `StepBoostMelee3`), permitiendo subir hasta **+1.5 bloques adicionales** de forma continua.

---

## ⚡ Sistema de Maná

- **Capacidad Máxima:** Base de 100 puntos, incrementada en $+2$ por cada nivel en la rama de Magia (hasta **300 de maná** a nivel 100).
  $$\text{MaxMana} = 100.0 + (L_{\text{magic}} \times 2.0)$$
- **Regeneración:** Base de $2.0 \text{ maná/s}$, potenciada en $+5\%$ por cada nivel en Magia:
  $$\text{Regen}(L_{\text{magic}}) = 2.0 \times (1.0 + L_{\text{magic}} \times 0.05)$$
  - *Nivel 0:* $2.0 \text{ maná/s}$
  - *Nivel 10:* $3.0 \text{ maná/s}$ ($+50\%$)
  - *Nivel 100:* $12.0 \text{ maná/s}$ ($+500\%$)

---

## 📖 Catálogo Completo de Habilidades

### 1. Cuerpo a Cuerpo (Melee)
| ID | Tipo | Coste / CD | Requisitos | Efecto |
| :--- | :---: | :---: | :--- | :--- |
| `melee_step_boost_3` | Pasiva | — | Melee Lvl 3 | **Zancada Marcial:** $+0.5$ a la altura de paso. |
| `melee_double_attack` | Activa | 0 ticks | Melee Lvl 4 | **Doble Ataque:** Si la barra de ataque está al $\ge 92\%$, asesta un segundo impacto consecutivo al 80% de daño. |
| `melee_unarmed_style` | Pasiva | — | Melee Lvl 4 | **Estilo Desarmado:** $+10\%$ de daño a puño limpio, $-5\%$ si usas armas. |
| `melee_weapon_mastery` | Pasiva | — | Melee Lvl 8 | **Dominio de Armas:** $+15\%$ daño con armas CaC (15% prob. de desgaste extra). |
| `melee_leg_trip` | Activa | 160t (8s) | Melee Lvl 6 | **Golpe Bajo:** Barre las piernas de los enemigos infligiendo daño y lentitud extrema. |
| `melee_wide_sweep` | Activa | 100t (5s) | Melee Lvl 12 | **Barrido Ciclónico:** Golpe horizontal de 180° que corta a todos los enemigos al frente. |
| `melee_ether_dual_sword`| Activa | 0 ticks | Melee Lvl 5, Magia Lvl 6 | **Doble Espada Espectral:** Impacto mágico adicional con espada offhand (75% daño). |
| `melee_heavy_tornado` | Activa | 160t (8s) | Melee Lvl 10, 20 bajas | **Torbellino Ultrapesado:** Giro de 5.5m que levanta a los enemigos con $+50\%$ de daño. |
| `melee_megacut` | Activa | 400t (20s) | Melee Lvl 50, 100 bajas | **Megacorte:** Onda lineal penetrante de 12m que causa $3\times$ daño base. |
| `melee_ultracut` | Definitiva | 12000t (10m) | Melee Lvl 100, 250 bajas | **Ultracorte Final:** Carga el arma; el próximo golpe inflige $+500\%$ de daño y explosión de 6m. |

### 2. Arquería (Ranged)
| ID | Tipo | Coste / CD | Requisitos | Efecto |
| :--- | :---: | :---: | :--- | :--- |
| `ranged_tailwind` | Pasiva | — | Ranged Lvl 5 | **Viento a Favor:** Proyectiles viajan $+80\%$ más rápido y con mayor precisión. |
| `ranged_rapid_fire` | Activa | 300t (15s) | Ranged Lvl 12 | **Disparo Rápido:** Ráfaga inmediata de 6 flechas a velocidad máxima sin tensar arco. |
| `ranged_homing_arrow` | Activa | 200t (10s) | Ranged Lvl 16 | **Tiro Teledirigido:** Dispara flecha buscadora (penalización $+10$s de CD si no hay objetivos en 25m). |
| `ranged_crossbow_artillery`| Activa | — | Ranged Lvl 20, 30 bajas | **Artillería de Ballesta:** Cohetes causan explosión sónica con aliento de dragón y daño extra. |
| `ranged_hypersonic` | Activa | — | Ranged Lvl 50 | **Tiro Hipersónico:** Al disparar agachado, la flecha viaja sin gravedad y perfora 5 objetivos. |

### 3. Magia y Elementos (Magic)
| ID | Tipo | Coste / CD | Requisitos | Efecto |
| :--- | :---: | :---: | :--- | :--- |
| `magic_fireball` | Activa | 20 Maná / 5s | Magia Lvl 3 | **Piroclasto Elemental:** Ráfaga cónica de fuego arcano perforante de 10m. |
| `magic_healing_aura` | Activa | 35 Maná / 12s | Magia Lvl 6 | **Aura de Sanación:** Restaura salud, limpia veneno/wither/lentitud y otorga Regeneración. |
| `magic_earth_tune` | Activa | 2 Pts Comida / 10s| Magia Lvl 8 | **Sintonía Terrenal:** Sobre tierra/roca/arena, consume hambre y regenera $+40$ maná. |
| `magic_summon_zombies` | Activa | 45 Maná / 20s | Magia Lvl 10 | **Horda de Infantes:** Invoca 5 zombis infantes leales con cascos durante 20s. |
| `magic_counter_attack` | Activa | 25 Maná / 12s | Magia Lvl 12 | **Contraataque:** Postura de 1.5s; al ser golpeado, anula el daño y ataca a 2 rivales. |
| `magic_bee_swarm` | Activa | 35 Maná / 12s | Magia Lvl 12 | **Enjambre Hostil:** Envía 4 abejas furiosas al enemigo en el punto de mira durante 8s. |
| `magic_necrotic_drain` | Pasiva | — | Magia Lvl 15 | **Drenaje Necrótico:** Cura al jugador un $15\%$ del daño infligido a cualquier enemigo. |
| `magic_summon_skeletons`| Activa | 50 Maná / 25s | Magia Lvl 15 | **Arqueros Espectrales:** Invoca 2 esqueletos arqueros con casco durante 25s. |
| `magic_summon_wolves` | Activa | 40 Maná / 18s | Magia Lvl 16 | **Manada Espectral:** Invoca 3 lobos domesticados leales durante 15s. |
| `magic_lightning_chain` | Activa | 30 Maná / 7s | Magia Lvl 20 | **Chispa Encadenada:** Descarga eléctrica que salta entre hasta 3 enemigos cercanos. |

### 4. Movilidad (Mobility)
| ID | Tipo | Coste / CD | Requisitos | Efecto |
| :--- | :---: | :---: | :--- | :--- |
| `mobility_light_step` | Pasiva | — | Movilidad Lvl 2 | **Paso Ligero:** $+0.5$ bloques a la altura de paso. |
| `mobility_dash` | Activa | 60t (3s) | Movilidad Lvl 10 | **Embestida Evasiva:** Impulso horizontal con 15 ticks (0.75s) de inmunidad absoluta. |
| `mobility_flurry_of_strikes`| Activa | 240t (12s) | Movilidad Lvl 10 | **Salto Ronin:** Salto acrobático hacia adelante y otorga Velocidad III durante 10s. |
| `mobility_impact_jump` | Activa | 300t (15s) | Movilidad Lvl 15 | **Ground Slam:** Gran salto vertical; al caer al suelo anula daño de caída y causa **150 daño en área**. |
| `mobility_air_jump` | Activa | 80t (4s) | Movilidad Lvl 20 | **Salto de Viento:** Impulso aéreo vertical con anulación garantizada de daño de caída. |
| `mobility_step_boost_20`| Pasiva | — | Movilidad Lvl 20 | **Mejora de Paso I:** $+0.5$ bloques adicionales a la altura de paso. |

### 5. Defensa (Defense)
| ID | Tipo | Coste / CD | Requisitos | Efecto |
| :--- | :---: | :---: | :--- | :--- |
| `defense_stone_skin` | Pasiva | — | Defensa Lvl 3 | **Piel de Piedra:** Reduce pasivamente todo el daño recibido en un $20\%$. |
| `defense_push_and_wear` | Pasiva | — | Defensa Lvl 8 | **Empuje y Desgaste:** Tus ataques empujan violentamente y desgastan el arma enemiga. |
| `defense_iron_strength` | Activa | 600t (30s) | Defensa Lvl 14 | **Fuerza de Hierro:** Durante 12s, no recibes daño y cada impacto recibido te cura $+2.5$ HP. |
| `defense_iron_fortress` | Activa | 600t (30s) | Defensa Lvl 20 | **Fortaleza Inquebrantable:** Otorga Resistencia III y Absorción II durante 10s. |

### 6. Sinergias Híbridas (Multiclase)
| ID | Tipo | Coste / CD | Requisitos | Efecto |
| :--- | :---: | :---: | :--- | :--- |
| `hybrid_hunter` | Sinergia | — | Melee 25, Ranged 25 | **Cazador Híbrido:** Flechas marcan objetivos; rematarlos a corta distancia causa $+150\%$ daño de vacío. |
| `hybrid_arrow_propulsion`| Sinergia | 60t (3s) | Melee 35, Ranged 30 | **Impulso de Flecha:** Disparar al suelo bajo tus pies (>55° pitch) te propulsa con caída lenta. |
| `hybrid_sword_quiver` | Sinergia | — | Melee 70, Ranged 50 | **Carcaj de Espadas:** Las flechas suman un $+150\%$ de tu daño CaC al impacto. |
| `hybrid_combined_ultimate`| Definitiva | — | Melee 100, Ranged 70 | **Lluvia de Espadas del Vacío:** El Ultracorte Final detona una lluvia de espadas mágicas (40% de daño en 7m). |

---

## 🐺 Sub-sistema de Esbirros e Invocaciones

Gestionado mediante `MinionHelper`:
1. **Identificación NBT y Tags:** A cada entidad se le asignan los tags `modrpg_minion`, `modrpg_owner_<UUID>` y el valor NBT `modrpg_lifespan`.
2. **Ciclo de Vida Automático:** Un evento en `LivingEvent.LivingTickEvent` decrementa el lifespan de los esbirros; al expirar se desvanecen con efectos de partículas sin dejar drops residuales.
3. **Redirección de Objetivos:** Al golpear a una criatura, el jugador reenvía automáticamente la agresión de todos sus esbirros cercanos hacia ese objetivo.
4. **Fuego Amigo Cero:** Cancelación garantizada de daño mutuo entre jugador y esbirros, y entre esbirros que compartan el mismo dueño.

---

## 🖥 Interfaz de Usuario (HUD y GUI)

- **Árbol de Habilidades (`SkillTreeScreen` - `K`):**
  - **Zoom Dinámico:** Rueda del ratón para ampliar o alejar (0.55x a 1.8x).
  - **Pan / Arrastre:** Clic sostenido con botón izquierdo, central o derecho.
  - **Pestañas de Filtrado:** Filtra por ramas (Todas, CaC, Arquería, Magia, Movilidad, Defensa).
  - **Interacciones:** Clic Izquierdo para desbloquear nodos; Clic Derecho para equipar/desequipar habilidades activas en el Loadout.
- **Rueda Radial (`RadialMenuScreen` - `Z`):**
  - Distribución polar automática según el número de habilidades en el loadout.
  - Indicadores visuales en tiempo real: bordes dorados (listo), azules (falta maná), o rojos con contador numérico (en enfriamiento).
- **Overlays del HUD:**
  - **Barra de Maná (`ManaOverlay`):** Ubicada a la derecha, por encima de los muslitos de hambre. Muestra valor numérico y porcentaje animado.
  - **Contadores de Enfriamiento (`SkillCooldownOverlay`):** Iconos semitransparentes sobre la hotbar con indicador de segundos restantes (`Xm Xs` o `Xs`).

---

## 📡 Protocolo de Red (Networking)

Canal `modrpg:main` registrado en Forge con SimpleChannel (Versión `5`):

| Packet | Flujo | Descripción |
| :--- | :---: | :--- |
| `PacketCastSkill` | C $\to$ S | Solicita la ejecución de una habilidad activa/definitiva validando cooldown y maná en servidor. |
| `PacketSyncSkillsToClient` | S $\to$ C | Sincroniza ramas, nodos, contadores de práctica, cooldowns, maná y loadout. |
| `PacketUpgradeSkill` | C $\to$ S | Envía la petición de compra y aumento de nivel en una rama específica. |
| `PacketUnlockNode` | C $\to$ S | Transacciona el desbloqueo de un nodo específico del árbol en el servidor. |
| `PacketSyncMana` | S $\to$ C | Paquete ligero de actualización de maná actual y máximo hacia el cliente. |
| `PacketEquipSkill` | C $\to$ S | Solicita equipar o desequipar un nodo en el loadout radial del jugador. |

---

## ⌨️ Comandos del Servidor

Todos los comandos se estructuran bajo el espacio de nombres `/rpg`:

```bash
# Consulta tus niveles de rama, estadísticas de práctica y habilidades aprendidas
/rpg stats

# Sube de nivel una rama consumiendo tu experiencia y práctica acumulada
/rpg upgrade <melee|ranged|mobility|magic|defense>

# [ADMIN] Otorga niveles directos a una rama sin consumir XP ni práctica (Permiso nivel 2)
/rpg addlevel <rama> <cantidad>
# Ejemplo: /rpg addlevel magic 10

# [ADMIN] Desbloquea instantáneamente cualquier nodo del mod para pruebas (Permiso nivel 2)
/rpg unlock <skill_id>
# Ejemplo: /rpg unlock melee_ultracut