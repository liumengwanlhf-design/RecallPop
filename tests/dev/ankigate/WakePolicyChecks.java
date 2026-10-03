package dev.ankigate;
import java.time.*;
public final class WakePolicyChecks {
 static int n;static void check(boolean value,String why){n++;if(!value)throw new AssertionError(why);}
 public static void main(String[] args){
  check(!WakePolicy.inWindow(LocalTime.of(8,59,59),540,1380),"before 09:00 excluded");
  check(WakePolicy.inWindow(LocalTime.of(9,0),540,1380),"09:00 included");
  check(WakePolicy.inWindow(LocalTime.of(22,59,59),540,1380),"before 23:00 included");
  check(!WakePolicy.inWindow(LocalTime.of(23,0),540,1380),"23:00 excluded");
  LocalDateTime noon=LocalDateTime.of(2026,10,3,12,0);
  check(WakePolicy.nextDelay(noon,540,1380,300000)==300000,"normal 5-minute delay");
  check(WakePolicy.nextDelay(noon,540,1380,600000)==600000,"normal 10-minute delay");
  LocalDateTime late=LocalDateTime.of(2026,10,3,22,58);
  long wait=WakePolicy.nextDelay(late,540,1380,300000);
  check(late.plusNanos(wait*1000000).equals(LocalDateTime.of(2026,10,4,9,5)),"crossing end schedules next window, no catch-up at 23:03");
  LocalDateTime early=LocalDateTime.of(2026,10,3,8,0);
  wait=WakePolicy.nextDelay(early,540,1380,600000);
  check(early.plusNanos(wait*1000000).equals(LocalDateTime.of(2026,10,3,9,10)),"before start defers to window plus sampled delay");
  check(WakePolicy.minutes("09:00")==540&&WakePolicy.time(1380).equals("23:00"),"settings parse and render");
  boolean bad=false;try{WakePolicy.minutes("25:00");}catch(IllegalArgumentException e){bad=true;}check(bad,"invalid settings rejected");
  check(WakePolicy.IDLE_MS==30000,"initial no-touch exit 30 seconds");
  System.out.println(n+" wake time-window/settings/idle boundary checks passed");
 }
}
