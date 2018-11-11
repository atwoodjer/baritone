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

package baritone.bot.spec;

import baritone.Baritone;
import baritone.bot.IBaritoneUser;
import baritone.utils.InputOverrideHandler;
import net.minecraft.util.MovementInput;

import static baritone.utils.InputOverrideHandler.Input.*;

/**
 * @author Brady
 * @since 10/29/2018
 */
public class BotMovementInput extends MovementInput {

    private final IBaritoneUser user;

    public BotMovementInput(IBaritoneUser user) {
        this.user = user;
    }

    public void updatePlayerMoveState() {
        this.moveStrafe = 0.0F;
        this.moveForward = 0.0F;

        // These are placeholders until an input overrider is implemented for bots
        boolean forward, back, left, right, jump, sneak;

        InputOverrideHandler i = Baritone.INSTANCE.getInputOverrideHandler();
        forward = i.isInputForcedDown(MOVE_FORWARD);
        back    = i.isInputForcedDown(MOVE_BACK);
        left    = i.isInputForcedDown(MOVE_LEFT);
        right   = i.isInputForcedDown(MOVE_RIGHT);
        jump    = i.isInputForcedDown(JUMP);
        sneak   = i.isInputForcedDown(SNEAK);

        if (this.forwardKeyDown = forward) {
            this.moveForward++;
        }

        if (this.backKeyDown = back) {
            this.moveForward--;
        }

        if (this.leftKeyDown = left) {
            this.moveStrafe++;
        }

        if (this.rightKeyDown = right) {
            this.moveStrafe--;
        }

        this.jump = jump;

        if (this.sneak = sneak) {
            this.moveStrafe *= 0.3D;
            this.moveForward *= 0.3D;
        }
    }
}
