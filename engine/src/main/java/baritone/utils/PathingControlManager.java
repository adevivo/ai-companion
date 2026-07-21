package baritone.utils;

import baritone.Baritone;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.pathing.calc.IPathingControlManager;
import baritone.api.pathing.goals.Goal;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.behavior.PathingBehavior;
import baritone.pathing.path.PathExecutor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;

public class PathingControlManager implements IPathingControlManager {
   private final Baritone baritone;
   private final HashSet<IBaritoneProcess> processes;
   private final List<IBaritoneProcess> active;
   private IBaritoneProcess inControlLastTick;
   private IBaritoneProcess inControlThisTick;
   private PathingCommand command;

   public PathingControlManager(Baritone baritone) {
      this.baritone = baritone;
      this.processes = new HashSet<>();
      this.active = new ArrayList<>();
      baritone.getGameEventHandler().registerEventListener(new AbstractGameEventListener() {
         @Override
         public void onTickServer() {
            PathingControlManager.this.postPathingTick();
         }
      });
   }

   @Override
   public void registerProcess(IBaritoneProcess process) {
      process.onLostControl();
      this.processes.add(process);
   }

   public void cancelEverything() {
      this.inControlLastTick = null;
      this.inControlThisTick = null;
      this.command = null;
      this.active.clear();

      for (IBaritoneProcess proc : this.processes) {
         proc.onLostControl();
         if (proc.isActive() && !proc.isTemporary()) {
            throw new IllegalStateException(proc.displayName());
         }
      }
   }

   @Override
   public Optional<IBaritoneProcess> mostRecentInControl() {
      return Optional.ofNullable(this.inControlThisTick);
   }

   @Override
   public Optional<PathingCommand> mostRecentCommand() {
      return Optional.ofNullable(this.command);
   }

   public void prePathingTick() {
      this.inControlLastTick = this.inControlThisTick;
      this.inControlThisTick = null;
      PathingBehavior p = this.baritone.getPathingBehavior();
      this.command = this.executeProcesses();
      if (this.command == null) {
         p.cancelSegmentIfSafe();
         p.secretInternalSetGoal(null);
      } else {
         if (!Objects.equals(this.inControlThisTick, this.inControlLastTick)
            && this.command.commandType != PathingCommandType.REQUEST_PAUSE
            && this.inControlLastTick != null
            && !this.inControlLastTick.isTemporary()) {
            p.cancelSegmentIfSafe();
         }

         switch (this.command.commandType) {
            case REQUEST_PAUSE:
               p.requestPause();
               break;
            case CANCEL_AND_SET_GOAL:
               p.secretInternalSetGoal(this.command.goal);
               p.cancelSegmentIfSafe();
               break;
            case FORCE_REVALIDATE_GOAL_AND_PATH:
               if (!p.isPathing() && !p.getInProgress().isPresent()) {
                  p.secretInternalSetGoalAndPath(this.command);
               }
               break;
            case REVALIDATE_GOAL_AND_PATH:
               if (!p.isPathing() && !p.getInProgress().isPresent()) {
                  p.secretInternalSetGoalAndPath(this.command);
               }
               break;
            case SET_GOAL_AND_PATH:
               if (this.command.goal != null) {
                  this.baritone.getPathingBehavior().secretInternalSetGoalAndPath(this.command);
               }
               break;
            default:
               throw new IllegalStateException();
         }
      }
   }

   private void postPathingTick() {
      if (this.command != null) {
         PathingBehavior p = this.baritone.getPathingBehavior();
         switch (this.command.commandType) {
            case FORCE_REVALIDATE_GOAL_AND_PATH:
               if (this.command.goal == null || this.forceRevalidate(this.command.goal) || this.revalidateGoal(this.command.goal)) {
                  p.softCancelIfSafe();
               }

               p.secretInternalSetGoalAndPath(this.command);
               break;
            case REVALIDATE_GOAL_AND_PATH:
               if (this.baritone.settings().cancelOnGoalInvalidation.get() && (this.command.goal == null || this.revalidateGoal(this.command.goal))) {
                  p.softCancelIfSafe();
               }

               p.secretInternalSetGoalAndPath(this.command);
         }
      }
   }

   public boolean forceRevalidate(Goal newGoal) {
      PathExecutor current = this.baritone.getPathingBehavior().getCurrent();
      if (current != null) {
         return newGoal.isInGoal(current.getPath().getDest()) ? false : !newGoal.toString().equals(current.getPath().getGoal().toString());
      } else {
         return false;
      }
   }

   public boolean revalidateGoal(Goal newGoal) {
      PathExecutor current = this.baritone.getPathingBehavior().getCurrent();
      if (current != null) {
         Goal intended = current.getPath().getGoal();
         BlockPos end = current.getPath().getDest();
         if (intended.isInGoal(end) && !newGoal.isInGoal(end)) {
            return true;
         }
      }

      return false;
   }

   public PathingCommand executeProcesses() {
      for (IBaritoneProcess process : this.processes) {
         if (process.isActive()) {
            if (!this.active.contains(process)) {
               this.active.add(0, process);
            }
         } else {
            this.active.remove(process);
         }
      }

      this.active.sort(Comparator.comparingDouble(IBaritoneProcess::priority).reversed());
      Iterator<IBaritoneProcess> iterator = this.active.iterator();

      while (iterator.hasNext()) {
         IBaritoneProcess proc = iterator.next();
         PathingCommand exec = proc.onTick(
            Objects.equals(proc, this.inControlLastTick) && this.baritone.getPathingBehavior().calcFailedLastTick(),
            this.baritone.getPathingBehavior().isSafeToCancel()
         );
         if (exec == null) {
            if (proc.isActive()) {
               throw new IllegalStateException(proc.displayName() + " actively returned null PathingCommand");
            }
         } else if (exec.commandType != PathingCommandType.DEFER) {
            this.inControlThisTick = proc;
            if (!proc.isTemporary()) {
               iterator.forEachRemaining(IBaritoneProcess::onLostControl);
            }

            return exec;
         }
      }

      return null;
   }

   public boolean isActive() {
      return !this.active.isEmpty();
   }
}
