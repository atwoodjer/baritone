/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.utils;

import baritone.api.cache.IWaypoint;
import baritone.api.event.events.ChatEvent;
import baritone.api.pathing.goals.*;
import baritone.api.pathing.movement.ActionCosts;
import baritone.behavior.Behavior;
import baritone.behavior.FollowBehavior;
import baritone.behavior.MineBehavior;
import baritone.behavior.PathingBehavior;
import baritone.cache.ChunkPacker;
import baritone.cache.Waypoint;
import baritone.cache.WorldProvider;
import baritone.pathing.calc.AbstractNodeCostSearch;
import baritone.pathing.movement.Movement;
import baritone.pathing.movement.Moves;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.block.Block;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;

/**
 * @author Brady
 * @since 10/21/2018
 */
public class BrigadierBaritoneControl extends Behavior implements Helper {

    private final CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();

    BrigadierBaritoneControl() {
        CommandNode<Object> goalClearNode, cancelNode, spawnNode, repackNode;

        dispatcher.register(literal("thisway")
            .then(argument("distance", doubleArg(0.0))
                .suggests((ctx, builder) -> builder.suggest(1000).buildFuture())
                .executes(c -> {
                    double distance = c.getArgument("distance", Double.class);

                    Goal goal = GoalXZ.fromDirection(playerFeetAsVec(), player().rotationYaw, distance);
                    PathingBehavior.INSTANCE.setGoal(goal);
                    logDirect("Goal: " + goal);

                    return 0;
                })
            )
        );

        dispatcher.register(literal("goal")
            .then(argument("yLevel", integer())
                .executes(c -> {
                    setGoal(new GoalYLevel(c.getArgument("yLevel", Integer.class)));
                    return 0;
                })
            )
            .then(argument("x", integer())
                .then(argument("z", integer())
                    .executes(c -> {
                        setGoal(new GoalXZ(c.getArgument("x", Integer.class), c.getArgument("z", Integer.class)));
                        return 0;
                    })
                )
            )
            .then(argument("x", integer())
                .then(argument("y", integer())
                    .then(argument("z", integer())
                        .executes(c -> {
                            setGoal(new GoalBlock(c.getArgument("x", Integer.class), c.getArgument("y", Integer.class), c.getArgument("z", Integer.class)));
                            return 0;
                        })
                    )
                )
            )
            .then(goalClearNode = literal("clear")
                .executes(c -> {
                    setGoal(null);
                    return 0;
                }).build()
            )
            .executes(c -> {
                setGoal(new GoalBlock(playerFeet()));
                return 0;
            })
        );

        // I dislike this
        dispatcher.register(literal("none").redirect(goalClearNode));

        dispatcher.register(literal("path").executes(c -> {
            if (!PathingBehavior.INSTANCE.path()) {
                if (PathingBehavior.INSTANCE.getGoal() == null) {
                    logDirect("No goal.");
                } else {
                    if (PathingBehavior.INSTANCE.getGoal().isInGoal(playerFeet())) {
                        logDirect("Already in goal");
                    } else {
                        logDirect("Currently executing a path. Please cancel it first.");
                    }
                }
            }
            return 0;
        }));

        dispatcher.register(literal("axis").executes(c -> {
            PathingBehavior.INSTANCE.setGoal(new GoalAxis());
            PathingBehavior.INSTANCE.path();
            return 0;
        }));

        cancelNode = dispatcher.register(literal("clear").executes(c -> {
            MineBehavior.INSTANCE.cancel();
            FollowBehavior.INSTANCE.cancel();
            PathingBehavior.INSTANCE.cancel();
            logDirect("ok canceled");
            return 0;
        }));
        dispatcher.register(literal("stop").redirect(cancelNode));

        dispatcher.register(literal("forcecancel").executes(c -> {
            MineBehavior.INSTANCE.cancel();
            FollowBehavior.INSTANCE.cancel();
            PathingBehavior.INSTANCE.cancel();
            AbstractNodeCostSearch.forceCancel();
            PathingBehavior.INSTANCE.forceCancel();
            logDirect("ok force canceled");
            return 0;
        }));

        dispatcher.register(literal("gc").executes(c -> {
            System.gc();
            logDirect("Called System.gc();");
            return 0;
        }));

        dispatcher.register(literal("saveall").executes(c -> {
            WorldProvider.INSTANCE.getCurrentWorld().getCachedWorld().save();
            logDirect("Saved the current cached world");
            return 0;
        }));

        dispatcher.register(literal("reloadall").executes(c -> {
            WorldProvider.INSTANCE.getCurrentWorld().getCachedWorld().reloadAllFromDisk();
            logDirect("Reloaded the cached world");
            return 0;
        }));

        dispatcher.register(literal("sethome").executes(c -> {
            WorldProvider.INSTANCE.getCurrentWorld().getWaypoints().addWaypoint(new Waypoint("", Waypoint.Tag.HOME, playerFeet()));
            logDirect("Saved. Say home to set goal.");
            return 0;
        }));

        dispatcher.register(literal("home").executes(c -> {
            IWaypoint waypoint = WorldProvider.INSTANCE.getCurrentWorld().getWaypoints().getMostRecentByTag(Waypoint.Tag.HOME);
            if (waypoint == null) {
                logDirect("home not saved");
            } else {
                Goal goal = new GoalBlock(waypoint.getLocation());
                PathingBehavior.INSTANCE.setGoal(goal);
                PathingBehavior.INSTANCE.path();
                logDirect("Going to saved home " + goal);
            }
            return 0;
        }));

        spawnNode = dispatcher.register(literal("spawn").executes(c -> {
            IWaypoint waypoint = WorldProvider.INSTANCE.getCurrentWorld().getWaypoints().getMostRecentByTag(Waypoint.Tag.BED);
            if (waypoint == null) {
                BlockPos spawnPoint = player().getBedLocation();
                // for some reason the default spawnpoint is underground sometimes
                Goal goal = new GoalXZ(spawnPoint.getX(), spawnPoint.getZ());
                logDirect("spawn not saved, defaulting to world spawn. set goal to " + goal);
                PathingBehavior.INSTANCE.setGoal(goal);
            } else {
                Goal goal = new GoalBlock(waypoint.getLocation());
                PathingBehavior.INSTANCE.setGoal(goal);
                logDirect("Set goal to most recent bed " + goal);
            }
            return 0;
        }));
        dispatcher.register(literal("bed").redirect(spawnNode));

        dispatcher.register(literal("msg").executes(c -> {
            List<Movement> moves = Stream.of(Moves.values()).map(x -> x.apply0(playerFeet())).collect(Collectors.toCollection(ArrayList::new));
            while (moves.contains(null)) {
                moves.remove(null);
            }
            moves.sort(Comparator.comparingDouble(Movement::getCost));
            for (Movement move : moves) {
                String[] parts = move.getClass().toString().split("\\.");
                double cost = move.getCost();
                String strCost = cost + "";
                if (cost >= ActionCosts.COST_INF) {
                    strCost = "IMPOSSIBLE";
                }
                logDirect(parts[parts.length - 1] + " " + move.getDest().getX() + "," + move.getDest().getY() + "," + move.getDest().getZ() + " " + strCost);
            }
            return 0;
        }));

        dispatcher.register(literal("damn").executes(c -> {
            logDirect("daniel");
            return 1;
        }));

        dispatcher.register(literal("pause").executes(c -> {
            boolean enabled = PathingBehavior.INSTANCE.toggle();
            logDirect("Pathing Behavior has " + (enabled ? "resumed" : "paused") + ".");
            return 0;
        }));

        dispatcher.register(literal("invert").executes(c -> {
            Goal goal = PathingBehavior.INSTANCE.getGoal();
            BlockPos runAwayFrom;
            if (goal instanceof GoalXZ) {
                runAwayFrom = new BlockPos(((GoalXZ) goal).getX(), 0, ((GoalXZ) goal).getZ());
            } else if (goal instanceof GoalBlock) {
                runAwayFrom = ((GoalBlock) goal).getGoalPos();
            } else {
                logDirect("Goal must be GoalXZ or GoalBlock to invert");
                logDirect("Inverting goal of player feet");
                runAwayFrom = playerFeet();
            }
            PathingBehavior.INSTANCE.setGoal(new GoalRunAway(1, runAwayFrom) {
                @Override
                public boolean isInGoal(BlockPos pos) {
                    return false;
                }
            });
            if (!PathingBehavior.INSTANCE.path()) {
                logDirect("Currently executing a path. Please cancel it first.");
            }
            return 0;
        }));

        repackNode = dispatcher.register(literal("repack").executes(c -> {
            ChunkProviderClient cli = world().getChunkProvider();
            int playerChunkX = playerFeet().getX() >> 4;
            int playerChunkZ = playerFeet().getZ() >> 4;
            int count = 0;
            for (int x = playerChunkX - 40; x <= playerChunkX + 40; x++) {
                for (int z = playerChunkZ - 40; z <= playerChunkZ + 40; z++) {
                    Chunk chunk = cli.getLoadedChunk(x, z);
                    if (chunk != null) {
                        count++;
                        WorldProvider.INSTANCE.getCurrentWorld().getCachedWorld().queueForPacking(chunk);
                    }
                }
            }
            logDirect("Queued " + count + " chunks for repacking");
            return 0;
        }));
        dispatcher.register(literal("rescan").redirect(repackNode));

        dispatcher.register(literal("find")
            .then(argument("block", string()).executes(c -> { // TODO: Create a custom "Block" argument type
                String blockType = c.getArgument("block", String.class);

                LinkedList<BlockPos> locs = WorldProvider.INSTANCE.getCurrentWorld().getCachedWorld().getLocationsOf(blockType, 1, 4);
                logDirect("Have " + locs.size() + " locations");
                for (BlockPos pos : locs) {
                    Block actually = BlockStateInterface.get(pos).getBlock();
                    if (!ChunkPacker.blockToString(actually).equalsIgnoreCase(blockType)) {
                        System.out.println("Was looking for " + blockType + " but actually found " + actually + " " + ChunkPacker.blockToString(actually));
                    }
                }
                return 0;
            })
        ));
    }

    @Override
    public void onSendChatMessage(ChatEvent event) {
        try {
            // If the command exited with a regular state (0) then cancel the input message
            if (dispatcher.execute(event.getMessage(), mc.player) == 0) {
                event.cancel();
            }
        } catch (CommandSyntaxException e) {
            e.printStackTrace();
            if (!e.getType().toString().equals("Unknown command")) {
                logDirect(e.getMessage());
                event.cancel();
            }
        }
    }

    private void setGoal(Goal goal) {
        PathingBehavior.INSTANCE.setGoal(goal);
        logDirect("Goal: " + goal);
    }
}
