Documentación Técnica: ModRpg (Minecraft 1.20.1 - Forge)
1. Especificaciones del Entorno

    Plataforma: Minecraft Java Edition

    Versión de Minecraft: 1.20.1

    Cargador de Mods: Minecraft Forge (v47.4.10+)

    Toolchain / JDK: Java 17 LTS

    Identificador de Mod (modid): modrpg

    Mapeos: Official Mojang Mappings

2. Arquitectura del Sistema

El mod implementa una arquitectura orientada a datos y desacoplada mediante eventos, dividida en cinco subsistemas principales:
code Code

┌────────────────────────────────────────────────────────┐
│                   Cliente (GUI / Input)                │
│   SkillTreeScreen | RadialMenuScreen | CooldownOverlay │
└───────────────────────────┬────────────────────────────┘
                            │ Packets (Forge SimpleChannel)
┌───────────────────────────▼────────────────────────────┐
│                    Capa de Red                         │
│   PacketCastSkill | PacketSyncSkills | PacketUnlockNode│
└───────────────────────────┬────────────────────────────┘
                            │ Invocación
┌───────────────────────────▼────────────────────────────┐
│              Dominio Lógico (Servidor)                 │
│   PlayerSkills (Capability) ───► SkillRegistry         │
│   SkillEconomy ◄───────────────► SkillNode             │
└───────────────────────────┬────────────────────────────┘
                            │ Modificadores
┌───────────────────────────▼────────────────────────────┐
│                   Event Pipeline                       │
│   ModEvents (LivingHurt, LivingDeath, EntityJoinLevel) │
└────────────────────────────────────────────────────────┘

3. Modelo de Datos y Persistencia
3.1. Capability: PlayerSkills

La persistencia de estado por jugador se implementa mediante net.minecraftforge.common.capabilities.Capability vinculada a entidades de tipo Player.
Estructuras de Datos Internas:

    branchLevels (Map<ResourceLocation, Integer>): Nivel acumulado por rama de habilidad. Clave: ID de rama. Valor: Rango

            
    [0,100]
    [0,100]

          

    .

    unlockedNodes (Set<ResourceLocation>): Conjunto de nodos de habilidad adquiridos.

    practiceCounters (Map<ResourceLocation, Integer>): Acumuladores de métricas de práctica (ej. bajas con armas específicas).

    cooldowns (Map<ResourceLocation, Integer>): Tiempos de recarga restantes medidos en ticks (

            
    1 s=20 ticks
    1 s=20 ticks

          

    ).

    ultimateCharged (boolean): Bandera transitoria de estado para consumición en el siguiente impacto físico.

Serialización NBT (PlayerSkillsProvider):

    BranchLevels (TAG_Compound): Pares clave-valor String (ID)

            
    →
    →

          

    int (Nivel).

    UnlockedNodes (TAG_List de TAG_String): Identificadores serializados de nodos adquiridos.

    PracticeCounters (TAG_Compound): Pares clave-valor String (ID)

            
    →
    →

          

    int (Bajas).

    Cooldowns (TAG_Compound): Pares clave-valor String (ID)

            
    →
    →

          

    int (Ticks restantes).

    UltimateCharged (TAG_Byte): Booleano de estado.

El ciclo de vida de la entidad gestiona la pérdida de contexto mediante PlayerEvent.Clone, transfiriendo el NBT completo con el método copyFrom() antes de invalidar las capacidades de la entidad original.
4. Estructura de Habilidades (skills.data)
4.1. Clase Base: SkillNode

Unidad fundamental polimórfica que encapsula la lógica de ejecución y los criterios de adquisición.

    Tipos de Nodo (NodeType):

        PASSIVE_STAT: Modificador pasivo permanente de atributos o comportamiento.

        ACTIVE_ABILITY: Habilidad accionada por interacción del usuario sujeta a enfriamiento.

        HYBRID_SYNERGY: Nodo de convergencia que combina requerimientos de múltiples ramas.

        ULTIMATE: Habilidad terminal con alto impacto y cooldown extendido.

    Puntos de Inserción (Hooks de Ejecución):

        canUnlock(ServerPlayer, PlayerSkills): Evaluación booleana de requisitos.

        tryUnlock(ServerPlayer, PlayerSkills): Ejecución de costes y mutación de estado.

        onExecuteActive(ServerPlayer, PlayerSkills): Callback invocado por activación explícita.

        onLivingHurt(ServerPlayer, LivingHurtEvent, PlayerSkills): Callback invocado en el canal de cálculo de daño.

        onArrowShoot(ServerPlayer, EntityJoinLevelEvent, AbstractArrow, PlayerSkills): Callback invocado al instanciarse proyectiles asociados al jugador.

4.2. Motor de Requisitos: SkillRequirement

Interfaz funcional encargada de validar y mutar precondiciones de desbloqueo:

    minPlayerXpLevel(int): Valida player.experienceLevel >= minXp.

    consumePlayerXpLevels(int): Valida y descuenta niveles mediante player.giveExperienceLevels(-xpCost).

    branchLevel(ResourceLocation, int): Valida el nivel de una rama contra un umbral.

    practice(ResourceLocation, int, String): Valida el acumulador de bajas específico.

    prerequisiteNode(ResourceLocation, String): Verifica la presencia del nodo padre en unlockedNodes.

5. Economía y Curvas de Escalado
5.1. Coste de Niveles de Experiencia

El coste en niveles de XP para ascender de nivel en cualquier rama se rige por la función:

        
C(L)=max⁡(1,round(1.0+(L99)1.6×99.0))
C(L)=max(1,round(1.0+(99L​)1.6×99.0))

      

Donde

        
L∈[0,99]
L∈[0,99]

      

representa el nivel actual de la rama.
5.2. Requisitos de Práctica

El umbral de bajas requeridas para el nivel

        
L+1
L+1

      

se define linealmente:

        
K(L+1)=(L+1)×3
K(L+1)=(L+1)×3

      

5.3. Escalado de Atributos Físicos (SkillAttributes)

    Daño Cuerpo a Cuerpo (Attributes.ATTACK_DAMAGE):
    Modificador transitivo aditivo (AttributeModifier.Operation.ADDITION):

            
    Dbonus(Lmelee)=baseAttack×(Lmelee×0.02)
    Dbonus​(Lmelee​)=baseAttack×(Lmelee​×0.02)

          


    Garantiza un incremento estricto del

            
    +2%
    +2%

          

    del daño base por nivel (

            
    +200%
    +200%

          

    a nivel 100).

    Velocidad de Movimiento (Attributes.MOVEMENT_SPEED):

            
    Vbonus(Lmobility)=(Lmobility100)1.5×0.08
    Vbonus​(Lmobility​)=(100Lmobility​​)1.5×0.08

          

6. Registro de Nodos del Sistema (SkillRegistry)
Identificador del Nodo	Rama	Tipo	Requisitos de Adquisición	Cooldown	Comportamiento / Efecto
modrpg:melee_double_attack	melee	ACTIVE_ABILITY	Rama Melee

        
≥4
≥4

      

	0 ticks	Si la barra de ataque

        
≥92%
≥92%

      

, genera un impacto secundario recursivo por el

        
80%
80%

      

del daño original.
modrpg:melee_ether_dual_sword	melee	ACTIVE_ABILITY	Melee

        
≥5
≥5

      

, Magic

        
≥6
≥6

      

	0 ticks	Los ataques con espada generan un segundo corte mágico en offhand por el

        
75%
75%

      

del daño base (DamageSource.magic()).
modrpg:melee_heavy_tornado	melee	ACTIVE_ABILITY	Melee

        
≥10
≥10

      

, Bajas Melee

        
≥20
≥20

      

	160 ticks (8s)	Barrido radial de

        
5.5 m
5.5 m

      

. Aplica

        
(1.5×base)×(1+0.02⋅Lmelee)
(1.5×base)×(1+0.02⋅Lmelee​)

      

de daño físico con vector de empuje y elevación

        
+0.45 Y
+0.45 Y

      

.
modrpg:melee_megacut	melee	ACTIVE_ABILITY	Melee

        
≥50
≥50

      

, Bajas Melee

        
≥100
≥100

      

	400 ticks (20s)	Raycast frontal perforante de

        
12 m
12 m

      

. Inflige

        
3.0×dan˜o base
3.0×dan˜o base

      

a todas las entidades interceptadas.
modrpg:melee_ultracut	melee	ULTIMATE	Melee

        
≥100
≥100

      

, Bajas Melee

        
≥250
≥250

      

	12000 ticks (10m)	Carga el siguiente ataque físico para infligir

        
+500%
+500%

      

de daño crítico con detonación en área de

        
6.0 m
6.0 m

      

de radio.
modrpg:ranged_tailwind	ranged	PASSIVE_STAT	Rama Ranged

        
≥5
≥5

      

	0 ticks	Aplica escala vectorial

        
×1.8
×1.8

      

a la velocidad inicial de proyectiles AbstractArrow.
modrpg:ranged_crossbow_artillery	ranged	ACTIVE_ABILITY	Ranged

        
≥20
≥20

      

, Bajas Ranged

        
≥30
≥30

      

	0 ticks	Cohetes disparados con ballesta reciben

        
+15.0+(Lranged×0.5)
+15.0+(Lranged​×0.5)

      

de daño plano y partículas sónicas.
modrpg:ranged_hypersonic	ranged	ACTIVE_ABILITY	Rama Ranged

        
≥50
≥50

      

	0 ticks	Al disparar agachado: flecha sin gravedad, velocidad

        
×1.5
×1.5

      

y perforación de 5 objetivos.
modrpg:hybrid_hunter	melee	HYBRID_SYNERGY	Melee

        
≥25
≥25

      

, Ranged

        
≥25
≥25

      

	0 ticks	Flechas aplican etiqueta modrpg_hunter_mark. El impacto melee subsiguiente consume la marca y multiplica el daño por

        
×2.5
×2.5

      

.
modrpg:hybrid_arrow_propulsion	melee	HYBRID_SYNERGY	Melee

        
≥35
≥35

      

, Ranged

        
≥30
≥30

      

	60 ticks (3s)	Disparar flechas con pitch

        
>55∘
>55∘

      

propulsa al jugador (vector

        
Y=+1.35
Y=+1.35

      

) y aplica caída lenta.
modrpg:hybrid_sword_quiver	melee	HYBRID_SYNERGY	Melee

        
≥70
≥70

      

, Ranged

        
≥50
≥50

      

	0 ticks	Proyectiles a distancia suman al impacto un bonus de daño plano equivalente a

        
1.5×dan˜o CaC
1.5×dan˜o CaC

      

.
modrpg:hybrid_combined_ultimate	melee	ULTIMATE	Melee

        
≥100
≥100

      

, Ranged

        
≥70
≥70

      

	0 ticks	La ejecución del Ultracorte invoca un bombardeo secundario en área (

        
7 m
7 m

      

) que inflige el

        
40%
40%

      

del daño en forma de magia.
modrpg:magic_fireball	magic	ACTIVE_ABILITY	Rama Magic

        
≥3
≥3

      

	100 ticks (5s)	Emite un cono frontal de

        
10 m
10 m

      

. Aplica

        
8.0+(Lmagic×0.4)
8.0+(Lmagic​×0.4)

      

de daño mágico e ignición.
modrpg:magic_healing_aura	magic	ACTIVE_ABILITY	Rama Magic

        
≥6
≥6

      

	240 ticks (12s)	Regenera

        
6.0+(Lmagic×0.2)
6.0+(Lmagic​×0.2)

      

de vida base, remueve efectos negativos y aplica Regeneración II.
modrpg:magic_necrotic_drain	magic	PASSIVE_STAT	Rama Magic

        
≥15
≥15

      

	0 ticks	Restaura salud al jugador equivalente al

        
15%
15%

      

del daño infligido a cualquier entidad viva.
modrpg:mobility_light_step	mobility	PASSIVE_STAT	Rama Mobility

        
≥2
≥2

      

	0 ticks	Sobrescribe dinámicamente player.maxUpStep = 1.25F, permitiendo subir bloques completos sin saltar.
modrpg:mobility_dash	mobility	ACTIVE_ABILITY	Rama Mobility

        
≥10
≥10

      

	60 ticks (3s)	Aplica impulso vectorial horizontal

        
×1.6
×1.6

      

y 20 ticks de invulnerabilidad estricta (invulnerableTime = 20).
modrpg:mobility_air_jump	mobility	ACTIVE_ABILITY	Rama Mobility

        
≥20
≥20

      

	80 ticks (4s)	Aplica aceleración vertical pura (

        
Y=+0.95
Y=+0.95

      

) en el aire.
modrpg:defense_stone_skin	defense	PASSIVE_STAT	Rama Defense

        
≥3
≥3

      

	0 ticks	Intercepta daño recibido y reduce el valor final un

        
20%
20%

      

(amount * 0.80F).
modrpg:defense_iron_fortress	defense	ACTIVE_ABILITY	Rama Defense

        
≥20
≥20

      

	600 ticks (30s)	Aplica Resistencia III y Absorción II durante 200 ticks (10s).
7. Protocolo de Red (modrpg:main)

Canal bidireccional registrado mediante NetworkRegistry.newSimpleChannel bajo la versión de protocolo "3".
7.1. Tabla de Paquetes
ID	Clase	Dirección	Carga Útil (Payload)	Lógica de Procesamiento
0	PacketCastSkill	C

        
→
→

      

S	ResourceLocation (ID)	Valida que el nodo exista, esté desbloqueado y sin cooldown activo. Aplica enfriamiento e invoca node.onExecuteActive().
1	PacketSyncSkillsToClient	S

        
→
→

      

C	Maps serializados: branchLevels, unlockedNodes, practiceCounters, cooldowns, boolean ultimateCharged	Sobrescribe las estructuras de datos de la capability en el hilo del cliente (Minecraft.getInstance().player).
2	PacketUpgradeSkill	C

        
→
→

      

S	String (nombre de rama)	Invoca SkillEconomy.upgradeBranch() en el contexto del servidor.
3	PacketUnlockNode	C

        
→
→

      

S	ResourceLocation (ID)	Evalúa node.tryUnlock(). Descuenta costes y sincroniza con el cliente si la transacción es válida.
8. Capa de Presentación e Interfaz de Usuario
8.1. Lienzo Navegable (SkillTreeScreen)

    Mapeo de Coordenadas: Transformación afín 2D basada en desplazamiento acumulado:

            
    Xrender=Xpantalla/2+scrollX+Xnodo
    Xrender​=Xpantalla​/2+scrollX+Xnodo​

          


            
    Yrender=Ypantalla/2+scrollY+Ynodo
    Yrender​=Ypantalla​/2+scrollY+Ynodo​

          

    Grafo de Nodos: Algoritmo diferencial de línea entera (Bresenham) implementado en drawLine() para conectar parentId

            
    →
    →

          

    id.

    Criterio Cromático de Aristas:

        Desbloqueado: 0xFFDAA520 (Dorado)

        Bloqueado: 0xFF444455 (Gris apagado)

8.2. Menú de Selección Radial (RadialMenuScreen)

Distribución geométrica polar equidistante centrada en ventana:

        
θi=(2πN)⋅i−π2
θi​=(N2π​)⋅i−2π​

      

        
Xi=Xcentro+cos⁡(θi)⋅R,Yi=Ycentro+sin⁡(θi)⋅R
Xi​=Xcentro​+cos(θi​)⋅R,Yi​=Ycentro​+sin(θi​)⋅R

      

Donde

        
R=75 px
R=75 px

      

y

        
N
N

      

es el recuento total de habilidades activas desbloqueadas.
8.3. Capa de Superposición (SkillCooldownOverlay)

Hook registrado en el pipeline de renderizado de Forge (RegisterGuiOverlaysEvent) posicionado de forma relativa sobre VanillaGuiOverlay.HOTBAR. Itera las entradas de cooldowns mayores a cero y dibuja la máscara oscurecida junto al remanente en segundos (

        
t/20
t/20

      

).
9. Interfaz de Comandos (RpgCommands)

Todos los subcomandos están anidados bajo el comando raíz /rpg.

    /rpg stats:

        Permiso requerido: Nivel 0 (Todos los jugadores).

        Salida: Imprime en el log de chat del emisor los niveles de cada rama, contadores de bajas y la lista completa de habilidades desbloqueadas.

    /rpg upgrade <branch>:

        Permiso requerido: Nivel 0.

        Argumentos: <branch> (String, autocompletado con ramas registradas).

        Acción: Evalúa y transacciona el ascenso de nivel en modo supervivencia.

    /rpg addlevel <branch> <amount>:

        Permiso requerido: Nivel 2 (Operadores / Modo creativo).

        Argumentos: <branch> (String), <amount> (Integer, rango

                
        [1,100]
        [1,100]

              

        ).

        Acción: Incrementa artificialmente el nivel sin descontar experiencia ni exigir bajas.

    /rpg unlock <skill>:

        Permiso requerido: Nivel 2.

        Argumentos: <skill> (String, autocompletado con nodos de SkillRegistry).

        Acción: Otorga el nodo al jugador eludiendo todos los prerrequisitos del árbol.