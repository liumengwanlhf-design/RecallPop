package dev.ankigate;

final class GlobalTriggers {
 static final long MIN_INTERVAL_MS=120000, MAX_INTERVAL_MS=300000;
 final java.util.function.LongSupplier random;
 long intervalMs;
 long usedMs;
 long runningSince=-1;
 boolean unlockPending;
 boolean usagePending;
 GlobalTriggers(long savedUsage,long savedInterval){this(savedUsage,savedInterval,()->java.util.concurrent.ThreadLocalRandom.current().nextLong(MIN_INTERVAL_MS,MAX_INTERVAL_MS+1));}
 GlobalTriggers(long savedUsage,long savedInterval,java.util.function.LongSupplier random){this.random=random;intervalMs=savedInterval>=MIN_INTERVAL_MS&&savedInterval<=MAX_INTERVAL_MS?savedInterval:random.getAsLong();usedMs=Math.max(0,Math.min(intervalMs,savedUsage));}
 void update(long now,boolean active,boolean usageEnabled){
  if(runningSince>=0)usedMs=Math.min(intervalMs,usedMs+Math.max(0,now-runningSince));
  runningSince=active&&usageEnabled?now:-1;
  usagePending=usageEnabled&&usedMs>=intervalMs;
 }
 void unlocked(boolean enabled){if(enabled)unlockPending=true;}
 boolean pending(){return unlockPending||usagePending;}
 long remaining(){return Math.max(0,intervalMs-usedMs);}
 void clearUnlock(){unlockPending=false;}
 void consumed(long now){intervalMs=random.getAsLong();usedMs=0;unlockPending=false;usagePending=false;if(runningSince>=0)runningSince=now;}
}
