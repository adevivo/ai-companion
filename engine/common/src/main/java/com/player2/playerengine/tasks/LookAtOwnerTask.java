package com.player2.playerengine.tasks;

import com.player2.playerengine.util.Playground;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.helpers.LookHelper;

public class LookAtOwnerTask extends Task {
    public static String name = "LookAtOwnerTask";
    @Override
    protected void onStart() {
        
    }

    @Override
    protected Task onTick() {
        if (this.controller.getOwner() != null) {
            LookHelper.lookAt(this.controller, this.controller.getOwner().getEyePosition());
        } else {
            this.controller.getClosestPlayer().ifPresent(
                    (player) -> {
                        LookHelper.lookAt(controller, player.getEyePosition());
                    });
        }
        Playground.IDLE_TEST_TICK_FUNCTION(this.controller);
        return null;
    }

    @Override
    protected void onStop(Task interruptTask) {
    }

    @Override
    public boolean isFinished() {
        return false;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof LookAtOwnerTask;
    }

    @Override
    protected String toDebugString() {
        return "LookAtOwnerTask";
    }
}
