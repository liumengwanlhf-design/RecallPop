package dev.ankigate;
import java.time.*;
import java.util.*;

/** Public Android reason values and bounded human-readable system text; never a trace. */
final class ExitReasonPolicy {
 static final int FETCH_LIMIT=12,BATCH_LIMIT=4,SEEN_LIMIT=32;
 static final int DESCRIPTION_LIMIT=320,DETAIL_LIMIT=4;
 static final String NO_DESCRIPTION="系统未提供说明";
 record Exit(long time,int pid,int reason,int status,int importance,String description){
  Exit{description=cleanDescription(description);}
  Exit(long time,int pid,int reason,int status,int importance){this(time,pid,reason,status,importance,null);}
  String identity(){return time+":"+pid+":"+reason+":"+status;}
  String event(){return "系统退出报告：原退出="+Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDateTime().toString().replace('T',' ')+"；"+label(reason)+"；reason="+reason+" status="+status+" pid="+pid+" importance="+importance;}
  String report(){return event()+"\n系统说明（仅供人阅读，格式不稳定）："+description;}
  String saved(){return time+"|"+pid+"|"+reason+"|"+status+"|"+importance+"|"+description;}
 }
 static String cleanDescription(String value){
  if(value==null)return NO_DESCRIPTION;
  StringBuilder line=new StringBuilder();boolean space=false;
  for(int i=0;i<Math.min(value.length(),4096);){int cp=value.codePointAt(i);i+=Character.charCount(cp);int type=Character.getType(cp);
   if(Character.isWhitespace(cp)||Character.isISOControl(cp)||type==Character.FORMAT||type==Character.SURROGATE){space=line.length()>0;continue;}
   if(space){line.append(' ');space=false;}line.appendCodePoint(cp);
  }
  // System descriptions may contain file locations; retain the cause wording, not paths/URIs.
  String text=line.toString().replaceAll("(?i:content|file|https?)://\\S+|(?<![\\p{L}\\p{N}])/\\S+","[路径或地址已隐藏]").trim();
  if(text.isEmpty())return NO_DESCRIPTION;
  if(text.length()>DESCRIPTION_LIMIT){int end=DESCRIPTION_LIMIT-1;if(Character.isHighSurrogate(text.charAt(end-1)))end--;text=text.substring(0,end)+"…";}
  return text;
 }
 static List<Exit> details(String saved){
  List<Exit> values=new ArrayList<>();for(String line:saved.split("\n",DETAIL_LIMIT+1)){if(values.size()==DETAIL_LIMIT)break;String[] fields=line.split("\\|",6);
   try{if(fields.length==6)values.add(new Exit(Long.parseLong(fields[0]),Integer.parseInt(fields[1]),Integer.parseInt(fields[2]),Integer.parseInt(fields[3]),Integer.parseInt(fields[4]),fields[5]));}catch(RuntimeException ignored){}
  }return values;
 }
 static List<Exit> details(List<Exit> exits,String saved){
  Map<String,Exit> merged=new HashMap<>();for(Exit e:details(saved))merged.put(e.identity(),e);for(Exit e:exits)merged.put(e.identity(),e);
  List<Exit> values=new ArrayList<>(merged.values());values.sort(Comparator.comparingLong(Exit::time).thenComparing(Exit::identity).reversed());return new ArrayList<>(values.subList(0,Math.min(DETAIL_LIMIT,values.size())));
 }
 static String saveDetails(List<Exit> exits){StringJoiner saved=new StringJoiner("\n");for(Exit e:exits)saved.add(e.saved());return saved.toString();}
 static String label(int reason){return switch(reason){
  case 1->"自行退出";case 2->"信号终止（来源不确定）";case 3->"低内存";
  case 4->"Java崩溃";case 5->"Native崩溃";case 6->"ANR（无响应）";
  case 7->"初始化失败";case 8->"权限变更";case 9->"资源过量";
  case 10->"用户请求/旧系统更新等（来源不确定）";case 11->"用户停止";
  case 12->"依赖进程退出";case 13->"其他系统原因";case 14->"冻结进程";
  case 15->"包状态变更";case 16->"包更新";default->"未知原因";
 };}
 static List<Exit> fresh(List<Exit> exits,Set<String> seen,long floor){
  List<Exit> fresh=new ArrayList<>();Set<String> batch=new HashSet<>();
  for(Exit e:exits)if(e.time>floor&&!seen.contains(e.identity())&&batch.add(e.identity()))fresh.add(e);
  fresh.sort(Comparator.comparingLong(Exit::time).reversed());
  return new ArrayList<>(fresh.subList(0,Math.min(BATCH_LIMIT,fresh.size())));
 }
}
