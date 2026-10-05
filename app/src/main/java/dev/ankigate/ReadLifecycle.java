package dev.ankigate;

/** Only a read may be cancelled. The slot stays occupied until both real calls return. */
final class ReadLifecycle {
 static final long TIMEOUT_MS=30000;
 final long started;
 volatile String stage="读取Anki队列",reason="";
 private volatile boolean cancelled;
 private boolean readerEnded,cancelEnded=true;
 ReadLifecycle(long started){this.started=started;}
 boolean expired(long now){return now-started>=TIMEOUT_MS;}
 boolean accepts(long now){return !cancelled&&!expired(now);}
 boolean cancelled(){return cancelled;}
 synchronized boolean cancel(String why){if(cancelled||released())return false;reason=why;cancelled=true;cancelEnded=false;return true;}
 synchronized void readerEnded(){readerEnded=true;}
 synchronized void cancelEnded(){cancelEnded=true;}
 synchronized boolean released(){return readerEnded&&cancelEnded;}
 String status(long now){return (cancelled?"取消读取中（"+reason+"），等待接口结束；":"正在读取：")+stage+"，已等"+RunStatusPolicy.seconds(Math.max(0,now-started))+"秒";}
}
