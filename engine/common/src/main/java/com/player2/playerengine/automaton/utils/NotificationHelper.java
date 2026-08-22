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

package com.player2.playerengine.automaton.utils;

import com.player2.playerengine.PlayerEngine;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.TrayIcon.MessageType;
import java.io.IOException;
import org.apache.commons.lang3.SystemUtils;

public class NotificationHelper {
   private static TrayIcon trayIcon;

   public static void notify(String text, boolean error) {
      if (SystemUtils.IS_OS_WINDOWS) {
         windows(text, error);
      } else if (SystemUtils.IS_OS_MAC_OSX) {
         mac(text);
      } else if (SystemUtils.IS_OS_LINUX) {
         linux(text);
      }
   }

   private static void windows(String text, boolean error) {
      if (SystemTray.isSupported()) {
         try {
            if (trayIcon == null) {
               SystemTray tray = SystemTray.getSystemTray();
               Image image = Toolkit.getDefaultToolkit().createImage("");
               trayIcon = new TrayIcon(image, "Baritone");
               trayIcon.setImageAutoSize(true);
               trayIcon.setToolTip("Baritone");
               tray.add(trayIcon);
            }

            trayIcon.displayMessage("Baritone", text, error ? MessageType.ERROR : MessageType.INFO);
         } catch (Exception var4) {
            PlayerEngine.LOGGER.error(var4);
         }
      } else {
         PlayerEngine.LOGGER.error("SystemTray is not supported");
      }
   }

   private static void mac(String text) {
      ProcessBuilder processBuilder = new ProcessBuilder();
      processBuilder.command("osascript", "-e", "display notification \"" + text + "\" with title \"Baritone\"");

      try {
         processBuilder.start();
      } catch (IOException var3) {
         PlayerEngine.LOGGER.error(var3);
      }
   }

   private static void linux(String text) {
      ProcessBuilder processBuilder = new ProcessBuilder();
      processBuilder.command("notify-send", "-a", "Baritone", text);

      try {
         processBuilder.start();
      } catch (IOException var3) {
         PlayerEngine.LOGGER.error(var3);
      }
   }
}
