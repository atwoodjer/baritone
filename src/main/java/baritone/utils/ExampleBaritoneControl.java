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

import baritone.Baritone;
import baritone.api.Settings;
import baritone.api.cache.IWaypoint;
import baritone.api.event.events.ChatEvent;
import baritone.api.pathing.goals.*;
import baritone.api.pathing.movement.ActionCosts;
import baritone.api.utils.RayTraceUtils;
import baritone.api.utils.SettingsUtil;
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
import net.minecraft.block.Block;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ExampleBaritoneControl extends Behavior implements Helper {

    public static ExampleBaritoneControl INSTANCE = new ExampleBaritoneControl();

    private ExampleBaritoneControl() {

    }

    public void initAndRegister() {
//        Baritone.INSTANCE.registerBehavior(this);
        Baritone.INSTANCE.registerBehavior(new BrigadierBaritoneControl());
    }

    @Override
    public void onSendChatMessage(ChatEvent event) {
        if (!Baritone.settings().chatControl.get()) {
            if (!Baritone.settings().removePrefix.get()) {
                return;
            }
        }
        String msg = event.getMessage();
        if (Baritone.settings().prefix.get()) {
            if (!msg.startsWith("#")) {
                return;
            }
            msg = msg.substring(1);
        }
        if (runCommand(msg)) {
            event.cancel();
        }
    }

    public boolean runCommand(String msg) {
        msg = msg.toLowerCase(Locale.US).trim();
        List<Settings.Setting<Boolean>> toggleable = Baritone.settings().getAllValuesByType(Boolean.class);
        for (Settings.Setting<Boolean> setting : toggleable) {
            if (msg.equalsIgnoreCase(setting.getName())) {
                setting.value ^= true;
                logDirect("Toggled " + setting.getName() + " to " + setting.value);
                SettingsUtil.save(Baritone.settings());
                return true;
            }
        }
        if (msg.equals("baritone") || msg.equals("settings")) {
            for (Settings.Setting<?> setting : Baritone.settings().allSettings) {
                logDirect(setting.toString());
            }
            return true;
        }
        if (msg.contains(" ")) {
            String[] data = msg.split(" ");
            if (data.length == 2) {
                Settings.Setting setting = Baritone.settings().byLowerName.get(data[0]);
                if (setting != null) {
                    try {
                        if (setting.value.getClass() == Long.class) {
                            setting.value = Long.parseLong(data[1]);
                        }
                        if (setting.value.getClass() == Integer.class) {
                            setting.value = Integer.parseInt(data[1]);
                        }
                        if (setting.value.getClass() == Double.class) {
                            setting.value = Double.parseDouble(data[1]);
                        }
                        if (setting.value.getClass() == Float.class) {
                            setting.value = Float.parseFloat(data[1]);
                        }
                    } catch (NumberFormatException e) {
                        logDirect("Unable to parse " + data[1]);
                        return true;
                    }
                    SettingsUtil.save(Baritone.settings());
                    logDirect(setting.toString());
                    return true;
                }
            }
        }
        if (Baritone.settings().byLowerName.containsKey(msg)) {
            Settings.Setting<?> setting = Baritone.settings().byLowerName.get(msg);
            logDirect(setting.toString());
            return true;
        }
        if (msg.startsWith("follow")) {
            String name = msg.substring(6).trim();
            Optional<Entity> toFollow = Optional.empty();
            if (name.length() == 0) {
                toFollow = RayTraceUtils.getSelectedEntity();
            } else {
                for (EntityPlayer pl : world().playerEntities) {
                    String theirName = pl.getName().trim().toLowerCase();
                    if (!theirName.equals(player().getName().trim().toLowerCase())) { // don't follow ourselves lol
                        if (theirName.contains(name) || name.contains(theirName)) {
                            toFollow = Optional.of(pl);
                        }
                    }
                }
            }
            if (!toFollow.isPresent()) {
                logDirect("Not found");
                return true;
            }
            FollowBehavior.INSTANCE.follow(toFollow.get());
            logDirect("Following " + toFollow.get());
            return true;
        }
        if (msg.startsWith("mine")) {
            String[] blockTypes = msg.substring(4).trim().split(" ");
            try {
                int quantity = Integer.parseInt(blockTypes[1]);
                Block block = ChunkPacker.stringToBlock(blockTypes[0]);
                Objects.requireNonNull(block);
                MineBehavior.INSTANCE.mine(quantity, block);
                logDirect("Will mine " + quantity + " " + blockTypes[0]);
                return true;
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException | NullPointerException ex) {}
            for (String s : blockTypes) {
                if (ChunkPacker.stringToBlock(s) == null) {
                    logDirect(s + " isn't a valid block name");
                    return true;
                }

            }
            MineBehavior.INSTANCE.mine(0, blockTypes);
            logDirect("Started mining blocks of type " + Arrays.toString(blockTypes));
            return true;
        }
        if (msg.startsWith("list") || msg.startsWith("get ") || msg.startsWith("show")) {
            String waypointType = msg.substring(4).trim();
            if (waypointType.endsWith("s")) {
                // for example, "show deaths"
                waypointType = waypointType.substring(0, waypointType.length() - 1);
            }
            Waypoint.Tag tag = Waypoint.Tag.fromString(waypointType);
            if (tag == null) {
                logDirect("Not a valid tag. Tags are: " + Arrays.asList(Waypoint.Tag.values()).toString().toLowerCase());
                return true;
            }
            Set<IWaypoint> waypoints = WorldProvider.INSTANCE.getCurrentWorld().getWaypoints().getByTag(tag);
            // might as well show them from oldest to newest
            List<IWaypoint> sorted = new ArrayList<>(waypoints);
            sorted.sort(Comparator.comparingLong(IWaypoint::getCreationTimestamp));
            logDirect("Waypoints under tag " + tag + ":");
            for (IWaypoint waypoint : sorted) {
                logDirect(waypoint.toString());
            }
            return true;
        }
        if (msg.startsWith("save")) {
            String name = msg.substring(4).trim();
            BlockPos pos = playerFeet();
            if (name.contains(" ")) {
                logDirect("Name contains a space, assuming it's in the format 'save waypointName X Y Z'");
                String[] parts = name.split(" ");
                if (parts.length != 4) {
                    logDirect("Unable to parse, expected four things");
                    return true;
                }
                try {
                    pos = new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                } catch (NumberFormatException ex) {
                    logDirect("Unable to parse coordinate integers");
                    return true;
                }
                name = parts[0];
            }
            WorldProvider.INSTANCE.getCurrentWorld().getWaypoints().addWaypoint(new Waypoint(name, Waypoint.Tag.USER, pos));
            logDirect("Saved user defined position " + pos + " under name '" + name + "'. Say 'goto user' to set goal, say 'list user' to list.");
            return true;
        }
        if (msg.startsWith("goto")) {
            String waypointType = msg.substring(4).trim();
            if (waypointType.endsWith("s") && Waypoint.Tag.fromString(waypointType.substring(0, waypointType.length() - 1)) != null) {
                // for example, "show deaths"
                waypointType = waypointType.substring(0, waypointType.length() - 1);
            }
            Waypoint.Tag tag = Waypoint.Tag.fromString(waypointType);
            IWaypoint waypoint;
            if (tag == null) {
                String mining = waypointType;
                Block block = ChunkPacker.stringToBlock(mining);
                //logDirect("Not a valid tag. Tags are: " + Arrays.asList(Waypoint.Tag.values()).toString().toLowerCase());
                if (block == null) {
                    waypoint = WorldProvider.INSTANCE.getCurrentWorld().getWaypoints().getAllWaypoints().stream().filter(w -> w.getName().equalsIgnoreCase(mining)).max(Comparator.comparingLong(IWaypoint::getCreationTimestamp)).orElse(null);
                    if (waypoint == null) {
                        logDirect("No locations for " + mining + " known, cancelling");
                        return true;
                    }
                } else {
                    List<BlockPos> locs = MineBehavior.INSTANCE.scanFor(Collections.singletonList(block), 64);
                    if (locs.isEmpty()) {
                        logDirect("No locations for " + mining + " known, cancelling");
                        return true;
                    }
                    PathingBehavior.INSTANCE.setGoal(new GoalComposite(locs.stream().map(GoalGetToBlock::new).toArray(Goal[]::new)));
                    PathingBehavior.INSTANCE.path();
                    return true;
                }
            } else {
                waypoint = WorldProvider.INSTANCE.getCurrentWorld().getWaypoints().getMostRecentByTag(tag);
                if (waypoint == null) {
                    logDirect("None saved for tag " + tag);
                    return true;
                }
            }
            Goal goal = new GoalBlock(waypoint.getLocation());
            PathingBehavior.INSTANCE.setGoal(goal);
            if (!PathingBehavior.INSTANCE.path()) {
                if (!goal.isInGoal(playerFeet())) {
                    logDirect("Currently executing a path. Please cancel it first.");
                }
            }
            return true;
        }
        return false;
    }
}
