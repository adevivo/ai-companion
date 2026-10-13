package com.player2.playerengine.tasks.base;

/**
 * A root task that runs until told to stop and is not searching for anything, such as guarding or
 * following the owner. Its wanders are only ways of catching up, so standing still for a while is
 * the job going right. {@code TimeoutWanderTask}'s stall give-up skips these roots: it used to end a
 * bodyguard after a minute of calm and tell the model to go check chests for the missing items.
 */
public interface ITaskOpenEnded {
}
