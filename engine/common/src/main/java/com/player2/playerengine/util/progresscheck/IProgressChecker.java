package com.player2.playerengine.util.progresscheck;

public interface IProgressChecker<T> {
   void setProgress(T var1);

   boolean failed();

   void reset();
}
