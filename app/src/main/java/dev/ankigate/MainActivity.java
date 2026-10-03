package dev.ankigate;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
 static boolean visible,configuring;
 final ExecutorService worker=Executors.newSingleThreadExecutor();
 android.content.SharedPreferences prefs;TextView status,statistics;Spinner decks;List<Long> ids=new ArrayList<>();boolean loading,syncing;
 Switch enabledSwitch,wakeSwitch;EditText startTime,endTime,minDelay,maxDelay;
 final android.content.SharedPreferences.OnSharedPreferenceChangeListener errors=(p,key)->{if("error".equals(key))runOnUiThread(()->{if(status!=null&&!isDestroyed())status.setText(p.getString("error",""));});};
 @Override public void onCreate(Bundle state){super.onCreate(state);prefs=getSharedPreferences("gate",0);prefs.registerOnSharedPreferenceChangeListener(errors);showSettings();}
 void showSettings(){
  LinearLayout root=ShellUi.column(this);ScrollView scroll=new ScrollView(this);scroll.addView(root);ShellUi.safeInsets(scroll);setContentView(scroll);
  root.addView(ShellUi.title(this,"Anki 复习介入"));
  root.addView(ShellUi.text(this,"解锁优先，持续使用随机2–5分钟。完成或退出后保护90秒，每日自动显示最多150次。用户启动的前台服务运行时有通知，不使用无障碍。",16));
  statistics=ShellUi.text(this,"",16);root.addView(statistics);refreshStatistics();status=ShellUi.text(this,prefs.getString("error","先授权AnkiDroid并选择牌组，再授权悬浮窗和通知。"),14);root.addView(status);
  root.addView(ShellUi.button(this,"授权AnkiDroid / 刷新牌组",v->{if(checkSelfPermission(AnkiApi.PERMISSION)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{AnkiApi.PERMISSION},10);else loadDecks();}));
  decks=new Spinner(this);root.addView(decks);decks.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,android.view.View v,int position,long id){if(position<ids.size()&&prefs.getLong("deck",-1)!=ids.get(position))prefs.edit().putLong("deck",ids.get(position)).apply();}});
  root.addView(ShellUi.button(this,"授权现有collection.media目录",v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);configuring=true;startActivityForResult(i,11);}));
  root.addView(ShellUi.text(this,"只读取AnkiDroid现有媒体，媒体不可读不会进入评分。",14));
  root.addView(ShellUi.button(this,"复习一张",v->startActivity(new Intent(this,ReviewActivity.class))));
  root.addView(ShellUi.button(this,"授权悬浮窗",v->openConfiguration(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())))));
  root.addView(ShellUi.button(this,"授权运行通知",v->{if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},12);else openConfiguration(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));}));
  enabledSwitch=new Switch(this);enabledSwitch.setText("启用复习介入 / 启动服务");enabledSwitch.setChecked(prefs.getBoolean("enabled",false));root.addView(enabledSwitch);
  enabledSwitch.setOnCheckedChangeListener((b,on)->{if(syncing)return;if(on)startRunning();else stopRunning();});
  root.addView(ShellUi.button(this,"启动 / 恢复前台服务",v->startRunning()));
  Switch unlock=new Switch(this);unlock.setText("解锁优先（不检测切换应用）");unlock.setChecked(prefs.getBoolean("unlock_mode",true));root.addView(unlock);unlock.setOnCheckedChangeListener((b,on)->prefs.edit().putBoolean("unlock_mode",on).apply());
  Switch usage=new Switch(this);usage.setText("持续使用随机2–5分钟");usage.setChecked(prefs.getBoolean("usage_mode",true));root.addView(usage);usage.setOnCheckedChangeListener((b,on)->prefs.edit().putBoolean("usage_mode",on).apply());
  root.addView(ShellUi.text(this,"本应用设置和主动复习暂停自动介入；不读取其他应用前台或页面，因此无法保证外部设置/AnkiDroid界面免弹。停止按钮始终可用。",14));
  root.addView(ShellUi.title(this,"熄屏亮屏复习"));
  root.addView(ShellUi.text(this,"开启后，自用卡片会直接显示在锁屏上并可回答，系统锁仍保留，不会解锁。熄屏后随机5–10分钟、每日09:00–23:00；深度待机可能延迟。初始未触摸最多暂保持亮屏30秒，再退出卡片；首次触摸取消暂保持与退出计时，之后由系统管理熄屏。不改全局屏幕超时。",15));
  root.addView(ShellUi.button(this,"授权精确闹钟",v->{if(android.os.Build.VERSION.SDK_INT>=31)openConfiguration(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));}));
  wakeSwitch=new Switch(this);wakeSwitch.setText("允许锁屏上亮屏显示我的卡片");wakeSwitch.setChecked(prefs.getBoolean("wake_mode",false));root.addView(wakeSwitch);
  wakeSwitch.setOnCheckedChangeListener((b,on)->{if(syncing)return;if(on&&!wakeReady()){status.setText("请先授权悬浮窗、通知和精确闹钟，再明确开启亮屏模式。");syncing=true;wakeSwitch.setChecked(false);syncing=false;return;}prefs.edit().putBoolean("wake_mode",on).apply();});
  startTime=field(root,"开始时间 HH:mm",WakePolicy.time(prefs.getInt("wake_config_start",540)));endTime=field(root,"结束时间 HH:mm",WakePolicy.time(prefs.getInt("wake_config_end",1380)));
  minDelay=field(root,"最短间隔（分钟）",""+prefs.getInt("wake_config_min",5));maxDelay=field(root,"最长间隔（分钟）",""+prefs.getInt("wake_config_max",10));
  root.addView(ShellUi.button(this,"保存亮屏时段和间隔",v->{try{int start=WakePolicy.minutes(startTime.getText().toString()),end=WakePolicy.minutes(endTime.getText().toString()),min=Integer.parseInt(minDelay.getText().toString()),max=Integer.parseInt(maxDelay.getText().toString());if(start>=end||min<1||max<min||max>60)throw new IllegalArgumentException("同日开始需早于结束；间隔需1–60分钟且最短≤最长");prefs.edit().putInt("wake_config_start",start).putInt("wake_config_end",end).putInt("wake_config_min",min).putInt("wake_config_max",max).apply();status.setText("亮屏设置已保存；系统待机可能推迟提醒。");}catch(Exception e){status.setText("未保存："+e.getMessage());}}));
  root.addView(ShellUi.button(this,"测试亮屏提醒（10秒后）",v->{if(GateService.instance==null){status.setText("请先开启模式、授权并启动服务。");return;}startService(new Intent(this,GateService.class).setAction(GateService.TEST_WAKE));status.setText("尝试安排10秒测试，请在倒计时内熄屏。仍检查模式、权限、时段、保护和配额；拒绝原因会显示在此。");}));
  root.addView(ShellUi.button(this,"停止全部介入",v->stopRunning()));
  if(checkSelfPermission(AnkiApi.PERMISSION)==PackageManager.PERMISSION_GRANTED)loadDecks();
 }
 EditText field(LinearLayout root,String label,String value){root.addView(ShellUi.text(this,label,14));EditText e=new EditText(this);e.setSingleLine(true);e.setText(value);root.addView(e);return e;}
 void openConfiguration(Intent intent){configuring=true;startActivity(intent);}
 boolean notificationsReady(){return ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled()&&(android.os.Build.VERSION.SDK_INT<33||checkSelfPermission("android.permission.POST_NOTIFICATIONS")==PackageManager.PERMISSION_GRANTED);}
 boolean wakeReady(){return Settings.canDrawOverlays(this)&&notificationsReady()&&(android.os.Build.VERSION.SDK_INT<31||((AlarmManager)getSystemService(ALARM_SERVICE)).canScheduleExactAlarms());}
 void startRunning(){
  if(!Settings.canDrawOverlays(this)||!notificationsReady()){status.setText("请先授权悬浮窗和运行通知，再启动服务。");syncing=true;enabledSwitch.setChecked(prefs.getBoolean("enabled",false));syncing=false;return;}
  prefs.edit().putBoolean("enabled",true).apply();syncing=true;enabledSwitch.setChecked(true);syncing=false;
  try{startForegroundService(new Intent(this,GateService.class).setAction(GateService.START));status.setText("服务已请求启动；运行通知可停止全部介入。");}catch(Exception e){prefs.edit().putBoolean("enabled",false).apply();syncing=true;enabledSwitch.setChecked(false);syncing=false;status.setText("服务启动失败："+e);}
 }
 void stopRunning(){prefs.edit().putBoolean("enabled",false).apply();syncing=true;enabledSwitch.setChecked(false);syncing=false;if(GateService.instance!=null)GateService.instance.stopAll();else stopService(new Intent(this,GateService.class));status.setText("已停止全部介入；重新开启直接从本界面启动。");}
 void loadDecks(){if(loading)return;loading=true;status.setText("读取牌组…");worker.execute(()->{try{Map<Long,String> found=new AnkiApi(this).decks();runOnUiThread(()->{if(isDestroyed())return;loading=false;ids=new ArrayList<>(found.keySet());List<String> names=new ArrayList<>(found.values());int selected=ids.indexOf(prefs.getLong("deck",-1));if(selected<0)selected=names.indexOf("VOCAREAD");decks.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));if(selected>=0)decks.setSelection(selected);status.setText(found.isEmpty()?"AnkiDroid没有牌组":"牌组已读取；评分会真实提交到AnkiDroid。");});}catch(Exception e){runOnUiThread(()->{if(!isDestroyed()){loading=false;status.setText("读取失败："+e);}});}});}
 @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==10&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)loadDecks();else if(r==12)status.setText("通知授权返回，请确认后启动服务。");else status.setText("未获得AnkiDroidAPI授权");}
 @Override protected void onActivityResult(int r,int result,Intent data){super.onActivityResult(r,result,data);if(r==11&&result==RESULT_OK&&data!=null){try{Uri tree=data.getData();getContentResolver().takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION);prefs.edit().putString("media",tree.toString()).apply();status.setText("现有媒体目录授权已保存。");}catch(Exception e){status.setText("目录授权失败："+e);}}}
 void refreshStatistics(){if(statistics!=null){int shown=java.time.LocalDate.now().toString().equals(prefs.getString("day",""))?prefs.getInt("count",0):0;statistics.setText("本应用今日完成 "+ReviewStats.today(this)+"，实验目标80–120\n自动显示 "+shown+" / 150\n无可复习卡不制造复习，目标不保证达到。");}}
 @Override protected void onStart(){super.onStart();refreshStatistics();}
 @Override protected void onResume(){super.onResume();visible=true;configuring=false;syncing=true;enabledSwitch.setChecked(prefs.getBoolean("enabled",false));wakeSwitch.setChecked(prefs.getBoolean("wake_mode",false));syncing=false;if(GateService.instance!=null)GateService.instance.ownActivity(true);else if(prefs.getBoolean("enabled",false)&&Settings.canDrawOverlays(this)&&notificationsReady())startRunning();}
 @Override protected void onPause(){visible=false;if(GateService.instance!=null)GateService.instance.ownActivity(false);super.onPause();}
 @Override protected void onDestroy(){configuring=false;prefs.unregisterOnSharedPreferenceChangeListener(errors);worker.shutdown();super.onDestroy();}
}
