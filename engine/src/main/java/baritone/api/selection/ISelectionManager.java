package baritone.api.selection;

import baritone.api.component.EntityComponentKey;
import baritone.api.utils.BetterBlockPos;
import baritone.selection.SelectionManager;
import net.minecraft.core.Direction;

public interface ISelectionManager {
   EntityComponentKey<ISelectionManager> KEY = new EntityComponentKey<>(SelectionManager::new);

   ISelection addSelection(ISelection var1);

   ISelection addSelection(BetterBlockPos var1, BetterBlockPos var2);

   ISelection removeSelection(ISelection var1);

   ISelection[] removeAllSelections();

   ISelection[] getSelections();

   ISelection getOnlySelection();

   ISelection getLastSelection();

   ISelection expand(ISelection var1, Direction var2, int var3);

   ISelection contract(ISelection var1, Direction var2, int var3);

   ISelection shift(ISelection var1, Direction var2, int var3);
}
