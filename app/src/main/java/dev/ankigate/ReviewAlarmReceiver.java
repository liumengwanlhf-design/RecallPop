package dev.ankigate;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.provider.Settings;

/** Exact alarm delivery validates a single persisted review plan before any FGS start. */
public final class ReviewAlarmReceiver extends BroadcastReceiver {
 static final String ACTION="dev.ankigate.REVIEW_ALARM";
 static int boot(Context c){return Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1);}
 public void onReceive(Context c,Intent intent){
  if(!ACTION.equals(intent.getAction()))return;
  android.content.SharedPreferences p=c.getSharedPreferences("gate",0);String type=intent.getStringExtra("type");long token=intent.getLongExtra("token",0);int epoch=intent.getIntExtra("boot",-1);
  String why=ReviewAlarmPolicy.reject(p.getBoolean("enabled",false),type,p.getString("review_alarm_type",""),token,p.getLong("review_alarm_token",0),epoch,boot(c),p.getLong("review_alarm_elapsed",0),SystemClock.elapsedRealtime());
  if(why==null&&epoch!=p.getInt("review_alarm_boot",-1))why="保存epoch不匹配";
  boolean notification=((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).areNotificationsEnabled()&&(Build.VERSION.SDK_INT<33||c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")==PackageManager.PERMISSION_GRANTED);
  if(why==null)why=RecoveryPolicy.blocked(true,((UserManager)c.getSystemService(Context.USER_SERVICE)).isUserUnlocked(),Settings.canDrawOverlays(c),notification,true,Build.VERSION.SDK_INT<31||((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms());
  if(why==null&&!("usage".equals(type)?p.getBoolean("usage_mode",true):p.getBoolean("wake_mode",false)))why="对应复习模式已关闭";
  if(why!=null){DiagnosticLog.event(c,"系统复习检查拒绝："+why);return;}
  // Consume before starting: a duplicate/stale delivery cannot reconstruct a service.
  p.edit().remove("review_alarm_elapsed").remove("review_alarm_type").remove("review_alarm_token").remove("review_alarm_boot").remove("review_alarm_source").putLong("review_delivery_token",token).putString("review_delivery_type",type).commit();
  DiagnosticLog.event(c,"系统复习闹钟到达："+type+"；请求前台服务；token="+token);
  try{c.startForegroundService(new Intent(c,GateService.class).setAction(ACTION).putExtra("type",type).putExtra("token",token));}
  catch(RuntimeException e){p.edit().remove("review_delivery_token").remove("review_delivery_type").commit();DiagnosticLog.event(c,"系统复习服务请求失败："+e.getClass().getSimpleName());}
 }
 static String status(Context c){android.content.SharedPreferences p=c.getSharedPreferences("gate",0);long at=p.getLong("review_alarm_elapsed",0),now=SystemClock.elapsedRealtime();if(at>now&&p.getInt("review_alarm_boot",-1)==boot(c))return "系统复习计划：约"+RunStatusPolicy.seconds(at-now)+"秒后"+("wake".equals(p.getString("review_alarm_type",""))?"亮屏检查":"使用检查")+"；系统可能推迟，离线不计使用";return "当前没有未来系统复习计划；仅解锁模式没有周期恢复，强行停止需主页重开";}
}
