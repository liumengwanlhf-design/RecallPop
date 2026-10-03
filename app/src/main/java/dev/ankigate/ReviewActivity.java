package dev.ankigate;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.widget.*;
import java.util.concurrent.*;

/** User-requested review; does not alter automatic intervention accounting. */
public final class ReviewActivity extends Activity {
 static boolean visible,inProgress;
 final ExecutorService worker=Executors.newSingleThreadExecutor();
 ReviewPane pane;
 TextView status;
 Future<?> reading;
 long generation;
 boolean begun,rejected;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  if(inProgress||(GateService.instance!=null&&GateService.instance.hasGate())){rejectExisting();return;}
  inProgress=true;
  LinearLayout root=ShellUi.column(this);ShellUi.safeInsets(root);setContentView(root);
  root.addView(ShellUi.title(this,"复习一张"));status=ShellUi.text(this,"准备读取AnkiDroid安排的一张卡…",16);root.addView(status);
  root.addView(ShellUi.button(this,"返回",v->finish()));
 }
 void rejectExisting(){rejected=true;Toast.makeText(this,"已有复习正在进行，请先完成或停止介入。",Toast.LENGTH_LONG).show();finish();}
 boolean screenReady(){
  return ((PowerManager)getSystemService(POWER_SERVICE)).isInteractive()&&!((KeyguardManager)getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked();
 }
 boolean canSubmit(){return visible&&!isFinishing()&&screenReady();}
 void begin(){
  begun=true;
  if(!screenReady()){status.setText("请在亮屏且已解锁时主动复习。");return;}
  if(GateService.instance!=null&&GateService.instance.hasGate()){status.setText("已有自动复习正在读取或显示，请先完成或停止介入。");return;}
  if(checkSelfPermission(AnkiApi.PERMISSION)!=PackageManager.PERMISSION_GRANTED){status.setText("尚未授权AnkiDroid。请返回主界面授权并选择牌组。");return;}
  long deck=getSharedPreferences("gate",0).getLong("deck",-1);
  if(deck<0){status.setText("尚未选择牌组。请返回主界面配置。");return;}
  final long request=++generation;
  android.util.Log.d("AnkiGateActivity","manual begin");
  reading=worker.submit(()->{try{
   AnkiApi.Card card=new AnkiApi(this).next(deck);MediaAccess media=new MediaAccess(this);if(card!=null)media.prepare(card.media);
   runOnUiThread(()->{
    if(request!=generation||!visible||isFinishing()||isDestroyed())return;
    reading=null;
    if(!screenReady()){status.setText("设备已锁屏或熄屏，本次未开始复习。");return;}
    if(card==null){status.setText("当前没有AnkiDroid安排的可复习卡片。");return;}
    pane=new ReviewPane(this,card,media,worker,()->{android.util.Log.d("AnkiGateActivity","manual answer confirmed; finish");finish();},()->{
     android.util.Log.d("AnkiGateActivity","manual global stop");
     getSharedPreferences("gate",0).edit().putBoolean("enabled",false).apply();
     if(GateService.instance!=null)GateService.instance.stopAll();
     finish();
    });
    setContentView(pane);
   });
  }catch(Exception e){runOnUiThread(()->{if(request==generation&&!isFinishing()&&!isDestroyed())status.setText("复习未开始："+e);});}});
 }
 @Override protected void onResume(){
  super.onResume();if(rejected||isFinishing())return;
  if(GateService.instance!=null&&GateService.instance.hasGate()){rejectExisting();return;}
  visible=true;
  if(GateService.instance!=null)GateService.instance.ownActivity(true);
  if(!begun)begin();
 }
 @Override protected void onPause(){if(!rejected){visible=false;if(GateService.instance!=null)GateService.instance.ownActivity(false);}super.onPause();}
 @Override protected void onStop(){
  generation++;if(reading!=null){reading.cancel(false);reading=null;}
  if(pane!=null){ReviewStats.finished(this);pane.dispose();pane=null;}
  if(!rejected){inProgress=false;if(GateService.instance!=null)GateService.instance.ownActivity(false);}
  if(!isFinishing())finish();
  super.onStop();
 }
 @Override protected void onDestroy(){if(!rejected)inProgress=false;worker.shutdown();super.onDestroy();}
}
