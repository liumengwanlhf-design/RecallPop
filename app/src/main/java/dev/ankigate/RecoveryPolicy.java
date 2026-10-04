package dev.ankigate;
final class RecoveryPolicy {
 static String blocked(boolean enabled,boolean userUnlocked,boolean overlay,boolean notification,boolean wake,boolean exact){
  if(!enabled)return "已停用，不恢复";if(!userUnlocked)return "开机后尚未完成凭据解锁，需打开主页恢复";
  if(!overlay||!notification)return "缺悬浮窗或通知许可，需主页授权";if(wake&&!exact)return "亮屏模式缺精确闹钟许可，需主页授权";return null;
 }
 static boolean keepAlarm(boolean enabled,boolean wake,long at,long now,int savedBoot,int currentBoot){return enabled&&wake&&at>now&&currentBoot>=0&&savedBoot==currentBoot;}
}
