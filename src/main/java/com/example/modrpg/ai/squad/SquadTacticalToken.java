package com.example.modrpg.ai.squad;

import net.minecraft.world.entity.Mob;

import java.util.Objects;
import java.util.UUID;

/**
 * Representa un permiso de acción táctica dentro del escuadrón.
 * Implementa el protocolo "Lease Heartbeat" para evitar softlocks:
 * Si el portador muere, se aturde o pierde capacidad operativa, el token se libera al instante.
 */
public class SquadTacticalToken {

    public enum TokenType {
        PEEL(100, 60),            // Prioridad máxima: rescate de aliados acorralados (3s max lease)
        PRIMARY_ATTACK(50, 50),   // Hechizos de área o ataques pesados (2.5s max lease)
        SUPPRESSION(10, 80);      // Ráfagas rápidas de hostigamiento y cobertura (4s max lease)

        private final int priority;
        private final int maxLeaseTicks;

        TokenType(int priority, int maxLeaseTicks) {
            this.priority = priority;
            this.maxLeaseTicks = maxLeaseTicks;
        }

        public int getPriority() { return priority; }
        public int getMaxLeaseTicks() { return maxLeaseTicks; }
    }

    public enum TokenState {
        IDLE,
        ACTIVE
    }

    private final TokenType type;
    private UUID holderUUID = null;
    private int remainingTicks = 0;
    private TokenState state = TokenState.IDLE;

    public SquadTacticalToken(TokenType type) {
        this.type = type;
    }

    public TokenType getType() { return type; }
    public UUID getHolderUUID() { return holderUUID; }
    public TokenState getState() { return state; }
    public boolean isAvailable() { return state == TokenState.IDLE; }

    /**
     * Intenta reclamar o renovar el token para un mob específico.
     */
    public synchronized boolean claim(Mob mob, int requestedTicks) {
        if (mob == null || !mob.isAlive()) return false;

        UUID candidateUUID = mob.getUUID();
        if (isAvailable() || Objects.equals(this.holderUUID, candidateUUID)) {
            this.holderUUID = candidateUUID;
            this.remainingTicks = Math.min(requestedTicks, type.getMaxLeaseTicks());
            this.state = TokenState.ACTIVE;
            return true;
        }
        return false;
    }

    /**
     * Libera voluntariamente el token cuando la acción concluye.
     */
    public synchronized void release() {
        this.holderUUID = null;
        this.remainingTicks = 0;
        this.state = TokenState.IDLE;
    }

    /**
     * Heartbeat ejecutado en cada tick del escuadrón.
     * Si la entidad que posee el token está aturdida, muerta o inválida, se revoca inmediatamente.
     */
    public synchronized void tick(Mob holderMob) {
        if (state != TokenState.ACTIVE) return;

        // Comprobación de integridad del portador (Drop-on-Disruption)
        if (holderMob == null || !holderMob.isAlive() || holderMob.getTags().contains("modrpg_staggered")) {
            release();
            return;
        }

        remainingTicks--;
        if (remainingTicks <= 0) {
            release();
        }
    }
}