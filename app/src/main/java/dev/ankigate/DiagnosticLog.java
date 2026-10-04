package dev.ankigate;
import android.content.Context;
import android.content.SharedPreferences;
import java.time.*;
final class DiagnosticLog {
 static synchronized void event(Context c,String event){SharedPreferences p=c.getSharedPreferences("gate",0);DiagnosticHistory h=new DiagnosticHistory(p.getString("diagnostic_log",""));if(h.add(System.currentTimeMillis(),event))p.edit().putString("diagnostic_log",h.saved()).apply();}
 static synchronized String recent(Context c){DiagnosticHistory h=new DiagnosticHistory(c.getSharedPreferences("gate",0).getString("diagnostic_log",""));StringBuilder text=new StringBuilder("仅运行事件；历史记录不能证明服务现在存活。\n\n");java.util.Iterator<String> it=h.lines.descendingIterator();while(it.hasNext()){String line=it.next();int split=line.indexOf('|');try{text.append(Instant.ofEpochMilli(Long.parseLong(line.substring(0,split))).atZone(ZoneId.systemDefault()).toLocalDateTime().toString().replace('T',' ')).append("  ").append(line.substring(split+1)).append('\n');}catch(RuntimeException ignored){}}return text.toString();}
}
