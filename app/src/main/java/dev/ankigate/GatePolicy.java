package dev.ankigate;

final class GatePolicy {
 static final long PROTECTION_MS = 90000;
 static final int DAILY_LIMIT = 150;
 static boolean allows(long now, long protectedUntil, int count) {
  return count < DAILY_LIMIT && now>=protectedUntil;
 }
 static boolean allowsRequest(long now,long last,int count,boolean debug){
  return count<DAILY_LIMIT&&(debug||allows(now,last,count));
 }
}
