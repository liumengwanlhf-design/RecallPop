package dev.ankigate;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.view.WindowManager;
import java.time.*;
import java.util.concurrent.*;

/** User-started foreground owner of automatic review, ordinary overlay and wake alarms. */
public final class GateService extends Service {
 static final String START="dev.ankigate.START",STOP="dev.ankigate.STOP",WAKE_ALARM="dev.ankigate.WAKE_ALARM",TEST_WAKE="dev.ankigate.TEST_WAKE_DELAY";
 static GateService instance;
 final ExecutorService worker=Executors.newSingleThreadExecutor();
 final Handler main=new Handler(Looper.getMainLooper());
 final Runnable boundary=this::reevaluate,stateCheck=this::checkSystemState;
 final Runnable launchTimeout=this::wakeLaunchTimedOut;
 SharedPreferences prefs;WindowManager windows;PowerManager power;KeyguardManager keyguard;AlarmManager alarms;GlobalTriggers triggers;
 ReviewPane pane;Future<?> fetchFuture;PendingWake pendingWake;WakeReviewActivity wakeActivity;
 long generation;boolean fetching,fetchIsWake,destroyed,registered,debugRegistered,observedActive,observedInteractive,stateObserved;
 long lastNotificationElapsed;String lastNotification="",currentResult="";
 static final class PendingWake {
  final long token;final AnkiApi.Card card;final MediaAccess media;final boolean debug;final PowerManager.WakeLock cpu;boolean shown;
  PendingWake(long token,AnkiApi.Card card,MediaAccess media,boolean debug,PowerManager.WakeLock cpu){this.token=token;this.card=card;this.media=media;this.debug=debug;this.cpu=cpu;}
 }
 void wakeLaunchTimedOut(){if(pendingWake!=null&&!pendingWake.shown){error("亮屏卡页面未确认显示，可能被系统限制；本次不计展示");closeWake("launch-timeout");scheduleWake();reevaluate();}}
 final SharedPreferences.OnSharedPreferenceChangeListener changed=(p,key)->{
  if("protected_until".equals(key)){main.post(this::reevaluate);return;}
  if("enabled".equals(key)||"unlock_mode".equals(key)||"usage_mode".equals(key)||"deck".equals(key)||"media".equals(key)||"wake_mode".equals(key)||key.startsWith("wake_config_"))main.post(()->{
   if(destroyed)return;
   if(!enabled()){stopAll();return;}
   invalidateFetch();if(!unlockMode())triggers.clearUnlock();
   if(!wakeMode()){cancelAlarm();closeWake("wake-disabled");}else{cancelAlarm();scheduleWake();}
   reevaluate();
  });
 };
 final BroadcastReceiver screen=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
  log("screen action="+i.getAction());checkSystemState();
  if(Intent.ACTION_USER_PRESENT.equals(i.getAction())){offerUnlock();reevaluate();}
 }};
 final BroadcastReceiver debug=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
  boolean wake="dev.ankigate.TEST_WAKE".equals(i.getAction());
  if(!wake&&!"dev.ankigate.TEST_TRIGGER".equals(i.getAction()))return;
  // Debug bypasses the 90-second protection; wake debug additionally bypasses the time window.
  boolean allowed=wake?wakeAllowed(true):ordinaryAllowed(true);
  log("debug wake="+wake+" allowed="+allowed+" interactive="+power.isInteractive()+" locked="+keyguard.isKeyguardLocked()+" count="+todayCount());
  if(allowed)fetch(wake,true);
 }};
 @Override public void onCreate(){
  super.onCreate();prefs=getSharedPreferences("gate",0);power=(PowerManager)getSystemService(POWER_SERVICE);keyguard=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
  alarms=(AlarmManager)getSystemService(ALARM_SERVICE);windows=(WindowManager)getSystemService(WINDOW_SERVICE);
  foregroundNow();instance=this;triggers=new GlobalTriggers(prefs.getLong("used_ms",0),prefs.getLong("interval_ms",0));
  prefs.registerOnSharedPreferenceChangeListener(changed);
  IntentFilter f=new IntentFilter();f.addAction(Intent.ACTION_SCREEN_ON);f.addAction(Intent.ACTION_SCREEN_OFF);f.addAction(Intent.ACTION_USER_PRESENT);
  if(Build.VERSION.SDK_INT>=33)registerReceiver(screen,f,Context.RECEIVER_EXPORTED);else registerReceiver(screen,f);registered=true;
  if((getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0){
   IntentFilter d=new IntentFilter("dev.ankigate.TEST_TRIGGER");d.addAction("dev.ankigate.TEST_WAKE");
   if(Build.VERSION.SDK_INT>=33)registerReceiver(debug,d,"android.permission.DUMP",main,Context.RECEIVER_EXPORTED);else registerReceiver(debug,d,"android.permission.DUMP",main);debugRegistered=true;
  }
  // Sticky reconstruction does not replay an old unlock, and never counts offline time.
  if(power.isInteractive()||!RecoveryPolicy.keepAlarm(enabled(),wakeMode(),prefs.getLong("wake_alarm_elapsed",0),SystemClock.elapsedRealtime(),prefs.getInt("wake_alarm_boot",-1),bootCount()))cancelAlarm();
  DiagnosticLog.event(this,"前台服务已创建");log("created foreground enabled="+enabled());checkSystemState();reevaluate();
 }
 void foregroundNow(){
  NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
  manager.createNotificationChannel(new NotificationChannel("review","复习介入正在运行",NotificationManager.IMPORTANCE_LOW));
  Notification note=notification("正在启动；实际运行状态将在服务创建后更新");
  if(Build.VERSION.SDK_INT>=34)startForeground(6,note,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(6,note);
 }
 Notification notification(String text){
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,GateService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  return new Notification.Builder(this,"review").setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("Anki复习介入").setContentText(text.split("\n")[0]).setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true).addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"停止介入",stop).build()).build();
 }
 void refreshNotification(){if(destroyed||triggers==null)return;long elapsed=SystemClock.elapsedRealtime();if(elapsed-lastNotificationElapsed<15000)return;String text=describe();if(!text.equals(lastNotification)){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(6,notification(text));lastNotification=text;}lastNotificationElapsed=elapsed;}
 int bootCount(){return Settings.Global.getInt(getContentResolver(),Settings.Global.BOOT_COUNT,-1);}
 String describe(){
  long now=System.currentTimeMillis(),elapsed=SystemClock.elapsedRealtime(),protection=Math.max(0,prefs.getLong("protected_until",0)-now);boolean permission=Settings.canDrawOverlays(this)&&notificationsReady();
  String text=RunStatusPolicy.primary(!destroyed,enabled(),permission,ownVisible(),hasGate(),protection,todayCount(),power.isInteractive(),!keyguard.isKeyguardLocked());
  text+="\n模式：解锁"+(unlockMode()?"开":"关")+" / 使用计时"+(usageMode()?"开":"关")+" / 亮屏"+(wakeMode()?"开":"关")+"；自动展示 "+todayCount()+" / 150";
  text+="\n许可：悬浮窗"+(Settings.canDrawOverlays(this)?"有":"缺")+" / 通知"+(notificationsReady()?"有":"缺")+" / 精确闹钟"+(exactReady()?"有":"缺");
  if(checkSelfPermission(AnkiApi.PERMISSION)!=android.content.pm.PackageManager.PERMISSION_GRANTED||prefs.getLong("deck",-1)<0||!prefs.contains("media"))text+="\nAnki许可、牌组或媒体目录未配置；无法保证取卡";
  if(enabled()&&!hasGate()&&todayCount()<GatePolicy.DAILY_LIMIT){
   if(usageMode()&&power.isInteractive()&&!keyguard.isKeyguardLocked()){long used=triggers.usedMs+(triggers.runningSince>=0?Math.max(0,elapsed-triggers.runningSince):0),remaining=Math.max(0,triggers.intervalMs-used);text+="\n"+(ownVisible()?"离开本页后累计":"预计")+RunStatusPolicy.seconds(Math.max(remaining,protection))+"秒到下次使用检查";}
   else if(!power.isInteractive()&&wakeMode()){long at=prefs.getLong("wake_alarm_elapsed",0);text+="\n"+(at>elapsed?"计划约"+RunStatusPolicy.seconds(at-elapsed)+"秒后亮屏检查（系统可能推迟）":"暂无未来亮屏计划；投递/许可原因需诊断确认");}
   else if(unlockMode())text+="\n等待保护结束后的真实解锁事件";
  }
  if(!currentResult.isEmpty())text+="\n本轮结果："+currentResult;
  return text+"\n检查不保证弹卡：仍须有到期卡、许可和媒体可读";
 }
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  String action=intent==null?"sticky":intent.getAction();log("start action="+action);
  if(STOP.equals(action)||!enabled()){stopAll();return START_NOT_STICKY;}
  if(!Settings.canDrawOverlays(this)||!notificationsReady()){error("请从主界面授权悬浮窗和通知，再启动服务");stopAll();return START_NOT_STICKY;}
  if(WAKE_ALARM.equals(action)){DiagnosticLog.event(this,"锁屏闹钟已到达服务");cancelAlarm();if(wakeAllowed(false))fetch(true,false);else{DiagnosticLog.event(this,"锁屏闹钟未取卡：当前屏幕/模式/许可/保护/配额/复习状态不满足");log("alarm rejected interactive="+power.isInteractive()+" hasGate="+hasGate());scheduleWake();}}
  else if(TEST_WAKE.equals(action))scheduleTestWake();
  else{reevaluate();scheduleWake();}
  refreshNotification();return START_STICKY;
 }
 boolean notificationsReady(){return ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled()&&(Build.VERSION.SDK_INT<33||checkSelfPermission("android.permission.POST_NOTIFICATIONS")==android.content.pm.PackageManager.PERMISSION_GRANTED);}
 boolean enabled(){return prefs.getBoolean("enabled",false);}
 boolean unlockMode(){return prefs.getBoolean("unlock_mode",true);}
 boolean usageMode(){return prefs.getBoolean("usage_mode",true);}
 boolean wakeMode(){return prefs.getBoolean("wake_mode",false);}
 boolean ownVisible(){return MainActivity.visible||MainActivity.configuring||ReviewActivity.inProgress||UpdateActivity.inProgress||UpdateActivity.configuring;}
 boolean active(){return enabled()&&power.isInteractive()&&!keyguard.isKeyguardLocked();}
 public boolean hasGate(){return pane!=null||fetching||pendingWake!=null;}
 boolean ordinaryAllowed(boolean debug){return active()&&!ownVisible()&&!hasGate()&&Settings.canDrawOverlays(this)&&notificationsReady()&&prefs.getLong("deck",-1)>=0&&GatePolicy.allowsRequest(System.currentTimeMillis(),prefs.getLong("protected_until",0),todayCount(),debug);}
 boolean wakeAllowed(boolean debug){return enabled()&&wakeMode()&&!power.isInteractive()&&!ownVisible()&&!hasGate()&&Settings.canDrawOverlays(this)&&notificationsReady()&&exactReady()&&prefs.getLong("deck",-1)>=0&&(debug||inWakeWindow())&&GatePolicy.allowsRequest(System.currentTimeMillis(),prefs.getLong("protected_until",0),todayCount(),debug);}
 boolean exactReady(){return Build.VERSION.SDK_INT<31||alarms.canScheduleExactAlarms();}
 boolean inWakeWindow(){return WakePolicy.inWindow(LocalTime.now(),prefs.getInt("wake_config_start",540),prefs.getInt("wake_config_end",1380));}
 int todayCount(){return LocalDate.now().toString().equals(prefs.getString("day",""))?prefs.getInt("count",0):0;}
 void saveUsage(){if(prefs.getLong("used_ms",-1)!=triggers.usedMs||prefs.getLong("interval_ms",-1)!=triggers.intervalMs)prefs.edit().putLong("used_ms",triggers.usedMs).putLong("interval_ms",triggers.intervalMs).apply();}
 void offerUnlock(){boolean allowed=unlockMode()&&ordinaryAllowed(false);log("unlock accepted="+allowed);if(allowed)triggers.unlocked(true);}
 void checkSystemState(){
  main.removeCallbacks(stateCheck);if(destroyed)return;
  boolean interactive=power.isInteractive(),current=interactive&&!keyguard.isKeyguardLocked();
  if(stateObserved&&(interactive!=observedInteractive||current!=observedActive)){
   log("system state interactive="+interactive+" unlocked="+current);
   if(!interactive){if(!fetchIsWake)invalidateFetch();closeOrdinary();if(wakeActivity!=null)wakeActivity.finish();scheduleWake();}
   else{cancelAlarm();if(current&&!observedActive)offerUnlock();}
   reevaluate();
  }
  observedInteractive=interactive;observedActive=current;stateObserved=true;
  refreshNotification();
  if(enabled())main.postDelayed(stateCheck,interactive&&keyguard.isKeyguardLocked()?1000:5000);
 }
 void ownActivity(boolean visible){log("ownActivity visible="+visible);if(visible){invalidateFetch();closeGate();}reevaluate();if(!visible)scheduleWake();}
 void reevaluate(){
  if(destroyed||triggers==null)return;main.removeCallbacks(boundary);
  long elapsed=SystemClock.elapsedRealtime();triggers.update(elapsed,active()&&!ownVisible()&&!hasGate(),usageMode());saveUsage();
  if(!unlockMode())triggers.clearUnlock();
  if(!enabled())return;
  if(!main.hasCallbacks(stateCheck))main.postDelayed(stateCheck,1000);
  if(!active()||ownVisible()){
   if(!fetchIsWake)invalidateFetch();closeOrdinary();if(ownVisible())closeWake("own-page");return;
  }
  if(hasGate())return;
  if(triggers.pending()){
   if(prefs.getLong("deck",-1)<0||!Settings.canDrawOverlays(this))return;
   long now=System.currentTimeMillis(),until=prefs.getLong("protected_until",0);int count=todayCount();
   if(GatePolicy.allows(now,until,count)){fetch(false,false);return;}
   triggers.clearUnlock();
   if(count>=GatePolicy.DAILY_LIMIT)main.postDelayed(boundary,Math.max(1,Duration.between(Instant.ofEpochMilli(now),LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()).toMillis()));
   else if(triggers.usagePending)main.postDelayed(boundary,Math.max(1,until-now));
   else if(usageMode())main.postDelayed(boundary,Math.max(1,triggers.remaining()));
  }else if(usageMode())main.postDelayed(boundary,Math.max(1,triggers.remaining()));
 }
 void fetch(boolean wake,boolean debug){
  if(hasGate())return;cancelAlarm();long request=++generation;fetching=true;fetchIsWake=wake;
  currentResult="正在取卡";
  triggers.update(SystemClock.elapsedRealtime(),false,usageMode());triggers.clearUnlock();saveUsage();main.removeCallbacks(boundary);
  DiagnosticLog.event(this,"尝试取卡："+(wake?"锁屏亮屏":"普通介入")+(debug?"（调试）":""));log("fetch source="+(wake?(debug?"debug-wake":"wake"):(debug?"debug":"automatic")));
  final PowerManager.WakeLock cpu=wake?power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"ankigate:card-read"):null;
  if(cpu!=null)cpu.acquire(30000);
  fetchFuture=worker.submit(()->{boolean posted=false;try{
   AnkiApi.Card card=new AnkiApi(this).next(prefs.getLong("deck",-1));MediaAccess media=new MediaAccess(this);if(card!=null)media.prepare(card.media);
   posted=main.post(()->{try{
    if(destroyed||request!=generation)return;fetching=false;fetchIsWake=false;fetchFuture=null;
    boolean allowed=wake?wakeAllowed(debug):ordinaryAllowed(debug);
    if(!allowed){log("cancel fetched state changed wake="+wake);reevaluate();scheduleWake();return;}
    if(card==null){currentResult="本轮无到期卡，未展示";DiagnosticLog.event(this,"本次无到期卡，不展示");log("no scheduled card wake="+wake);consume();reevaluate();scheduleWake();return;}
    if(wake)launchWake(request,card,media,debug,cpu);else showOrdinary(card,media,debug);
   }finally{if(pendingWake==null||pendingWake.token!=request)release(cpu);}});
  }catch(Exception e){posted=main.post(()->{try{if(!destroyed&&request==generation){fetching=false;fetchIsWake=false;fetchFuture=null;DiagnosticLog.event(this,"取卡/媒体失败："+e.getClass().getSimpleName());error("取卡未开始："+e);consume();reevaluate();scheduleWake();}}finally{release(cpu);}});
  }finally{if(!posted)release(cpu);}});
 }
 void release(PowerManager.WakeLock lock){if(lock!=null&&lock.isHeld())lock.release();}
 void consume(){triggers.consumed(SystemClock.elapsedRealtime());saveUsage();}
 void recordShow(String source){int count=todayCount();prefs.edit().putString("day",LocalDate.now().toString()).putInt("count",count+1).putLong("last",System.currentTimeMillis()).apply();currentResult="卡面已实际显示";DiagnosticLog.event(this,"卡面已实际显示："+source);consume();log("display source="+source+" count="+(count+1));}
 void showOrdinary(AnkiApi.Card card,MediaAccess media,boolean debug){try{
  pane=new ReviewPane(this,card,media,worker,()->{log("answer-confirmed ordinary");closeOrdinary();reevaluate();},this::stopAll);
  WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.OPAQUE);
  windows.addView(pane,p);recordShow(debug?"debug":"automatic");
 }catch(Exception e){closeOrdinary();error("悬浮卡未显示："+e);consume();reevaluate();}}
 void launchWake(long token,AnkiApi.Card card,MediaAccess media,boolean debug,PowerManager.WakeLock cpu){
  pendingWake=new PendingWake(token,card,media,debug,cpu);
  try{startActivity(new Intent(this,WakeReviewActivity.class).putExtra("token",token).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));main.postDelayed(launchTimeout,10000);log("wake Activity launch requested token="+token+" debugWindowBypass="+debug);}
  catch(Exception e){error("亮屏卡启动受限："+e);closeWake("launch-error");scheduleWake();reevaluate();}
 }
 PendingWake claimWake(long token,WakeReviewActivity activity){
  if(pendingWake==null||pendingWake.token!=token||pendingWake.shown||destroyed||!enabled()||!wakeMode())return null;
  wakeActivity=activity;return pendingWake;
 }
 boolean wakeDisplayed(long token){
  if(pendingWake==null||pendingWake.token!=token||pendingWake.shown||destroyed||!enabled()||!wakeMode()||ownVisible()||!Settings.canDrawOverlays(this)||!notificationsReady()||!exactReady()||(!pendingWake.debug&&!inWakeWindow())||!GatePolicy.allowsRequest(System.currentTimeMillis(),prefs.getLong("protected_until",0),todayCount(),pendingWake.debug))return false;
  main.removeCallbacks(launchTimeout);pendingWake.shown=true;release(pendingWake.cpu);recordShow(pendingWake.debug?"debug-wake":"wake");log("wake Activity confirmed visible token="+token);return true;
 }
 void wakeEnded(long token){if(pendingWake!=null&&pendingWake.token==token){if(pendingWake.shown)ReviewStats.finished(this);release(pendingWake.cpu);pendingWake=null;wakeActivity=null;main.removeCallbacks(launchTimeout);log("wake ended token="+token);reevaluate();scheduleWake();}}
 void closeWake(String reason){main.removeCallbacks(launchTimeout);if(pendingWake!=null){if(pendingWake.shown)ReviewStats.finished(this);release(pendingWake.cpu);pendingWake=null;}WakeReviewActivity old=wakeActivity;wakeActivity=null;if(old!=null)old.finish();log("close wake reason="+reason);}
 void closeOrdinary(){if(pane!=null){ReviewStats.finished(this);ReviewPane old=pane;pane=null;try{windows.removeView(old);}catch(IllegalArgumentException ignored){}old.dispose();log("close ordinary");}}
 public void closeGate(){closeOrdinary();closeWake("close-all");}
 void invalidateFetch(){generation++;fetching=false;fetchIsWake=false;if(fetchFuture!=null){fetchFuture.cancel(false);fetchFuture=null;}}
 PendingIntent wakeIntent(){return PendingIntent.getForegroundService(this,7,new Intent(this,GateService.class).setAction(WAKE_ALARM),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
 void cancelAlarm(){alarms.cancel(wakeIntent());if(prefs.contains("wake_alarm_elapsed"))prefs.edit().remove("wake_alarm_elapsed").remove("wake_alarm_boot").apply();}
 void setAlarm(long delay,String source){try{long at=SystemClock.elapsedRealtime()+delay;alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,at,wakeIntent());prefs.edit().putLong("wake_alarm_elapsed",at).putInt("wake_alarm_boot",bootCount()).apply();DiagnosticLog.event(this,"锁屏检查已计划："+source+"，等待"+RunStatusPolicy.seconds(delay)+"秒");log("wake alarm source="+source+" delayMs="+delay);}catch(SecurityException e){DiagnosticLog.event(this,"锁屏闹钟计划失败：许可不足");error("精确闹钟权限不可用："+e);}}
 void scheduleWake(){
  if(destroyed||!enabled()||!wakeMode()||power.isInteractive()||ownVisible()||hasGate()||!Settings.canDrawOverlays(this)||!notificationsReady()||!exactReady())return;
  if(prefs.getLong("wake_alarm_elapsed",0)>SystemClock.elapsedRealtime())return;
  int min=prefs.getInt("wake_config_min",5),max=prefs.getInt("wake_config_max",10);
  long random=ThreadLocalRandom.current().nextLong(min*60000L,max*60000L+1);
  LocalDateTime now=LocalDateTime.now();int start=prefs.getInt("wake_config_start",540),end=prefs.getInt("wake_config_end",1380);
  long delay=todayCount()>=GatePolicy.DAILY_LIMIT?Duration.between(now,now.toLocalDate().plusDays(1).atStartOfDay().plusMinutes(start)).toMillis()+random:WakePolicy.nextDelay(now,start,end,random);
  setAlarm(delay,"normal");
 }
 void scheduleTestWake(){
  if(!enabled()||!wakeMode()||!Settings.canDrawOverlays(this)||!notificationsReady()||!exactReady()||!inWakeWindow()||hasGate()||!GatePolicy.allows(System.currentTimeMillis(),prefs.getLong("protected_until",0),todayCount())){error("亮屏测试未安排：请检查模式、权限、时段、保护期和当前复习");return;}
  cancelAlarm();setAlarm(10000,"user-test");
 }
 public void stopAll(){prefs.edit().putBoolean("enabled",false).apply();invalidateFetch();closeGate();cancelAlarm();triggers.update(SystemClock.elapsedRealtime(),false,usageMode());triggers.clearUnlock();saveUsage();main.removeCallbacks(boundary);main.removeCallbacks(stateCheck);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();log("stopped all");}
 @Override public IBinder onBind(Intent i){return null;}
 @Override public void onDestroy(){
  invalidateFetch();closeGate();if(!enabled()||!wakeMode())cancelAlarm();else scheduleWake();destroyed=true;main.removeCallbacksAndMessages(null);triggers.update(SystemClock.elapsedRealtime(),false,usageMode());saveUsage();prefs.unregisterOnSharedPreferenceChangeListener(changed);
  if(registered)unregisterReceiver(screen);if(debugRegistered)unregisterReceiver(debug);if(instance==this)instance=null;worker.shutdown();DiagnosticLog.event(this,"前台服务已销毁；"+(enabled()?"开关仍启用，后续恢复由系统决定":"用户已停用"));log("destroyed foreground service");super.onDestroy();
 }
 void error(String reason){currentResult="本轮失败；详见下方错误信息或近期运行记录";prefs.edit().putString("error",reason).apply();log("error "+reason);}
 void log(String value){android.util.Log.d("AnkiGateService",value);}
}
