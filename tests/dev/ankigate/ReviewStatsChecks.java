package dev.ankigate;
import android.content.*;
import java.util.*;
public final class ReviewStatsChecks {
 static class Store implements SharedPreferences {
  final Map<String,Object> values=new HashMap<>();
  public long getLong(String k,long d){return (long)values.getOrDefault(k,d);}
  public int getInt(String k,int d){return (int)values.getOrDefault(k,d);}
  public String getString(String k,String d){return (String)values.getOrDefault(k,d);}
  public Editor edit(){return new Editor(){final Map<String,Object> changes=new HashMap<>();public Editor putLong(String k,long v){changes.put(k,v);return this;}public Editor putInt(String k,int v){changes.put(k,v);return this;}public Editor putString(String k,String v){changes.put(k,v);return this;}public void apply(){values.putAll(changes);}};}
 }
 public static void main(String[] args)throws Exception{
  Store p=new Store();Context c=new Context(){public SharedPreferences getSharedPreferences(String n,int m){return p;}};
  if(ReviewStats.today(c)!=0)throw new AssertionError("no backfill");
  p.values.put("complete_day","2000-01-01");p.values.put("complete_count",100);
  Thread a=new Thread(()->{for(int i=0;i<1000;i++)ReviewStats.confirmed(c);});Thread b=new Thread(()->{for(int i=0;i<1000;i++)ReviewStats.confirmed(c);});a.start();b.start();a.join();b.join();
  if(ReviewStats.today(c)!=2000)throw new AssertionError("concurrent confirmations lost or old day backfilled");
  p.values.put("protected_until",Long.MAX_VALUE);ReviewStats.finished(c);
  if(p.getLong("protected_until",0)!=Long.MAX_VALUE||ReviewStats.today(c)!=2000)throw new AssertionError("exit shortens protection or counts completion");
  System.out.println("statistics concurrency, rollover, no backfill, unanswered exit and maximum protection passed");
 }
}
