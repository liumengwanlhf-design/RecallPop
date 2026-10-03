package dev.ankigate;

import android.app.Activity;
import android.os.*;
import android.view.MotionEvent;
import android.view.WindowManager;

/** Internal, already-prepared single card. Never unlocks or requests another card. */
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
  pane=new ReviewPane(this,review.card,review.media,owner.worker,()->{android.util.Log.d("AnkiGateActivity","wake answer confirmed; finish");finish();},owner::stopAll);
  pane.message.setText("保留系统锁；未触摸最多暂亮屏30秒，触摸后由系统管理。");setContentView(pane);
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
 @Override protected void onPause(){visible=false;clearInitialScreenHold();super.onPause();}
 @Override protected void onStop(){
  clearInitialScreenHold();main.removeCallbacksAndMessages(null);if(pane!=null){pane.dispose();pane=null;}
  if(owner!=null)owner.wakeEnded(token);if(!isFinishing())finish();super.onStop();
 }
 @Override protected void onDestroy(){clearInitialScreenHold();main.removeCallbacksAndMessages(null);super.onDestroy();}
}
