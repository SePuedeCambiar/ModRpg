package com.example.modrpg.ai.squad;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.UUID;

/**
 * Representa una posición táctica segura detrás de un muro que bloquea
 * la línea de visión del jugador.
 */
public class CoverNode {

    private final BlockPos pos;
    private final Vec3 centerVec;
    private UUID reservedBy = null;
    private int reservationTicks = 0;

    public CoverNode(BlockPos pos) {
        this.pos = pos.immutable();
        this.centerVec = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    public BlockPos getPos() { return pos; }
    public Vec3 getCenterVec() { return centerVec; }
    public UUID getReservedBy() { return reservedBy; }

    public boolean isAvailable(UUID mobUUID) {
        return reservedBy == null || Objects.equals(reservedBy, mobUUID) || reservationTicks <= 0;
    }

    public boolean claim(UUID mobUUID, int leaseTicks) {
        if (isAvailable(mobUUID)) {
            this.reservedBy = mobUUID;
            this.reservationTicks = leaseTicks;
            return true;
        }
        return false;
    }

    public void release() {
        this.reservedBy = null;
        this.reservationTicks = 0;
    }

    public void tick() {
        if (reservationTicks > 0) {
            reservationTicks--;
            if (reservationTicks <= 0) {
                this.reservedBy = null;
            }
        }
    }
}