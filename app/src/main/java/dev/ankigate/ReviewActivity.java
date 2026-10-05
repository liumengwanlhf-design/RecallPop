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
 static ReviewActivity current;
 final ExecutorService worker=Executors.newSingleThreadExecutor();
 ReviewPane pane;ReviewBatch batch;
 TextView status;
 CardRead.Request reading;
 final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
 final Runnable progress=new Runnable(){public void run(){if(!visible||isFinishing()||isDestroyed())return;if(pane==null&&CardRead.busy())status.setText("正在取第"+(batch==null?"1":batch.position())+"张\n"+CardRead.status()+"\n可以结束本轮；接口结束前不会开始第二次读取。");main.postDelayed(this,1000);}};
 boolean begun,rejected;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  if(inProgress||(GateService.instance!=null&&GateService.instance.hasCard())){rejectExisting();return;}
  inProgress=true;current=this;
  waiting("准备读取AnkiDroid安排的卡片…");
 }
 void waiting(String text){LinearLayout root=ShellUi.column(this);ShellUi.safeInsets(root);root.addView(ShellUi.title(this,"本轮 "+(batch==null?"1/"+ReviewBatch.limit(getSharedPreferences("gate",0).getInt("batch_limit",1)):batch.position())));status=ShellUi.text(this,text,16);root.addView(status);root.addView(ShellUi.button(this,"结束本轮",v->finishBatch()));root.addView(ShellUi.button(this,"停止全部介入",v->stopAll()));setContentView(root);}
 void finishBatch(){if(batch!=null)batch.finish(this);finish();}
 static void stopCurrent(){ReviewActivity activity=current;if(activity==null)return;if(activity.reading!=null)activity.reading.cancel("用户停止全部介入");activity.finishBatch();}
 void stopAll(){if(CardRead.active!=null)CardRead.active.cancel("用户停止全部介入");CardRead.nextDelay=null;getSharedPreferences("gate",0).edit().putBoolean("enabled",false).apply();if(GateService.instance!=null)GateService.instance.stopAll();finishBatch();}
 void rejectExisting(){rejected=true;Toast.makeText(this,"已有复习正在进行，请先完成或停止介入。",Toast.LENGTH_LONG).show();finish();}
 boolean screenReady(){
  return ((PowerManager)getSystemService(POWER_SERVICE)).isInteractive()&&!((KeyguardManager)getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked();
 }
 boolean canSubmit(){return visible&&!isFinishing()&&screenReady();}
 void begin(){
  begun=true;
  if(!screenReady()){status.setText("请在亮屏且已解锁时主动复习。");return;}
  if(CardRead.busy()){status.setText(CardRead.status()+"\n本次未启动新的读取；接口结束后请返回重新打开。");return;}
  if(checkSelfPermission(AnkiApi.PERMISSION)!=PackageManager.PERMISSION_GRANTED){status.setText("尚未授权AnkiDroid。请返回主界面授权并选择牌组。");return;}
  long deck=getSharedPreferences("gate",0).getLong("deck",-1);
  if(deck<0){status.setText("尚未选择牌组。请返回主界面配置。");return;}
  batch=new ReviewBatch(deck,getSharedPreferences("gate",0).getInt("batch_limit",1));readNext();
 }
 void readNext(){
  if(batch==null||batch.ended||!canSubmit())return;
  waiting("正在取第"+batch.position()+"张；可以结束本轮。");
  reading=CardRead.start(this,"主动复习 "+batch.position(),batch.deck,(request,card,media,error)->{
    reading=null;
    if(batch.ended||!visible||isFinishing()||isDestroyed())return;
    if(request.life.cancelled()){status.setText("读取已取消（"+request.life.reason+"），接口已结束；本次未展示。可以返回后重新取卡。");return;}
    if(error!=null){status.setText("复习未开始："+error.getClass().getSimpleName());return;}
    if(!screenReady()){status.setText("设备已锁屏或熄屏，本次未开始复习。");return;}
    if(card==null){Toast.makeText(this,"没有更多到期卡，本轮已结束。",Toast.LENGTH_SHORT).show();finishBatch();return;}
    pane=new ReviewPane(this,card,media,worker,batch,this::scoreConfirmed,this::finishBatch,this::stopAll);
    setContentView(pane);
  });
  if(reading==null)status.setText("上次读取或牌组归还尚未结束；本次没有排队，请结束本轮后重试。");
 }
 void scoreConfirmed(){
  if(batch.ended)return;boolean more=batch.confirmed();if(!more||!canSubmit()){finishBatch();return;}
  ReviewPane old=pane;pane=null;waiting("正在结束上一张，随后取第"+batch.position()+"张；可以结束本轮。");
  old.dispose(()->{if(batch.ended)return;if(canSubmit())readNext();else finishBatch();});
 }
 @Override protected void onResume(){
  super.onResume();if(rejected||isFinishing())return;
  if(GateService.instance!=null&&GateService.instance.hasCard()){rejectExisting();return;}
  visible=true;
  if(GateService.instance!=null)GateService.instance.ownActivity(true);
  if(!begun)begin();
  main.removeCallbacks(progress);main.post(progress);
 }
 @Override protected void onPause(){main.removeCallbacks(progress);if(!rejected){if(reading!=null)reading.cancel(screenReady()?"主动页面已离开":"设备已锁屏或熄屏");if(current==this){visible=false;if(GateService.instance!=null)GateService.instance.ownActivity(false);}}super.onPause();}
 @Override protected void onStop(){
  if(reading!=null){reading.cancel("主动复习已返回");reading.detach();reading=null;}
  if(batch!=null)batch.finish(this);if(pane!=null){pane.dispose();pane=null;}
  if(current==this){current=null;visible=false;inProgress=false;if(GateService.instance!=null)GateService.instance.ownActivity(false);}
  if(!isFinishing())finish();
  super.onStop();
 }
 @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);if(current==this){current=null;visible=false;inProgress=false;if(GateService.instance!=null)GateService.instance.ownActivity(false);}worker.shutdown();super.onDestroy();}
}
