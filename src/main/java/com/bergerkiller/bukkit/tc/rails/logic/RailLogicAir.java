package com.bergerkiller.bukkit.tc.rails.logic;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

/**
 * Handles movement of a minecart that is flying through the air
 */
public class RailLogicAir extends RailLogic {
    public static final RailLogicAir INSTANCE = new RailLogicAir();

    private RailLogicAir() {
        super(BlockFace.SELF);
    }

    @Override
    public BlockFace getMovementDirection(MinecartMember<?> member, BlockFace endDirection) {
        return endDirection;
    }

    @Override
    public double getForwardVelocity(MinecartMember<?> member) {
        if (member.getEntity().vel.xz.lengthSquared() == 0.0) {
            return member.getEntity().vel.getY() * member.getDirection().getModY();
        } else {
            return member.getEntity().vel.length();
        }
    }

    @Override
    public void setForwardVelocity(MinecartMember<?> member, double force) {
        if (member.isMovementControlled()) {
            // Be sure to use the direction, we are being controlled!
            super.setForwardVelocity(member, force);
        } else if (member.getEntity().vel.xz.lengthSquared() == 0.0) {
            // Moving only vertically; control speed in order to maintain a vertical stack
            Vector vel = member.getEntity().vel.vector();
            MathUtil.setVectorLength(vel, force);
            member.getEntity().vel.set(vel);
        } else {
            // Simply set vector length
            // Setting speed while in the air causes pretty awful breakage, unfortunately
            // Free-falling looks more natural
            Vector vel = member.getEntity().vel.vector();
            MathUtil.setVectorLength(vel, force);
            member.getEntity().vel.set(vel);
        }
    }

    @Override
    public boolean hasVerticalMovement() {
        return true;
    }

    @Override
    public Vector getFixedPosition(CommonMinecart<?> entity, double x, double y, double z, IntVector3 railPos) {
        return new Vector(x, y, z);
    }

    @Override
    public void onPreMove(MinecartMember<?> member) {
        CommonMinecart<?> entity = member.getEntity();

        // If we were previously on rails, check if these were sloped rails to give a Y-velocity boost
        if (member.getRailTracker().getLastLogic() instanceof RailLogicSloped) {
            BlockFace slopeDir = ((RailLogicSloped) member.getRailTracker().getLastLogic()).getDirection();
            double velLen = entity.vel.length();
            double dx = slopeDir.getModX() * MathUtil.HALFROOTOFTWO * velLen;
            double dz = slopeDir.getModZ() * MathUtil.HALFROOTOFTWO * velLen;
            if (slopeDir == member.getDirectionFrom()) {
                entity.vel.set(dx, MathUtil.HALFROOTOFTWO * velLen, dz);
            } else {
                entity.vel.set(-dx, -MathUtil.HALFROOTOFTWO * velLen, -dz);
            }
        }

        // Only do this logic if the head is is not moving vertically
        // Or if this member is the head, of course
        if (member.isMovingVerticalOnly() && entity.vel.getY() > 0.0) {
            MinecartMember<?> head = member.getGroup().head();
            if (member != head && head.isMovingVerticalOnly()) {
                return;
            }
        }

        // Apply flying friction
        if (!member.isMovementControlled()) {
            entity.vel.multiply(entity.getFlyingVelocityMod());
        }
    }
}
