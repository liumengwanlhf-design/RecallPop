package dev.ankigate;

import android.content.Context;
import android.os.*;

/** One process-wide read, never a scoring task or an unbounded request queue. */
final class CardRead {
 static final Handler main=new Handler(Looper.getMainLooper());
 static Request active;
 static ReviewSelection heldSelection;
 static TestDelay nextDelay;
 record TestDelay(String stage,int millis,boolean ignoreCancel){}
 static boolean testNext(Context c,String stage,int millis,boolean ignoreCancel){
  if((c.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)==0||busy()||millis<0||millis>45000||stage==null||!java.util.Set.of("读取Anki队列","读取卡面","读取模板","枚举媒体","校验媒体","等待显示").contains(stage))return false;
  nextDelay=millis==0?null:new TestDelay(stage,millis,ignoreCancel);return true;
 }
 interface Listener {void ended(Request request,AnkiApi.Card card,MediaAccess media,Exception error);}
 static final class Request {
  final Context context;final String source;final ReadLifecycle life;final CancellationSignal signal=new CancellationSignal();final ReviewSelection selection;
  Listener listener;AnkiApi.Card card;MediaAccess media;Exception error;
  TestDelay testDelay;
  Request(Context c,String source,long deck,Listener listener){context=c.getApplicationContext();this.source=source;this.listener=listener;selection=new ReviewSelection(deck);life=new ReadLifecycle(SystemClock.elapsedRealtime());testDelay=nextDelay;nextDelay=null;}
  void check(){if(!life.accepts(SystemClock.elapsedRealtime())){if(!life.cancelled())main.post(()->cancel("读取超时（30秒）"));throw new OperationCanceledException();}signal.throwIfCanceled();}
  void stage(String stage){check();life.stage=stage;main.post(()->{if(active==this&&!life.cancelled())DiagnosticLog.event(context,"取卡阶段："+source+" / "+stage);});
   TestDelay delay=testDelay;if(delay!=null&&delay.stage().equals(stage)){testDelay=null;long until=SystemClock.elapsedRealtime()+delay.millis();while(SystemClock.elapsedRealtime()<until){if(!delay.ignoreCancel())check();SystemClock.sleep(Math.min(100,Math.max(1,until-SystemClock.elapsedRealtime())));}check();}
  }
  void cancel(String reason){if(active!=this||!life.cancel(reason))return;DiagnosticLog.event(context,"取卡取消："+source+" / "+reason+" / "+life.stage);
   // cancel() may synchronously enter a provider Binder; never call it on the UI thread.
   new Thread(()->{try{signal.cancel();}catch(RuntimeException e){DiagnosticLog.event(context,"读取取消接口异常："+e.getClass().getSimpleName());}finally{main.post(()->{life.cancelEnded();complete(this);});}},"card-read-cancel").start();
  }
  String status(){return life.status(SystemClock.elapsedRealtime());}
  void detach(){listener=null;}
 }
 static boolean busy(){return active!=null||(heldSelection!=null&&heldSelection.closing.get()&&!heldSelection.ended);}
 static String status(){return active!=null?active.status():busy()?"正在结束复习，等待Anki牌组恢复；不会开始新的读取。":"";}
 static void closeSelection(Context context,ReviewSelection selection,java.util.concurrent.Executor restorer){
  closeSelection(context,selection,restorer,null);
 }
 static void closeSelection(Context context,ReviewSelection selection,java.util.concurrent.Executor restorer,Runnable after){
  if(selection==null){if(after!=null)main.post(after);return;}
  if(after!=null){if(selection.ended){main.post(after);return;}selection.afterClose=after;}
  if(!selection.beginClose())return;Context app=context.getApplicationContext();
  Runnable close=()->{boolean interrupted=selection.awaitScore();
   try{new AnkiApi(app).releaseSelection(selection);}
   finally{selection.ended=true;if(interrupted)Thread.currentThread().interrupt();main.post(()->{if(heldSelection==selection)heldSelection=null;Runnable next=selection.afterClose;selection.afterClose=null;if(next!=null)next.run();GateService service=GateService.instance;if(service!=null&&!service.destroyed){service.reevaluate();service.scheduleWake();}});}};
  if(restorer!=null)try{restorer.execute(close);return;}catch(java.util.concurrent.RejectedExecutionException ignored){}
  // One closing selection blocks new reads; a destroyed owner's executor may reject cleanup.
  new Thread(close,"anki-selection-close").start();
 }
 static final Runnable tick=new Runnable(){public void run(){Request r=active;if(r==null)return;if(r.life.expired(SystemClock.elapsedRealtime()))r.cancel("读取超时（30秒）");main.postDelayed(this,1000);}};
 static Request start(Context context,String source,long deck,Listener listener){
  if(active!=null||(heldSelection!=null&&!heldSelection.ended))return null;
  Request r=new Request(context,source,deck,listener);active=r;heldSelection=r.selection;DiagnosticLog.event(r.context,"尝试取卡："+source);main.postDelayed(tick,1000);
  new Thread(()->{try{r.card=new AnkiApi(r.context).next(deck,r);r.check();r.media=new MediaAccess(r.context);if(r.card!=null)r.media.prepare(r.card.media,r);r.stage("等待显示");r.check();}
   catch(Exception e){r.error=e;}
   finally{main.post(()->{if(!r.life.accepts(SystemClock.elapsedRealtime())&&!r.life.cancelled())r.cancel("读取超时（30秒）");r.life.readerEnded();complete(r);});}
  },"card-read").start();return r;
 }
 static void complete(Request r){
  if(active!=r||!r.life.released())return;active=null;main.removeCallbacks(tick);
  String end=r.life.cancelled()?"已取消，接口已结束；迟到结果丢弃":r.error!=null?"读取失败："+r.error.getClass().getSimpleName():r.card==null?"无到期卡":"读取完成，等待显示";
  DiagnosticLog.event(r.context,"取卡结束："+r.source+" / "+end);
  Listener listener=r.listener;r.listener=null;try{if(listener!=null)listener.ended(r,r.life.cancelled()?null:r.card,r.media,r.error);}
  finally{if(!r.selection.handedOff)closeSelection(r.context,r.selection,null);}
  GateService service=GateService.instance;if(service!=null&&!service.destroyed){service.reevaluate();service.scheduleWake();}
 }
}
