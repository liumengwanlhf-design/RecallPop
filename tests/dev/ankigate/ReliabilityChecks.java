package dev.ankigate;
public final class ReliabilityChecks {
 static void expect(boolean yes,String reason){if(!yes)throw new AssertionError(reason);}
 public static void main(String[] args){
  expect(RecoveryPolicy.blocked(false,true,true,true,true,true)!=null,"STOP cannot recover");
  expect(RecoveryPolicy.blocked(true,false,true,true,true,true)!=null,"CE locked cannot recover");
  expect(RecoveryPolicy.blocked(true,true,false,true,false,true)!=null,"missing overlay");
  expect(RecoveryPolicy.blocked(true,true,true,false,false,true)!=null,"missing notification");
  expect(RecoveryPolicy.blocked(true,true,true,true,true,false)!=null,"wake needs exact grant");
  expect(RecoveryPolicy.blocked(true,true,true,true,false,false)==null,"ordinary mode need not exact");
  expect(RecoveryPolicy.blocked(true,true,true,true,true,true)==null,"CE unlocked allows recovery even if lock screen returned");
  expect(RecoveryPolicy.keepAlarm(true,true,500,100,3,3),"same boot future alarm preserved");
  expect(!RecoveryPolicy.keepAlarm(false,true,500,100,3,3),"STOP cancels alarm");
  expect(!RecoveryPolicy.keepAlarm(true,false,500,100,3,3),"wake disabled cancels alarm");
  expect(!RecoveryPolicy.keepAlarm(true,true,500,100,3,4),"new boot discards old elapsed even if future-looking");
  expect(!RecoveryPolicy.keepAlarm(true,true,100,100,3,3),"past plan not restored");
  expect(!RecoveryPolicy.keepAlarm(true,true,500,100,-1,-1),"unknown boot cannot prove same elapsed epoch");
  DiagnosticHistory h=new DiagnosticHistory("");expect(h.add(10000,"取卡"),"first record");expect(!h.add(11000,"取卡"),"repeat throttled");expect(h.add(15000,"取卡"),"repeat after interval");
  for(int i=0;i<100;i++)h.add(20000+i,"事件"+i);expect(h.lines.size()==60,"history bounded");expect(!h.saved().contains("事件0\n"),"old events evicted");
  h.add(30000,"x".repeat(500)+"\nprivate");expect(h.lines.peekLast().length()<=DiagnosticHistory.MAX_EVENT+24&&!h.lines.peekLast().contains("\n"),"line and length bounded");expect(new DiagnosticHistory(h.saved()).lines.size()==60,"bounded persisted restore");
  expect(RunStatusPolicy.primary(false,true,true,false,false,0,0,true,true).contains("未检测"),"enabled is not alive");
  expect(RunStatusPolicy.primary(false,false,true,false,false,0,0,true,true).contains("已停止"),"disabled honest status");
  expect(RunStatusPolicy.primary(true,true,true,true,false,5000,0,true,true).contains("暂停"),"own page pauses regardless of protection");
  expect(RunStatusPolicy.primary(true,true,false,false,false,0,0,true,true).contains("许可"),"missing grant explicit");
  expect(RunStatusPolicy.primary(true,true,true,false,true,0,0,true,true).contains("取卡"),"gate current state");
  expect(RunStatusPolicy.primary(true,true,true,false,false,0,150,true,true).contains("150"),"daily cap");
  expect(RunStatusPolicy.primary(true,true,true,false,false,90000,0,true,true).contains("90秒"),"protection boundary");
  expect(RunStatusPolicy.primary(true,true,true,false,false,0,0,false,false).contains("锁屏"),"locked paused");
  expect(RunStatusPolicy.seconds(1)==1&&RunStatusPolicy.seconds(1000)==1&&RunStatusPolicy.seconds(1001)==2,"check countdown ceiling");
  System.out.println("recovery, boot epoch, bounded diagnostics and honest runtime status boundaries passed");
 }
}
