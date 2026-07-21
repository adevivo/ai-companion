package adris.altoclef.util.helpers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FuzzySearchHelper {
   public static String getClosestMatchMinecraftItems(String attemptedSearch, Collection<String> validValues) {
      attemptedSearch = attemptedSearch.toLowerCase().trim().replace(" ", "_");
      return getClosestMatch(attemptedSearch, validValues);
   }

   public static String getClosestMatch(String attemptedSearch, Collection<String> validValues) {
      List<String> result = new ArrayList<>(validValues);
      Comparator<String> closenessComp = Comparator.comparingDouble(k -> distance(attemptedSearch, k));
      return result.stream().min(closenessComp).orElse(null);
   }

   private static int distance(CharSequence lhs, CharSequence rhs) {
      return distance(lhs, rhs, 1, 1, 1, 1);
   }

   private static int distance(CharSequence source, CharSequence target, int deleteCost, int insertCost, int replaceCost, int swapCost) {
      if (2 * swapCost < insertCost + deleteCost) {
         throw new IllegalArgumentException("Unsupported cost assignment");
      } else if (source.length() == 0) {
         return target.length() * insertCost;
      } else if (target.length() == 0) {
         return source.length() * deleteCost;
      } else {
         int[][] table = new int[source.length()][target.length()];
         Map<Character, Integer> sourceIndexByCharacter = new HashMap<>();
         if (source.charAt(0) != target.charAt(0)) {
            table[0][0] = Math.min(replaceCost, deleteCost + insertCost);
         }

         sourceIndexByCharacter.put(source.charAt(0), 0);

         for (int k = 1; k < source.length(); k++) {
            int deleteDistance = table[k - 1][0] + deleteCost;
            int insertDistance = (k + 1) * deleteCost + insertCost;
            int matchDistance = k * deleteCost + (source.charAt(k) == target.charAt(0) ? 0 : replaceCost);
            table[k][0] = Math.min(Math.min(deleteDistance, insertDistance), matchDistance);
         }

         for (int j = 1; j < target.length(); j++) {
            int deleteDistance = (j + 1) * insertCost + deleteCost;
            int insertDistance = table[0][j - 1] + insertCost;
            int matchDistance = j * insertCost + (source.charAt(0) == target.charAt(j) ? 0 : replaceCost);
            table[0][j] = Math.min(Math.min(deleteDistance, insertDistance), matchDistance);
         }

         for (int i = 1; i < source.length(); i++) {
            int maxSourceLetterMatchIndex = source.charAt(i) == target.charAt(0) ? 0 : -1;

            for (int m = 1; m < target.length(); m++) {
               Integer candidateSwapIndex = sourceIndexByCharacter.get(target.charAt(m));
               int jSwap = maxSourceLetterMatchIndex;
               int deleteDistance = table[i - 1][m] + deleteCost;
               int insertDistance = table[i][m - 1] + insertCost;
               int matchDistance = table[i - 1][m - 1];
               if (source.charAt(i) != target.charAt(m)) {
                  matchDistance += replaceCost;
               } else {
                  maxSourceLetterMatchIndex = m;
               }

               int swapDistance;
               if (candidateSwapIndex != null && jSwap != -1) {
                  int iSwap = candidateSwapIndex;
                  int preSwapCost;
                  if (iSwap == 0 && jSwap == 0) {
                     preSwapCost = 0;
                  } else {
                     preSwapCost = table[Math.max(0, iSwap - 1)][Math.max(0, jSwap - 1)];
                  }

                  swapDistance = preSwapCost + (i - iSwap - 1) * deleteCost + (m - jSwap - 1) * insertCost + swapCost;
               } else {
                  swapDistance = Integer.MAX_VALUE;
               }

               table[i][m] = Math.min(Math.min(Math.min(deleteDistance, insertDistance), matchDistance), swapDistance);
            }

            sourceIndexByCharacter.put(source.charAt(i), i);
         }

         return table[source.length() - 1][target.length() - 1];
      }
   }
}
