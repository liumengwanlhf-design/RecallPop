package dev.ankigate;
public final class ReviewAlarmPolicyChecks {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  check(ReviewAlarmPolicy.reject(true,"usage","usage",9,9,2,2,100,100)==null);
  check(ReviewAlarmPolicy.reject(true,"wake","wake",9,9,2,2,100,200)==null);
  check(ReviewAlarmPolicy.reject(false,"usage","usage",9,9,2,2,100,100)!=null);
  check(ReviewAlarmPolicy.reject(true,"other","other",9,9,2,2,100,100)!=null);
  check(ReviewAlarmPolicy.reject(true,"usage","wake",9,9,2,2,100,100)!=null);
  check(ReviewAlarmPolicy.reject(true,"usage","usage",8,9,2,2,100,100)!=null);
  check(ReviewAlarmPolicy.reject(true,"usage","",9,0,2,2,100,100)!=null);
  check(ReviewAlarmPolicy.reject(true,"usage","usage",9,9,2,3,100,100)!=null);
  check(ReviewAlarmPolicy.reject(true,"usage","usage",9,9,-1,-1,100,100)!=null);
  check(ReviewAlarmPolicy.reject(true,"usage","usage",9,9,2,2,100,99)!=null);
  check(ReviewAlarmPolicy.reject(true,"usage","usage",9,9,2,2,0,100)!=null);
  check(ReviewAlarmPolicy.usageDelay(120000,90000)==120000);
  check(ReviewAlarmPolicy.usageDelay(5000,90000)==90000);
  check(ReviewAlarmPolicy.usageDelay(0,0)==1);
  check(ReviewAlarmPolicy.keep("usage","usage",2,2,10000,1,10500));
  check(!ReviewAlarmPolicy.keep("usage","wake",2,2,10000,1,10000));
  check(!ReviewAlarmPolicy.keep("usage","usage",2,3,10000,1,10000));
  check(!ReviewAlarmPolicy.keep("usage","usage",2,2,10000,10000,10000));
  GlobalTriggers live=new GlobalTriggers(40000,120000);live.update(1000,true,true);live.update(6000,true,true);
  GlobalTriggers cold=new GlobalTriggers(live.usedMs,live.intervalMs);cold.update(900000,true,true);
  check(cold.usedMs==45000&&cold.remaining()==75000&&!cold.pending());
  cold.update(905000,true,true);check(cold.usedMs==50000&&cold.remaining()==70000);
  System.out.println("ReviewAlarmPolicyChecks: token/boot/early/duplicate/protection and offline-not-counted passed");
 }
}
