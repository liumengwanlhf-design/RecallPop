package dev.ankigate;

import java.util.concurrent.atomic.AtomicBoolean;

/** One displayed review owns its temporary deck choice until its accepted score ends. */
final class ReviewSelection {
 final long target;
 long previous;
 volatile boolean opened,ended;boolean handedOff;Runnable afterClose;
 final AtomicBoolean closing=new AtomicBoolean();
 private boolean scoring;
 ReviewSelection(long target){this.target=target;}
 void opened(long previous){this.previous=previous;opened=true;}
 synchronized boolean beginScore(){if(closing.get()||ended||scoring)return false;scoring=true;return true;}
 synchronized void scoreEnded(){scoring=false;notifyAll();}
 boolean beginClose(){return closing.compareAndSet(false,true);}
 // Only the background restoration task calls this; STOP never cancels an accepted score.
 synchronized boolean awaitScore(){boolean interrupted=false;while(scoring){try{wait();}catch(InterruptedException e){interrupted=true;}}return interrupted;}
 boolean restoreWhen(long current){return opened&&previous!=target&&current==target;}
}
