package dev.ankigate;

import android.app.Activity;
import android.os.*;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.LinearLayout;

/** A finite lockscreen round. The same Activity and touch state serve every card. */
public final class WakeReviewActivity extends Activity {
 GateService owner;GateService.PendingWake review;ReviewPane pane;
 long token;boolean visible,shown,touched;long opened;
 final Handler main=new Handler(Looper.getMainLooper());
 void clearInitialScreenHold(){getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
 final Runnable idle=()->{clearInitialScreenHold();if(!touched&&pane!=null&&!pane.submitted.get()){android.util.Log.d("AnkiGateActivity","wake idle 30s; finish without score");finish();}};
 @Override public void onCreate(Bundle state){
  super.onCreate(state);owner=GateService.instance;token=getIntent().getLongExtra("token",-1);
  if(owner==null||(review=owner.claimWake(token,this))==null){finish();return;}
  setShowWhenLocked(true);setTurnScreenOn(true);opened=SystemClock.elapsedRealtime();
  showNext();pane.message.setText("保留系统锁；未触摸最多暂亮屏30秒，触摸后由系统管理。");
 }
 void showNext(){pane=new ReviewPane(this,review.card,review.media,owner.worker,review.batch,this::scoreConfirmed,this::finish,owner::stopAll);setContentView(pane);}
 void scoreConfirmed(){
  if(review.batch.ended)return;boolean more=review.batch.confirmed();if(!more||!canSubmit()){finish();return;}
  ReviewPane old=pane;pane=null;LinearLayout root=ShellUi.column(this);ShellUi.safeInsets(root);root.addView(ShellUi.title(this,"本轮 "+review.batch.position()));root.addView(ShellUi.text(this,"正在读取下一张；系统锁仍保留。",18));root.addView(ShellUi.button(this,"结束本轮",v->finish()));root.addView(ShellUi.button(this,"停止全部介入",v->owner.stopAll()));setContentView(root);
  old.dispose(()->{if(review.batch.ended||isFinishing())return;if(canSubmit())owner.readNextAutomatic(review.batch,true,review.debug);else finish();});
 }
 @Override protected void onResume(){super.onResume();visible=true;confirmVisible();}
 @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)confirmVisible();}
 void confirmVisible(){
  if(shown||pane==null||isFinishing()||!visible||!hasWindowFocus())return;
  if(!((PowerManager)getSystemService(POWER_SERVICE)).isInteractive()){
   if(SystemClock.elapsedRealtime()-opened<9000)main.postDelayed(this::confirmVisible,200);return;
  }
  if(!owner.wakeDisplayed(token)){finish();return;}
  shown=true;setTurnScreenOn(false);if(!touched){getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);main.postDelayed(idle,WakePolicy.IDLE_MS);}
 }
 @Override public boolean dispatchTouchEvent(MotionEvent event){
  if(event.getActionMasked()==MotionEvent.ACTION_DOWN&&!touched){touched=true;clearInitialScreenHold();main.removeCallbacks(idle);android.util.Log.d("AnkiGateActivity","wake real touch; cancel initial idle exit");}
  return super.dispatchTouchEvent(event);
 }
 boolean canSubmit(){return visible&&shown&&!isFinishing()&&hasWindowFocus()&&owner!=null&&owner.enabled()&&owner.wakeMode()&&owner.pendingWake==review&&((PowerManager)getSystemService(POWER_SERVICE)).isInteractive();}
 @Override protected void onPause(){visible=false;clearInitialScreenHold();if(owner!=null&&owner.fetchRequest!=null&&owner.fetchIsWake)owner.fetchRequest.cancel("锁屏卡页面已离开");super.onPause();}
 @Override protected void onStop(){
  clearInitialScreenHold();main.removeCallbacksAndMessages(null);if(pane!=null){pane.dispose();pane=null;}
  if(owner!=null)owner.wakeEnded(token);if(!isFinishing())finish();super.onStop();
 }
 @Override protected void onDestroy(){clearInitialScreenHold();main.removeCallbacksAndMessages(null);super.onDestroy();}
}
