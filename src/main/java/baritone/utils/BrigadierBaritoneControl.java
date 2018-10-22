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

import baritone.api.event.events.ChatEvent;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.pathing.goals.GoalYLevel;
import baritone.behavior.Behavior;
import baritone.behavior.PathingBehavior;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import static com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;

/**
 * @author Brady
 * @since 10/21/2018
 */
public class BrigadierBaritoneControl extends Behavior implements Helper {

    private final CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();

    BrigadierBaritoneControl() {
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
            // There should be a way to have multiple literals for a single command, not sure exactly how yet
            .then(literal("none")
                .executes(c -> {
                    setGoal(null);
                    return 0;
                })
            )
            .then(literal("clear")
                .executes(c -> {
                    setGoal(null);
                    return 0;
                })
            )
            .executes(c -> {
                setGoal(new GoalBlock(playerFeet()));
                return 0;
            })
        );

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
    }

    @Override
    public void onSendChatMessage(ChatEvent event) {
        try {
            dispatcher.execute(event.getMessage(), mc.player);
            event.cancel();
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
