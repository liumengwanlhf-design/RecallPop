package dev.ankigate;
import android.content.Context;
import android.content.SharedPreferences;
import java.time.*;
import java.util.*;
final class DiagnosticLog {
 static synchronized void event(Context c,String event){SharedPreferences p=c.getSharedPreferences("gate",0);DiagnosticHistory h=new DiagnosticHistory(p.getString("diagnostic_log",""));if(h.add(System.currentTimeMillis(),event))p.edit().putString("diagnostic_log",h.saved()).apply();}
 static synchronized void exits(Context c,List<ExitReasonPolicy.Exit> exits,String result){
  SharedPreferences p=c.getSharedPreferences("gate",0);DiagnosticHistory h=new DiagnosticHistory(p.getString("diagnostic_log",""));
  LinkedHashSet<String> seen=new LinkedHashSet<>(Arrays.asList(p.getString("exit_seen","").split("\n")));seen.remove("");long floor=p.getLong("exit_floor",0);
  List<ExitReasonPolicy.Exit> fresh=ExitReasonPolicy.fresh(exits,seen,floor);Collections.reverse(fresh);long now=System.currentTimeMillis();for(ExitReasonPolicy.Exit e:fresh)h.add(now,e.event());
  for(ExitReasonPolicy.Exit e:exits)if(e.time()>floor)seen.add(e.identity());
  List<String> ordered=new ArrayList<>(seen);ordered.sort(Comparator.comparingLong(id->Long.parseLong(id.substring(0,id.indexOf(':')))));seen=new LinkedHashSet<>(ordered);
  while(seen.size()>ExitReasonPolicy.SEEN_LIMIT){String oldest=seen.iterator().next();floor=Math.max(floor,Long.parseLong(oldest.substring(0,oldest.indexOf(':'))));seen.remove(oldest);}
  if(!"有记录".equals(result)&&!result.equals(p.getString("exit_result","")))h.add(now,result);
  // Independent of seen/event eviction; retain four newest details across old/empty reads.
  String details=ExitReasonPolicy.saveDetails(ExitReasonPolicy.details(exits,p.getString("exit_details_cache","")));
  // Commit from the worker before it exits; the same lock protects service/UI log writes.
  p.edit().putString("exit_details_cache",details).putString("diagnostic_log",h.saved()).putString("exit_seen",String.join("\n",seen)).putLong("exit_floor",floor).putString("exit_result",result).commit();
 }
 static synchronized String recent(Context c){SharedPreferences p=c.getSharedPreferences("gate",0);DiagnosticHistory h=new DiagnosticHistory(p.getString("diagnostic_log",""));StringBuilder text=new StringBuilder("仅运行事件与系统退出记录；历史记录不能证明服务现在存活。\n退出原因是系统报告，下次进程启动时读取；系统可能缺失或覆盖记录，信号终止不能确定来源。\n\n");List<ExitReasonPolicy.Exit> details=ExitReasonPolicy.details(p.getString("exit_details_cache",""));if(!details.isEmpty()){text.append("最近读取的系统退出详情（缓存，非存活证明）：\n");for(ExitReasonPolicy.Exit e:details)text.append(e.report()).append("\n\n");}java.util.Iterator<String> it=h.lines.descendingIterator();while(it.hasNext()){String line=it.next();int split=line.indexOf('|');try{text.append(Instant.ofEpochMilli(Long.parseLong(line.substring(0,split))).atZone(ZoneId.systemDefault()).toLocalDateTime().toString().replace('T',' ')).append("  ").append(line.substring(split+1)).append('\n');}catch(RuntimeException ignored){}}return text.toString();}
}
