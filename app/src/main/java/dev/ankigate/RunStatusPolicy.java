package dev.ankigate;
/** Text from current process state. No persisted heartbeat is treated as alive. */
final class RunStatusPolicy {
 static String primary(boolean running,boolean enabled,boolean permission,boolean own,boolean gate,long protection,int count,boolean interactive,boolean unlocked){
  if(!running)return enabled?"未检测到运行实例；正在等待或未运行，原因待确认":"已停止（开关停用）";
  if(!enabled)return "服务正在停止";if(!permission)return "运行实例存在，关键许可不满足";
  if(own)return "运行中；本页/主动复习/更新期间暂停，离开后才累计";
  if(gate)return "运行中；正在取卡或显示复习，累计暂停";if(count>=150)return "运行中；今日自动展示已达150，等待次日";
  if(protection>0)return "运行中；结束保护剩余"+seconds(protection)+"秒";
  if(!interactive||!unlocked)return "运行中；熄屏或锁屏，使用累计暂停";return "运行中；亮屏已解锁，按启用模式检查";
 }
 static long seconds(long ms){return Math.max(0,(ms+999)/1000);}
}
