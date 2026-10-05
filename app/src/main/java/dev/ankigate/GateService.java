package dev.ankigate;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
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
 ReviewPane pane;LinearLayout ordinaryRoot;ReviewBatch ordinaryBatch;boolean ordinaryDebug;CardRead.Request fetchRequest;PowerManager.WakeLock readCpu;PendingWake pendingWake;WakeReviewActivity wakeActivity;
 long generation;boolean fetching,fetchIsWake,destroyed,started,registered,debugRegistered,observedActive,observedInteractive,stateObserved;
 long lastNotificationElapsed;String lastNotification="",currentResult="";
 static final class PendingWake {
  final long token;AnkiApi.Card card;MediaAccess media;final ReviewBatch batch;final boolean debug;final PowerManager.WakeLock cpu;boolean shown;
  PendingWake(long token,AnkiApi.Card card,MediaAccess media,int limit,boolean debug,PowerManager.WakeLock cpu){this.token=token;this.card=card;this.media=media;batch=new ReviewBatch(card.deck,limit);this.debug=debug;this.cpu=cpu;}
 }
 void wakeLaunchTimedOut(){if(pendingWake!=null&&!pendingWake.shown){error("亮屏卡页面未确认显示，可能被系统限制；本次不计展示");closeWake("launch-timeout");scheduleWake();reevaluate();}}
 final SharedPreferences.OnSharedPreferenceChangeListener changed=(p,key)->{
  if("protected_until".equals(key)){main.post(this::reevaluate);return;}
  if("enabled".equals(key)||"unlock_mode".equals(key)||"usage_mode".equals(key)||"deck".equals(key)||"media".equals(key)||"wake_mode".equals(key)||key.startsWith("wake_config_"))main.post(()->{
   if(destroyed)return;
   if(!enabled()){stopAll();return;}
   invalidateFetch("配置已改变");if(!unlockMode())triggers.clearUnlock();
   if(!wakeMode()){cancelAlarm();closeWake("wake-disabled");}else{cancelAlarm();scheduleWake();}
   reevaluate();
  });
 };
 final BroadcastReceiver screen=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
  if(Intent.ACTION_TIME_CHANGED.equals(i.getAction())||Intent.ACTION_TIMEZONE_CHANGED.equals(i.getAction())){cancelAlarm();DiagnosticLog.event(c,"系统时间改变：重新核对复习时段与保护");reevaluate();return;}
  log("screen action="+i.getAction());checkSystemState();
  if(Intent.ACTION_USER_PRESENT.equals(i.getAction())){offerUnlock();reevaluate();}
 }};
 final BroadcastReceiver debug=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
  if("dev.ankigate.TEST_REVIEW_ALARM".equals(i.getAction())){
   if(active()&&usageMode()&&!ownVisible()&&!hasGate()&&exactReady()&&Settings.canDrawOverlays(c)&&notificationsReady())setAlarm(15000,"usage","debug-user-check");
   else log("debug system check rejected");return;
  }
  if("dev.ankigate.TEST_STOP_OWNER".equals(i.getAction())){DiagnosticLog.event(c,"调试：停止当前服务，保留用户开关和已登记系统检查");stopSelf();return;}
  if("dev.ankigate.TEST_READ_DELAY".equals(i.getAction())){
   int delay=i.getIntExtra("delayMs",-1);String stage=i.getStringExtra("stage");boolean accepted=CardRead.testNext(c,stage,delay,i.getBooleanExtra("ignoreCancel",false));
   log("debug read delay accepted="+accepted);return;
  }
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
  IntentFilter f=new IntentFilter();f.addAction(Intent.ACTION_SCREEN_ON);f.addAction(Intent.ACTION_SCREEN_OFF);f.addAction(Intent.ACTION_USER_PRESENT);f.addAction(Intent.ACTION_TIME_CHANGED);f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
  if(Build.VERSION.SDK_INT>=33)registerReceiver(screen,f,Context.RECEIVER_EXPORTED);else registerReceiver(screen,f);registered=true;
  if((getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0){
   IntentFilter d=new IntentFilter("dev.ankigate.TEST_TRIGGER");d.addAction("dev.ankigate.TEST_WAKE");d.addAction("dev.ankigate.TEST_READ_DELAY");d.addAction("dev.ankigate.TEST_REVIEW_ALARM");d.addAction("dev.ankigate.TEST_STOP_OWNER");
   if(Build.VERSION.SDK_INT>=33)registerReceiver(debug,d,"android.permission.DUMP",main,Context.RECEIVER_EXPORTED);else registerReceiver(debug,d,"android.permission.DUMP",main);debugRegistered=true;
  }
  // Sticky reconstruction does not replay an old unlock, and never counts offline time.
  cancelLegacyAlarm();
  if(prefs.getInt("review_alarm_boot",-1)!=bootCount()&&prefs.contains("review_alarm_elapsed"))cancelAlarm();
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
   else if(!power.isInteractive()&&wakeMode()){long at=prefs.getLong("review_alarm_elapsed",0);text+="\n"+(at>elapsed?"计划约"+RunStatusPolicy.seconds(at-elapsed)+"秒后亮屏检查（系统可能推迟）":"暂无未来亮屏计划；投递/许可原因需诊断确认");}
   else if(unlockMode())text+="\n等待保护结束后的真实解锁事件";
  }
  if(!currentResult.isEmpty())text+="\n本轮结果："+currentResult;
  if(CardRead.busy())text+="\n"+CardRead.status();
  return text+"\n"+ReviewAlarmReceiver.status(this)+"\n检查不保证弹卡：仍须有到期卡、许可和媒体可读";
 }
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  String action=intent==null?"sticky":intent.getAction();log("start action="+action);
  if(STOP.equals(action)||!enabled()){stopAll();return START_NOT_STICKY;}
  if(!Settings.canDrawOverlays(this)||!notificationsReady()){error("请从主界面授权悬浮窗和通知，再启动服务");stopAll();return START_NOT_STICKY;}
  if(WAKE_ALARM.equals(action)){cancelLegacyAlarm();DiagnosticLog.event(this,"拒绝旧版锁屏闹钟：旧交接已失效");if(!started){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return START_NOT_STICKY;}return START_STICKY;}
  if(ReviewAlarmReceiver.ACTION.equals(action)){
   long token=intent.getLongExtra("token",-1);String type=intent.getStringExtra("type");
   if(token<=0||token!=prefs.getLong("review_delivery_token",-2)||!java.util.Objects.equals(type,prefs.getString("review_delivery_type",""))){DiagnosticLog.event(this,"系统复习服务拒绝：旧或重复交接token");if(!started){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return START_NOT_STICKY;}return START_STICKY;}
   prefs.edit().remove("review_delivery_token").remove("review_delivery_type").commit();started=true;
   DiagnosticLog.event(this,"系统复习检查已进入前台服务："+type+"；token="+token+"；离线不累计使用");
   if("wake".equals(type)){if(wakeAllowed(false))fetch(true,false);else DiagnosticLog.event(this,"亮屏系统检查未取卡：屏幕/页面/时段/保护/配额状态不满足");}
   reevaluate();scheduleWake();
  }
  else if(TEST_WAKE.equals(action)){started=true;scheduleTestWake();reevaluate();}
  else{started=true;reevaluate();scheduleWake();}
  refreshNotification();return START_STICKY;
 }
 boolean notificationsReady(){return ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled()&&(Build.VERSION.SDK_INT<33||checkSelfPermission("android.permission.POST_NOTIFICATIONS")==android.content.pm.PackageManager.PERMISSION_GRANTED);}
 boolean enabled(){return prefs.getBoolean("enabled",false);}
 boolean unlockMode(){return prefs.getBoolean("unlock_mode",true);}
 boolean usageMode(){return prefs.getBoolean("usage_mode",true);}
 boolean wakeMode(){return prefs.getBoolean("wake_mode",false);}
 boolean ownVisible(){return MainActivity.visible||MainActivity.configuring||ReviewActivity.inProgress||UpdateActivity.inProgress||UpdateActivity.configuring;}
 boolean active(){return enabled()&&power.isInteractive()&&!keyguard.isKeyguardLocked();}
 public boolean hasCard(){return ordinaryRoot!=null||pendingWake!=null;}
 public boolean hasGate(){return hasCard()||CardRead.busy();}
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
   if(!interactive){if(!fetchIsWake)invalidateFetch("设备已锁屏或熄屏");closeOrdinary();if(wakeActivity!=null)wakeActivity.finish();scheduleWake();}
   else{if(!current&&!fetchIsWake){invalidateFetch("设备已锁屏");closeOrdinary();}if(current&&!observedActive&&started)offerUnlock();}
   reevaluate();
  }
  observedInteractive=interactive;observedActive=current;stateObserved=true;
  if(started)reevaluate();
  refreshNotification();
  if(enabled())main.postDelayed(stateCheck,interactive&&keyguard.isKeyguardLocked()?1000:5000);
 }
 void ownActivity(boolean visible){log("ownActivity visible="+visible);if(visible){invalidateFetch("进入本应用页面");closeGate();}reevaluate();if(!visible)scheduleWake();}
 void reevaluate(){
  if(destroyed||triggers==null||!started)return;main.removeCallbacks(boundary);
  try{
  long elapsed=SystemClock.elapsedRealtime();triggers.update(elapsed,active()&&!ownVisible()&&!hasGate(),usageMode());saveUsage();
  if(!unlockMode())triggers.clearUnlock();
  if(!enabled())return;
  if(!main.hasCallbacks(stateCheck))main.postDelayed(stateCheck,1000);
  if(!active()||ownVisible()){
   if(!fetchIsWake)invalidateFetch(ownVisible()?"进入本应用页面":"设备已锁屏或熄屏");closeOrdinary();if(ownVisible())closeWake("own-page");return;
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
  }finally{scheduleWake();}
 }
 void fetch(boolean wake,boolean debug){
  if(hasGate())return;cancelAlarm();long request=++generation;fetching=true;fetchIsWake=wake;
  currentResult="正在读取；30秒后请求取消，接口返回前保留等待状态";
  triggers.update(SystemClock.elapsedRealtime(),false,usageMode());triggers.clearUnlock();saveUsage();main.removeCallbacks(boundary);
  log("fetch source="+(wake?(debug?"debug-wake":"wake"):(debug?"debug":"automatic")));
  final PowerManager.WakeLock cpu=wake?power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"ankigate:card-read"):null;
  if(cpu!=null)cpu.acquire(30000);
  readCpu=cpu;
  fetchRequest=CardRead.start(this,(wake?"锁屏亮屏":"普通介入")+(debug?"（调试）":""),prefs.getLong("deck",-1),(reading,card,media,failure)->{try{
    fetching=false;fetchIsWake=false;fetchRequest=null;readCpu=null;
    if(destroyed)return;
    if(reading.life.cancelled()||request!=generation){currentResult="读取已取消，接口已结束；本次未展示";consume();reevaluate();scheduleWake();return;}
    if(failure!=null){error("取卡未开始："+failure.getClass().getSimpleName());consume();reevaluate();scheduleWake();return;}
    boolean allowed=wake?wakeAllowed(debug):ordinaryAllowed(debug);
    if(!allowed){currentResult="读取后状态已改变，本次未展示";DiagnosticLog.event(this,"读取结果未展示：屏幕/页面/许可状态已改变");consume();reevaluate();scheduleWake();return;}
    if(card==null){currentResult="本轮无到期卡，未展示";DiagnosticLog.event(this,"本次无到期卡，不展示");log("no scheduled card wake="+wake);consume();reevaluate();scheduleWake();return;}
    if(wake)launchWake(request,card,media,debug,cpu);else showOrdinary(card,media,debug);
   }finally{if(pendingWake==null||pendingWake.token!=request)release(cpu);}});
  if(fetchRequest==null){fetching=false;fetchIsWake=false;readCpu=null;release(cpu);currentResult="另一读取尚未结束；没有排队新的取卡";}
 }
 void release(PowerManager.WakeLock lock){if(lock!=null&&lock.isHeld())lock.release();}
 void consume(){triggers.consumed(SystemClock.elapsedRealtime());saveUsage();}
 void recordShow(String source){int count=todayCount();prefs.edit().putString("day",LocalDate.now().toString()).putInt("count",count+1).putLong("last",System.currentTimeMillis()).apply();currentResult="卡面已实际显示";DiagnosticLog.event(this,"卡面已实际显示："+source);consume();log("display source="+source+" count="+(count+1));}
 void showOrdinary(AnkiApi.Card card,MediaAccess media,boolean debug){try{
  ordinaryBatch=new ReviewBatch(card.deck,prefs.getInt("batch_limit",1));ordinaryDebug=debug;ordinaryRoot=new LinearLayout(this);ordinaryRoot.setOrientation(LinearLayout.VERTICAL);
  WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.OPAQUE);
  windows.addView(ordinaryRoot,p);showOrdinaryCard(card,media);
 }catch(Exception e){closeOrdinary();error("悬浮卡未显示："+e);consume();reevaluate();}}
 void showOrdinaryCard(AnkiApi.Card card,MediaAccess media){pane=new ReviewPane(this,card,media,worker,ordinaryBatch,this::ordinaryScored,()->{closeOrdinary();reevaluate();scheduleWake();},this::stopAll);ordinaryRoot.removeAllViews();ordinaryRoot.addView(pane,new LinearLayout.LayoutParams(-1,-1));recordShow(ordinaryDebug?"debug":"automatic");}
 void ordinaryScored(){
  ReviewBatch batch=ordinaryBatch;if(batch==null||batch.ended)return;
  if(!batch.confirmed()){closeOrdinary();reevaluate();scheduleWake();return;}
  ReviewPane old=pane;pane=null;ordinaryRoot.removeAllViews();ordinaryRoot.addView(ShellUi.text(this,"正在取第"+batch.position()+"张；上一张已提交。",18));
  ordinaryRoot.addView(ShellUi.button(this,"结束本轮",v->{closeOrdinary();reevaluate();scheduleWake();}));ordinaryRoot.addView(ShellUi.button(this,"停止全部介入",v->stopAll()));
  old.dispose(()->readNextAutomatic(batch,false,ordinaryDebug));
 }
 boolean continuationAllowed(ReviewBatch batch,boolean wake,boolean debug){
  if(destroyed||batch.ended||!enabled()||ownVisible()||!Settings.canDrawOverlays(this)||!notificationsReady()||todayCount()>=GatePolicy.DAILY_LIMIT)return false;
  if(wake)return pendingWake!=null&&pendingWake.batch==batch&&pendingWake.shown&&wakeActivity!=null&&!wakeActivity.isFinishing()&&wakeActivity.visible&&wakeActivity.hasWindowFocus()&&power.isInteractive()&&wakeMode()&&exactReady()&&(debug||inWakeWindow());
  return ordinaryBatch==batch&&ordinaryRoot!=null&&active();
 }
 void endAutomatic(ReviewBatch batch,boolean wake,String reason){if(wake){if(pendingWake!=null&&pendingWake.batch==batch)closeWake(reason);}else if(ordinaryBatch==batch)closeOrdinary();reevaluate();scheduleWake();}
 void readNextAutomatic(ReviewBatch batch,boolean wake,boolean debug){
  if(!continuationAllowed(batch,wake,debug)){endAutomatic(batch,wake,"batch-state-changed");return;}
  long request=++generation;fetching=true;fetchIsWake=wake;currentResult="正在取第"+batch.position()+"张；可以结束本轮";
  fetchRequest=CardRead.start(this,(wake?"锁屏亮屏":"普通介入")+" "+batch.position(),batch.deck,(reading,card,media,failure)->{
   fetching=false;fetchIsWake=false;fetchRequest=null;
   if(destroyed||batch.ended)return;
   if(reading.life.cancelled()||request!=generation||!continuationAllowed(batch,wake,debug)){endAutomatic(batch,wake,"batch-read-cancelled");return;}
   if(failure!=null){error("本轮下一张读取失败："+failure.getClass().getSimpleName());endAutomatic(batch,wake,"batch-read-failed");return;}
   if(card==null){currentResult="没有更多到期卡，本轮结束";DiagnosticLog.event(this,currentResult);endAutomatic(batch,wake,"batch-no-card");return;}
   try{if(wake){pendingWake.card=card;pendingWake.media=media;wakeActivity.showNext();recordShow(debug?"debug-wake":"wake");}else showOrdinaryCard(card,media);}
   catch(Exception e){error("本轮下一张未显示："+e.getClass().getSimpleName());endAutomatic(batch,wake,"batch-show-failed");}
  });
  if(fetchRequest==null){fetching=false;fetchIsWake=false;error("上次读取或牌组归还尚未结束；本轮安全结束");endAutomatic(batch,wake,"batch-slot-busy");}
 }
 void launchWake(long token,AnkiApi.Card card,MediaAccess media,boolean debug,PowerManager.WakeLock cpu){
  pendingWake=new PendingWake(token,card,media,prefs.getInt("batch_limit",1),debug,cpu);
  card.selection.handedOff=true;
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
 void wakeEnded(long token){if(pendingWake!=null&&pendingWake.token==token){closeWake("activity-ended");log("wake ended token="+token);reevaluate();scheduleWake();}}
 void closeWake(String reason){main.removeCallbacks(launchTimeout);if(pendingWake!=null){pendingWake.batch.finish(this);if(fetchRequest!=null&&fetchIsWake)invalidateFetch("本轮已结束");CardRead.closeSelection(this,pendingWake.card.selection,worker);release(pendingWake.cpu);pendingWake=null;}WakeReviewActivity old=wakeActivity;wakeActivity=null;if(old!=null)old.finish();log("close wake reason="+reason);}
 void closeOrdinary(){if(ordinaryBatch!=null){ordinaryBatch.finish(this);ordinaryBatch=null;}if(ordinaryRoot!=null){if(fetchRequest!=null&&!fetchIsWake)invalidateFetch("本轮已结束");LinearLayout oldRoot=ordinaryRoot;ordinaryRoot=null;try{windows.removeView(oldRoot);}catch(IllegalArgumentException ignored){}if(pane!=null){ReviewPane old=pane;pane=null;old.dispose();}log("close ordinary");}}
 public void closeGate(){closeOrdinary();closeWake("close-all");}
 void invalidateFetch(String reason){generation++;if(fetchRequest!=null){fetchRequest.cancel(reason);release(readCpu);}}
 PendingIntent wakeIntent(){return PendingIntent.getForegroundService(this,7,new Intent(this,GateService.class).setAction(WAKE_ALARM),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
 void cancelLegacyAlarm(){alarms.cancel(wakeIntent());if(prefs.contains("wake_alarm_elapsed"))prefs.edit().remove("wake_alarm_elapsed").remove("wake_alarm_boot").apply();}
 static PendingIntent reviewIntent(Context c,long token,String type,int boot){return PendingIntent.getBroadcast(c,8,new Intent(c,ReviewAlarmReceiver.class).setAction(ReviewAlarmReceiver.ACTION).setData(android.net.Uri.parse("ankigate-review://plan/"+token)).putExtra("token",token).putExtra("type",type).putExtra("boot",boot),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
 static void cancelPlans(Context c,SharedPreferences p){
  AlarmManager manager=(AlarmManager)c.getSystemService(ALARM_SERVICE);long token=p.getLong("review_alarm_token",0);
  if(token>0){PendingIntent pi=reviewIntent(c,token,p.getString("review_alarm_type",""),p.getInt("review_alarm_boot",-1));manager.cancel(pi);pi.cancel();}
  manager.cancel(PendingIntent.getForegroundService(c,7,new Intent(c,GateService.class).setAction(WAKE_ALARM),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
  p.edit().remove("wake_alarm_elapsed").remove("wake_alarm_boot").remove("review_alarm_elapsed").remove("review_alarm_boot").remove("review_alarm_token").remove("review_alarm_type").remove("review_alarm_source").remove("review_delivery_token").remove("review_delivery_type").commit();
 }
 void cancelAlarm(){
  if(prefs.contains("review_alarm_elapsed"))DiagnosticLog.event(this,"系统复习计划已取消：屏幕/页面/模式/许可或本轮状态已改变");
  cancelPlans(this,prefs);
 }
 void setAlarm(long delay,String type,String source){
  long at=SystemClock.elapsedRealtime()+Math.max(1,delay),token=prefs.getLong("review_alarm_serial",0)+1;int boot=bootCount();
  cancelPlans(this,prefs);
  prefs.edit().putLong("review_alarm_serial",token).putLong("review_alarm_token",token).putLong("review_alarm_elapsed",at).putInt("review_alarm_boot",boot).putString("review_alarm_type",type).putString("review_alarm_source",source).commit();
  try{alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,at,reviewIntent(this,token,type,boot));DiagnosticLog.event(this,"系统复习检查已计划："+type+" / "+source+"，等待"+RunStatusPolicy.seconds(delay)+"秒；token="+token);log("system alarm type="+type+" source="+source+" delayMs="+delay+" token="+token);}
  catch(SecurityException e){cancelAlarm();DiagnosticLog.event(this,"系统复习计划失败：精确闹钟许可不足；普通计时仍可运行");}
 }
 void scheduleWake(){
  if(destroyed||!started)return;
  // The explicit foreground test permits leaving this page and switching the screen off within 10s.
  if(enabled()&&wakeMode()&&!hasGate()&&exactReady()&&Settings.canDrawOverlays(this)&&notificationsReady()&&prefs.getLong("review_alarm_elapsed",0)>SystemClock.elapsedRealtime()&&prefs.getInt("review_alarm_boot",-1)==bootCount()&&"user-test".equals(prefs.getString("review_alarm_source","")))return;
  String type=active()&&usageMode()?"usage":!power.isInteractive()&&wakeMode()?"wake":"";
  if(!enabled()||type.isEmpty()||ownVisible()||hasGate()||!Settings.canDrawOverlays(this)||!notificationsReady()||!exactReady()||prefs.getLong("deck",-1)<0||!prefs.contains("media")||checkSelfPermission(AnkiApi.PERMISSION)!=android.content.pm.PackageManager.PERMISSION_GRANTED){if(prefs.contains("review_alarm_elapsed"))cancelAlarm();return;}
  long elapsed=SystemClock.elapsedRealtime(),at=prefs.getLong("review_alarm_elapsed",0);int boot=bootCount();String savedType=prefs.getString("review_alarm_type","");
  boolean future=at>elapsed&&boot>=0&&boot==prefs.getInt("review_alarm_boot",-1)&&type.equals(savedType);
  if(future&&("wake".equals(type)||"debug-user-check".equals(prefs.getString("review_alarm_source",""))))return;
  if("usage".equals(type)){
   long protection=Math.max(0,prefs.getLong("protected_until",0)-System.currentTimeMillis());
   long delay=ReviewAlarmPolicy.usageDelay(triggers.remaining(),protection);
   if(todayCount()>=GatePolicy.DAILY_LIMIT)delay=Math.max(delay,Duration.between(LocalDateTime.now(),LocalDate.now().plusDays(1).atStartOfDay()).toMillis());
   if(!ReviewAlarmPolicy.keep(type,savedType,boot,prefs.getInt("review_alarm_boot",-1),at,elapsed,elapsed+delay))setAlarm(delay,type,"active-use-remaining");return;
  }
  int min=prefs.getInt("wake_config_min",5),max=prefs.getInt("wake_config_max",10);
  long random=ThreadLocalRandom.current().nextLong(min*60000L,max*60000L+1);
  LocalDateTime now=LocalDateTime.now();int start=prefs.getInt("wake_config_start",540),end=prefs.getInt("wake_config_end",1380);
  long delay=todayCount()>=GatePolicy.DAILY_LIMIT?Duration.between(now,now.toLocalDate().plusDays(1).atStartOfDay().plusMinutes(start)).toMillis()+random:WakePolicy.nextDelay(now,start,end,random);
  setAlarm(delay,"wake","normal");
 }
 void scheduleTestWake(){
  if(!enabled()||!wakeMode()||!Settings.canDrawOverlays(this)||!notificationsReady()||!exactReady()||!inWakeWindow()||hasGate()||!GatePolicy.allows(System.currentTimeMillis(),prefs.getLong("protected_until",0),todayCount())){error("亮屏测试未安排：请检查模式、权限、时段、保护期和当前复习");return;}
  cancelAlarm();setAlarm(10000,"wake","user-test");
 }
 public void stopAll(){ReviewActivity.stopCurrent();CardRead.nextDelay=null;if(CardRead.active!=null)CardRead.active.cancel("用户停止全部介入");prefs.edit().putBoolean("enabled",false).apply();invalidateFetch("用户停止介入");closeGate();cancelAlarm();triggers.update(SystemClock.elapsedRealtime(),false,usageMode());triggers.clearUnlock();saveUsage();main.removeCallbacks(boundary);main.removeCallbacks(stateCheck);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();log("stopped all");}
 @Override public IBinder onBind(Intent i){return null;}
 @Override public void onDestroy(){
  destroyed=true;invalidateFetch("服务已结束");if(fetchRequest!=null)fetchRequest.detach();closeGate();if(!enabled())cancelAlarm();else cancelLegacyAlarm();main.removeCallbacksAndMessages(null);triggers.update(SystemClock.elapsedRealtime(),false,usageMode());saveUsage();prefs.unregisterOnSharedPreferenceChangeListener(changed);
  if(registered)unregisterReceiver(screen);if(debugRegistered)unregisterReceiver(debug);if(instance==this)instance=null;worker.shutdown();DiagnosticLog.event(this,"前台服务已销毁；"+(enabled()?"开关仍启用，后续恢复由系统决定":"用户已停用"));log("destroyed foreground service");super.onDestroy();
 }
 void error(String reason){currentResult="本轮失败；详见下方错误信息或近期运行记录";prefs.edit().putString("error",reason).apply();log("error "+reason);}
 void log(String value){android.util.Log.d("AnkiGateService",value);}
}
