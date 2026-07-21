package adris.altoclef.player2api;

import java.util.ArrayList;

public class MessageBuffer {
   ArrayList<String> msgs = new ArrayList<>();
   int maxSize;

   public MessageBuffer(int maxSize) {
      this.maxSize = maxSize;
   }

   public void addMsg(String msg) {
      this.msgs.add(msg);
      if (this.msgs.size() > this.maxSize) {
         this.msgs.remove(0);
      }
   }

   private void dump() {
      this.msgs = new ArrayList<>();
   }

   public String dumpAndGetString() {
      StringBuilder sb = new StringBuilder();
      sb.append("[");

      for (String msg : this.msgs) {
         sb.append(String.format("\"%s\",", msg));
      }

      this.dump();
      return sb.append("]").toString();
   }
}
