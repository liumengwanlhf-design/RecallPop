package dev.ankigate;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.provider.Settings;
/** Only platform boot/replacement events. No watchdog, hidden wakeup or score replay. */
public final class RecoveryReceiver extends BroadcastReceiver {
 public void onReceive(Context c,Intent i){String action=i.getAction();if(!Intent.ACTION_BOOT_COMPLETED.equals(action)&&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))return;
  android.content.SharedPreferences p=c.getSharedPreferences("gate",0);boolean notification=((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).areNotificationsEnabled()&&(Build.VERSION.SDK_INT<33||c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")==PackageManager.PERMISSION_GRANTED);
  String why=RecoveryPolicy.blocked(p.getBoolean("enabled",false),((UserManager)c.getSystemService(Context.USER_SERVICE)).isUserUnlocked(),Settings.canDrawOverlays(c),notification,p.getBoolean("wake_mode",false),Build.VERSION.SDK_INT<31||((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms());
  if(Intent.ACTION_BOOT_COMPLETED.equals(action))GateService.cancelPlans(c,p);
  if(why!=null){DiagnosticLog.event(c,"恢复未请求："+why);return;}
  try{c.startForegroundService(new Intent(c,GateService.class).setAction(GateService.START));DiagnosticLog.event(c,"系统恢复已请求："+(Intent.ACTION_BOOT_COMPLETED.equals(action)?"开机":"应用更新"));}
  catch(RuntimeException e){DiagnosticLog.event(c,"系统恢复请求失败："+e.getClass().getSimpleName());}
 }
}
