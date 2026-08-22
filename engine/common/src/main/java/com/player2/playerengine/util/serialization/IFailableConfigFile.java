package com.player2.playerengine.util.serialization;

public interface IFailableConfigFile {
   void onFailLoad();

   boolean failedToLoad();
}
