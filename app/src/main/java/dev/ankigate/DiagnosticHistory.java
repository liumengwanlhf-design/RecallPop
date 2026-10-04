package dev.ankigate;
import java.util.*;
/** Bounded operational events, never a heartbeat or card/score log. */
final class DiagnosticHistory {
 static final int LIMIT=60,MAX_EVENT=160;static final long REPEAT_MS=5000;
 final ArrayDeque<String> lines=new ArrayDeque<>();
 DiagnosticHistory(String saved){for(String line:saved.split("\n"))if(!line.isEmpty()&&line.length()<=MAX_EVENT+24){lines.add(line);while(lines.size()>LIMIT)lines.removeFirst();}}
 boolean add(long now,String event){event=event.replace('\n',' ').replace('\r',' ');if(event.length()>MAX_EVENT)event=event.substring(0,MAX_EVENT);String last=lines.peekLast();if(last!=null){int split=last.indexOf('|');try{long time=Long.parseLong(last.substring(0,split));if(last.substring(split+1).equals(event)&&now>=time&&now-time<REPEAT_MS)return false;}catch(RuntimeException ignored){}}
  lines.add(now+"|"+event);while(lines.size()>LIMIT)lines.removeFirst();return true;
 }
 String saved(){return String.join("\n",lines);}
}
