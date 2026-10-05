package dev.ankigate;
import android.app.*;
import android.content.Context;
import java.util.*;

final class ExitDiagnostics {
 static void read(Context c){
  try{
   ActivityManager manager=c.getSystemService(ActivityManager.class);
   if(manager==null){DiagnosticLog.exits(c,List.of(),"退出诊断不可用：系统服务不可用");return;}
   List<ExitReasonPolicy.Exit> exits=new ArrayList<>();
   for(ApplicationExitInfo e:manager.getHistoricalProcessExitReasons(c.getPackageName(),0,ExitReasonPolicy.FETCH_LIMIT)){
    if(c.getPackageName().equals(e.getProcessName()))exits.add(new ExitReasonPolicy.Exit(e.getTimestamp(),e.getPid(),e.getReason(),e.getStatus(),e.getImportance(),e.getDescription()));
   }
   DiagnosticLog.exits(c,exits,exits.isEmpty()?"退出诊断：系统未提供本应用主进程退出记录（不能判断此前退出原因）":"有记录");
  }catch(RuntimeException e){try{DiagnosticLog.exits(c,List.of(),"退出诊断读取失败："+e.getClass().getSimpleName()+"（不影响运行）");}catch(RuntimeException ignored){}}
 }
}
