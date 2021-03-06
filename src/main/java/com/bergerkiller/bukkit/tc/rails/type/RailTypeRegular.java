package com.bergerkiller.bukkit.tc.rails.type;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.map.MapBlendMode;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailTrackerMember;
import com.bergerkiller.bukkit.tc.editor.RailsTexture;
import com.bergerkiller.bukkit.tc.rails.logic.*;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.material.Rails;

/**
 * All regular types of rails - the ones provided by Minecraft.
 * All these rail types can slope and some of them can make curves.
 * None of them can be used vertically, hence, it is a horizontal rail.<br><br>
 * <p/>
 * Warning: all 'regular' type of rails REQUIRE a valid Rails Material type.
 * Do not write an isType check that results in non-Rails material types.
 */
public class RailTypeRegular extends RailTypeHorizontal {

    /**
     * Gets all possible directions a Minecart can go when on regular rails.
     * There is no 'UP' direction for vertical rails - it assumes the horizontal direction
     * towards the wall this vertical rail is attached to instead.
     *
     * @param railDirection of the track
     * @return all possible directions
     */
    public static BlockFace[] getPossibleDirections(BlockFace railDirection) {
        return FaceUtil.getFaces(railDirection.getOppositeFace());
    }

    /**
     * Gets the next position to go to, without requesting information from the rail itself.
     * This allows it to be used by other rail types.
     *
     * @param currentTrack     the 'Minecart' is on
     * @param currentDirection the 'Minecart' is moving
     * @param railDirection    of the currentTrack
     * @param sloped           state of the rail - whether it is sloped
     * @return the next position the 'Minecart' goes to
     */
    public static Block getNextPos(Block currentTrack, BlockFace currentDirection, BlockFace railDirection, boolean sloped) {
        if (FaceUtil.isSubCardinal(railDirection)) {
            // Get a set of possible directions to go to
            BlockFace[] possible = FaceUtil.getFaces(railDirection.getOppositeFace());

            // Simple forward - always true
            for (BlockFace newdir : possible) {
                if (newdir == currentDirection) {
                    return currentTrack.getRelative(currentDirection);
                }
            }

            // Get connected faces
            BlockFace dir = currentDirection.getOppositeFace();
            BlockFace nextDir;
            if (possible[0].equals(dir)) {
                nextDir = possible[1];
            } else if (possible[1].equals(dir)) {
                nextDir = possible[0];
                // south-east rule
            } else if (possible[0] == BlockFace.SOUTH || possible[0] == BlockFace.EAST) {
                nextDir = possible[0];
            } else {
                nextDir = possible[1];
            }
            return currentTrack.getRelative(nextDir);
        } else if (sloped) {
            if (railDirection == currentDirection || currentDirection == BlockFace.UP) {
                // Moving up the slope
                Block above = currentTrack.getRelative(BlockFace.UP);
                if (RailType.VERTICAL.isRail(above) && (currentDirection == BlockFace.UP || Util.getVerticalRailDirection(above) == currentDirection)) {
                    // Go to vertical rails above
                    return above;
                } else {
                    // Go up one and then forward
                    return above.getRelative(railDirection);
                }
            } else {
                // Moving down the slope, follow slope end-direction
                return currentTrack.getRelative(railDirection.getOppositeFace());
            }
        } else if (railDirection == currentDirection || railDirection.getOppositeFace() == currentDirection) {
            // Move along horizontal tracks
            return currentTrack.getRelative(currentDirection);
        } else {
            // South-West rule
            return currentTrack.getRelative(railDirection);
        }
    }

    @Override
    public boolean isRail(BlockData blockData) {
        return blockData.getType() == Material.RAILS;
    }

    @Override
    public IntVector3 findRail(MinecartMember<?> member, World world, IntVector3 pos) {
        if (member.getEntity().vel.getY() > 0.0) {
            // Only if we came from a vertical rail do we allow this to be used
            // In all other cases, we will no longer be using this (horizontal) rail.
            // If we came from a vertical rail and need to move onto a slope
            // Vertical -> Slope UP
            RailTrackerMember railTrackerMember = member.getRailTracker();
            if (railTrackerMember.getLastRailType() == RailType.VERTICAL) {
                IntVector3 nextPos = pos.add(railTrackerMember.getLastLogic().getDirection());
                BlockData blockData = WorldUtil.getBlockData(world, nextPos);
                if (this.isRail(blockData)) {
                    // Check that the direction of the rail is correct
                    Rails rails = blockData.newMaterialData(Rails.class);
                    BlockFace lastDirection = railTrackerMember.getLastLogic().getDirection();
                    if (rails != null && rails.isOnSlope() && rails.getDirection() == lastDirection) {
                        // We got a winner!
                        // Some position and velocity adjustment prior to moving between the types
                        CommonMinecart<?> entity = member.getEntity();
                        entity.loc.xz.set(nextPos.x + 0.5, nextPos.z + 0.5);
                        entity.loc.xz.subtract(lastDirection, 0.49);
                        // Y offset
                        final double transOffset = 0.01; // How high above the slope to teleport to
                        entity.loc.setY(nextPos.y + transOffset);
                        // Convert Y-velocity into XZ-velocity
                        entity.vel.xz.add(rails.getDirection(), entity.vel.getY());
                        entity.vel.y.setZero();
                        return nextPos;
                    }
                }
            }
            return super.findRail(member, world, pos);
        } else {
            // Find the rail, that'll be the end of it
            return super.findRail(member, world, pos);
        }
    }

    @Override
    public Block getNextPos(Block currentTrack, BlockFace currentDirection) {
        Rails rail = BlockUtil.getRails(currentTrack);
        if (rail == null) {
            return null;
        }
        return getNextPos(currentTrack, currentDirection, rail.getDirection(), rail.isOnSlope());
    }

    @Override
    public BlockFace[] getPossibleDirections(Block trackBlock) {
        Rails rails = BlockUtil.getRails(trackBlock);
        if (rails == null) {
            return new BlockFace[0];
        } else if (rails.isOnSlope() && Util.isVerticalAbove(trackBlock, rails.getDirection())) {
            return new BlockFace[] { rails.getDirection().getOppositeFace(), BlockFace.UP };
        } else {
            return getPossibleDirections(rails.getDirection());
        }
    }

    public RailLogicHorizontal getLogicForRails(Block railsBlock, Rails rails) {
        BlockFace direction = rails.getDirection();

        // Sloped logic
        if (rails.isOnSlope()) {
            // To vertical
            if (Util.isVerticalAbove(railsBlock, direction)) {
                return RailLogicVerticalSlopeDown.get(direction);
            }
            // Default
            return RailLogicSloped.get(direction);
        }

        // Default Horizontal logic
        return RailLogicHorizontal.get(direction);
    }

    @Override
    public RailLogic getLogic(MinecartMember<?> member, Block railsBlock) {
        Rails rails = BlockUtil.getRails(railsBlock);
        if (rails == null) {
            return RailLogicGround.INSTANCE;
        }
        return getLogicForRails(railsBlock, rails);
    }

    @Override
    public BlockFace getDirection(Block railsBlock) {
        Rails rails = BlockUtil.getRails(railsBlock);
        return rails == null ? BlockFace.SELF : rails.getDirection();
    }

    @Override
    public Location getSpawnLocation(Block railsBlock, BlockFace orientation) {
        Rails rails = BlockUtil.getRails(railsBlock);
        BlockFace dir = FaceUtil.getRailsCartDirection(rails.getDirection());
        Location result = super.getSpawnLocation(railsBlock, dir);
        if (rails.isOnSlope()) {
            // At a 45-degree angle
            result.setPitch(-45.0F);
            // Slope height offset
            result.setY(result.getY() + 0.5);
        }
        return result;
    }

    @Override
    public RailsTexture getRailsTexture(Block railsBlock) {
        Rails rails = BlockUtil.getRails(railsBlock);
        if (rails == null) {
            return super.getRailsTexture(railsBlock);
        }
        BlockFace direction = rails.getDirection();
        if (FaceUtil.isSubCardinal(direction)) {
            int yaw = 45 - FaceUtil.faceToYaw(direction);
            MapTexture top = RailsTexture.rotate(getResource(rails, "top"), yaw);
            MapTexture back = top.clone();
            back.setBlendMode(MapBlendMode.MULTIPLY).fill(MapColorPalette.getColor(160, 160, 160));
            back = RailsTexture.flipV(back);
            RailsTexture result = new RailsTexture()
                    .set(BlockFace.UP, top)
                    .set(BlockFace.DOWN, back);
            for (BlockFace face : FaceUtil.AXIS) {
                result.set(face, RailsTexture.rotate(top, FaceUtil.faceToYaw(face)));
            }
            return result;
        } else if (rails.isOnSlope()) {
            MapTexture side = getResource(rails, "side");
            MapTexture top = getResource(rails, "top");
            MapTexture back = top.clone();
            back.setBlendMode(MapBlendMode.MULTIPLY).fill(MapColorPalette.getColor(160, 160, 160));
            return new RailsTexture()
                    .set(direction.getOppositeFace(), top)
                    .set(direction, RailsTexture.flipV(back))
                    .set(BlockFace.UP, RailsTexture.rotate(top, -FaceUtil.faceToYaw(direction)))
                    .set(BlockFace.DOWN, RailsTexture.rotate(back, FaceUtil.faceToYaw(direction)))
                    .setOpposites(FaceUtil.rotate(direction, 2), side);
            
        } else {
            MapTexture front = getResource(rails, "front");
            MapTexture side = getResource(rails, "side");
            MapTexture top = RailsTexture.rotate(getResource(rails, "top"), FaceUtil.faceToYaw(direction));
            MapTexture back = top.clone();
            back.setBlendMode(MapBlendMode.MULTIPLY).fill(MapColorPalette.getColor(160, 160, 160));
            return new RailsTexture()
                    .set(BlockFace.UP, top)
                    .set(BlockFace.DOWN, back)
                    .setOpposites(direction, front)
                    .setOpposites(FaceUtil.rotate(direction, 2), side);
        }
    }

    protected String getRailsTexturePath(Rails rails, String name) {
        if (rails.isCurve()) {
            return "com/bergerkiller/bukkit/tc/textures/rails/regular_curved_" + name + ".png";
        } else if (rails.isOnSlope()) {
            return "com/bergerkiller/bukkit/tc/textures/rails/regular_sloped_" + name + ".png";
        } else {
            return "com/bergerkiller/bukkit/tc/textures/rails/regular_straight_" + name + ".png";
        }
    }

    private MapTexture getResource(Rails rails, String name) {
        return MapTexture.loadPluginResource(TrainCarts.plugin, getRailsTexturePath(rails, name));
    }
}
