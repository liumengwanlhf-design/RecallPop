package dev.ankigate;
public final class GlobalTriggerChecks {
 static int n;static void check(boolean b,String why){n++;if(!b)throw new AssertionError(why);}
 public static void main(String[] args){
  int[] samples={0};java.util.function.LongSupplier random=()->{samples[0]++;return 120000;};
  GlobalTriggers t=new GlobalTriggers(0,180000,random);
  check(samples[0]==0,"rebind must not resample");
  t.update(0,true,true);t.update(60000,false,true);t.update(900000,false,true);
  check(t.usedMs==60000,"screen off/manual/fetch/display pause excludes time");
  GlobalTriggers r=new GlobalTriggers(t.usedMs,t.intervalMs,random);r.update(2000000,true,true);r.update(2119999,true,true);
  check(r.usedMs==179999&&!r.pending(),"saved round excludes offline time");r.update(2120000,true,true);
  check(r.usagePending,"exact timer boundary");
  check(!GatePolicy.allows(89999,90000,0)&&r.usagePending,"timer remains pending in protection");
  check(GatePolicy.allows(90000,90000,0)&&r.usagePending,"same-app protection deadline eligible");
  r.consumed(2120000);check(samples[0]==1&&r.intervalMs==120000&&!r.pending(),"one consume one new sample");
  r.update(2120000,false,false);r.update(9999999,false,false);check(r.usedMs==0&&!r.pending(),"disabled mode pauses");
  r.unlocked(GatePolicy.allows(89999,90000,0));check(!r.unlockPending,"protected priority dropped");
  check(!r.pending()&&GatePolicy.allows(90000,90000,0),"no queued priority after protection");
  r.unlocked(true);r.clearUnlock();check(!r.pending(),"priority consumed before asynchronous fetch");
  check(!GatePolicy.allows(999999,0,150)&&GatePolicy.allows(999999,0,0),"new-day count zero restores quota");
  check(GatePolicy.allowsRequest(1,90000,149,true)&&!GatePolicy.allowsRequest(1,90000,150,true),"debug bypasses protection, never daily cap");
  GlobalTriggers fresh=new GlobalTriggers(999999,0,random);check(fresh.usedMs==120000,"legacy used time clamped");
  for(int i=0;i<1000;i++){GlobalTriggers v=new GlobalTriggers(0,0);check(v.intervalMs>=120000&&v.intervalMs<=300000,"random range");}
  System.out.println(n+" global transition checks passed");
 }
}
