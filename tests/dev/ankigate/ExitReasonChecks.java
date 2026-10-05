package dev.ankigate;
import java.util.*;
import android.content.*;
public final class ExitReasonChecks {
 static void expect(boolean yes,String reason){if(!yes)throw new AssertionError(reason);}
 public static void main(String[] args)throws Exception{
  expect(ExitReasonPolicy.label(4).equals("Java崩溃")&&ExitReasonPolicy.label(5).equals("Native崩溃")&&ExitReasonPolicy.label(6).contains("ANR")&&ExitReasonPolicy.label(3).equals("低内存"),"crash/ANR/memory distinct");
  ExitReasonPolicy.Exit killed=new ExitReasonPolicy.Exit(1000,23,2,9,100);
  expect(killed.event().contains("信号终止")&&!killed.event().contains("崩溃")&&!killed.event().contains("realme")&&killed.event().contains("status=9"),"signal9 has no inferred source");
  expect(ExitReasonPolicy.label(10).contains("旧系统更新")&&ExitReasonPolicy.label(10).contains("不确定"),"old Android USER_REQUESTED ambiguity");
  expect(ExitReasonPolicy.label(999).equals("未知原因"),"future/unknown reason retained");
  expect(killed.description().equals(ExitReasonPolicy.NO_DESCRIPTION)&&ExitReasonPolicy.cleanDescription(null).equals(ExitReasonPolicy.NO_DESCRIPTION)&&ExitReasonPolicy.cleanDescription("\r\n\t\u0000\u202e").equals(ExitReasonPolicy.NO_DESCRIPTION),"legacy constructor/null/empty controls explicitly unavailable");
  expect(ExitReasonPolicy.cleanDescription("  other\n\r\t reason\u0000\u202e detail  ").equals("other reason detail"),"controls and format marks produce one line");
  String longDescription=ExitReasonPolicy.cleanDescription("x".repeat(5000));expect(longDescription.length()==ExitReasonPolicy.DESCRIPTION_LIMIT&&longDescription.endsWith("…"),"bounded description and scan");
  String privateDescription=ExitReasonPolicy.cleanDescription("reason /data/user/0/dev.ankigate/private content://authority/private file:///private https://example.com/private");expect(privateDescription.startsWith("reason ")&&!privateDescription.contains("/data/")&&!privateDescription.contains("://")&&!privateDescription.contains("authority"),"common absolute paths and URIs are hidden");
  expect(ExitReasonPolicy.fresh(List.of(killed,killed),Set.of(),0).size()==1,"same batch duplicate");
  expect(ExitReasonPolicy.fresh(List.of(killed),Set.of(killed.identity()),0).isEmpty(),"repeat launch duplicate");
  expect(ExitReasonPolicy.fresh(List.of(killed),Set.of(),1001).isEmpty(),"evicted old history not reimported");
  expect(ExitReasonPolicy.fresh(List.of(killed),Set.of(),1000).isEmpty(),"eviction timestamp boundary not reimported");
  expect(ExitReasonPolicy.fresh(List.of(killed,new ExitReasonPolicy.Exit(1000,24,2,9,100)),Set.of(),0).size()==2,"same timestamp different PID");
  List<ExitReasonPolicy.Exit> many=new ArrayList<>();for(int i=1;i<=12;i++)many.add(new ExitReasonPolicy.Exit(i,23,3,0,400));
  List<ExitReasonPolicy.Exit> selected=ExitReasonPolicy.fresh(many,Set.of(),0);expect(selected.size()==4&&selected.get(0).time()==12&&selected.get(3).time()==9,"bounded newest report batch");
  DiagnosticHistory history=new DiagnosticHistory("");for(ExitReasonPolicy.Exit e:selected)history.add(2000,e.event());expect(history.lines.size()==4&&history.saved().contains("原退出="),"original exit time stored separately from read time");
  ReviewStatsChecks.Store store=new ReviewStatsChecks.Store();Context c=new Context(){public SharedPreferences getSharedPreferences(String name,int mode){return store;}};
  DiagnosticLog.exits(c,List.of(),"无记录");DiagnosticLog.exits(c,List.of(),"无记录");expect(new DiagnosticHistory(store.getString("diagnostic_log","")).lines.size()==1,"no records once, immutable empty input safe");
  DiagnosticLog.exits(c,many,"有记录");String saved=store.getString("diagnostic_log","");DiagnosticLog.exits(c,many,"有记录");expect(saved.equals(store.getString("diagnostic_log","")),"all scanned identities prevent overflow importing later");
  DiagnosticLog.exits(c,List.of(),"读取失败");saved=store.getString("diagnostic_log","");DiagnosticLog.exits(c,List.of(),"读取失败");expect(saved.equals(store.getString("diagnostic_log","")),"failure once");
  String previousLatest=store.getString("exit_details_cache","");DiagnosticLog.exits(c,List.of(),"无记录");expect(previousLatest.equals(store.getString("exit_details_cache","")),"empty/failure cannot erase cached system details");
  for(int i=13;i<=70;i++)DiagnosticLog.exits(c,List.of(new ExitReasonPolicy.Exit(i,23,2,9,100)),"有记录");
  expect(store.getString("exit_seen","").split("\n").length==32&&new DiagnosticHistory(store.getString("diagnostic_log","")).lines.size()==60,"persisted dedupe and shared history caps");
  saved=store.getString("diagnostic_log","");DiagnosticLog.exits(c,many,"有记录");expect(saved.equals(store.getString("diagnostic_log","")),"evicted history not reimported");
  Thread logs=new Thread(()->{for(int i=71;i<=200;i++)DiagnosticLog.event(c,"事件"+i);});Thread reads=new Thread(()->{for(int i=71;i<=200;i++){DiagnosticLog.exits(c,List.of(new ExitReasonPolicy.Exit(i,23,2,9,100)),"有记录");DiagnosticLog.recent(c);}});logs.start();reads.start();logs.join();reads.join();expect(new DiagnosticHistory(store.getString("diagnostic_log","")).lines.size()==60,"concurrent UI/service/exit writes remain bounded");
  ExitReasonPolicy.Exit described=new ExitReasonPolicy.Exit(1000,42,13,0,125,"system reason detail");DiagnosticLog.exits(c,List.of(described),"有记录");expect(DiagnosticLog.recent(c).contains("system reason detail")&&DiagnosticLog.recent(c).contains("importance=125"),"fresh latest includes description and original numeric fields");
  ReviewStatsChecks.Store upgraded=new ReviewStatsChecks.Store();Context upgradeContext=new Context(){public SharedPreferences getSharedPreferences(String name,int mode){return upgraded;}};upgraded.edit().putString("exit_seen",described.identity()).commit();
  DiagnosticLog.exits(upgradeContext,List.of(described),"有记录");expect(upgraded.getString("diagnostic_log","").isEmpty()&&DiagnosticLog.recent(upgradeContext).contains("system reason detail"),"already-seen exit gains description without replaying log");
  String cached=upgraded.getString("exit_details_cache","");DiagnosticLog.exits(upgradeContext,List.of(described),"有记录");expect(cached.equals(upgraded.getString("exit_details_cache",""))&&upgraded.getString("diagnostic_log","").isEmpty(),"same seen description produces no duplicate events");
  ExitReasonPolicy.Exit updated=new ExitReasonPolicy.Exit(1000,42,13,0,125,"system supplied detail later");DiagnosticLog.exits(upgradeContext,List.of(updated),"有记录");expect(DiagnosticLog.recent(upgradeContext).contains("system supplied detail later")&&upgraded.getString("diagnostic_log","").isEmpty(),"same identity may gain updated human description only");
  DiagnosticLog.exits(upgradeContext,List.of(killed,new ExitReasonPolicy.Exit(999,43,13,0,125,"older detail")),"有记录");expect(ExitReasonPolicy.details(upgraded.getString("exit_details_cache","")).get(0).identity().equals(updated.identity()),"older read cannot overwrite latest timestamp cache");
  for(int i=0;i<70;i++)DiagnosticLog.event(upgradeContext,"新事件"+i);expect(new DiagnosticHistory(upgraded.getString("diagnostic_log","")).lines.size()==60&&DiagnosticLog.recent(upgradeContext).contains("system supplied detail later"),"latest survives sixty-event eviction");
  DiagnosticLog.exits(upgradeContext,List.of(),"读取失败");expect(DiagnosticLog.recent(upgradeContext).contains("system supplied detail later"),"failure retains previous latest cache");
  ExitReasonPolicy.Exit noDescription=new ExitReasonPolicy.Exit(1001,44,13,0,125,null);DiagnosticLog.exits(upgradeContext,List.of(noDescription),"有记录");expect(DiagnosticLog.recent(upgradeContext).contains("系统未提供说明")&&ExitReasonPolicy.details(upgraded.getString("exit_details_cache","")).get(0).identity().equals(noDescription.identity()),"newer null description becomes latest honestly without erasing prior detail");
  expect(ExitReasonPolicy.details(List.of(described,noDescription,killed),"").get(0).identity().equals(noDescription.identity()),"latest selection independent of input ordering/seen");
  ExitReasonPolicy.Exit installed=new ExitReasonPolicy.Exit(1002,45,16,0,125,"package update");upgraded.edit().putString("exit_seen",described.identity()+"\n"+installed.identity()).commit();DiagnosticLog.exits(upgradeContext,List.of(installed,updated),"有记录");List<ExitReasonPolicy.Exit> details=ExitReasonPolicy.details(upgraded.getString("exit_details_cache",""));expect(details.size()==4&&details.get(0).identity().equals(installed.identity())&&DiagnosticLog.recent(upgradeContext).contains("system supplied detail later"),"install exit cannot hide already-seen prior reason13 detail");
  cached=upgraded.getString("exit_details_cache","");DiagnosticLog.exits(upgradeContext,List.of(killed),"有记录");expect(cached.equals(upgraded.getString("exit_details_cache","")),"old snapshot preserves four newest details");
  List<ExitReasonPolicy.Exit> bounded=ExitReasonPolicy.details(many,"");expect(bounded.size()==4&&bounded.get(0).time()==12&&bounded.get(3).time()==9,"detail cap and newest-first order independent of fresh event cap");expect(ExitReasonPolicy.details(ExitReasonPolicy.saveDetails(bounded)).equals(bounded),"detail serialization roundtrip");
  ExitReasonPolicy.Exit pipes=new ExitReasonPolicy.Exit(1003,46,13,0,125,"system | wording");expect(ExitReasonPolicy.details(pipes.saved()).get(0).description().equals("system | wording"),"description delimiters remain human text");
  System.out.println("exit mapping, original time, bounded dedupe/log, description cleaning, seen migration and persistent four-detail cache checks passed");
 }
}
