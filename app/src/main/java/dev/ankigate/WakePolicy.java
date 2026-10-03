package dev.ankigate;

import java.time.*;

final class WakePolicy {
 static final long IDLE_MS=30000;
 static boolean inWindow(LocalTime now,int start,int end){int minute=now.getHour()*60+now.getMinute();return start<end&&minute>=start&&minute<end;}
 static long nextDelay(LocalDateTime now,int start,int end,long randomDelay){
  LocalDateTime due=now.plusNanos(randomDelay*1000000);
  if(inWindow(due.toLocalTime(),start,end))return randomDelay;
  LocalDate date=now.toLocalDate();
  if(now.toLocalTime().toSecondOfDay()>=start*60)date=date.plusDays(1);
  LocalDateTime next=date.atStartOfDay().plusMinutes(start).plusNanos(randomDelay*1000000);
  return Math.max(randomDelay,Duration.between(now,next).toMillis());
 }
 static int minutes(String value){String[] p=value.trim().split(":");if(p.length!=2)throw new IllegalArgumentException("时间格式应为09:00");int h=Integer.parseInt(p[0]),m=Integer.parseInt(p[1]);if(h<0||h>23||m<0||m>59)throw new IllegalArgumentException("时间范围错误");return h*60+m;}
 static String time(int minute){return String.format(java.util.Locale.ROOT,"%02d:%02d",minute/60,minute%60);}
}
