package dev.ankigate;

import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;
import android.webkit.*;
import android.widget.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.*;

final class ReviewPane extends LinearLayout {
 final WebView web; final LinearLayout controls; final TextView message;
 final AtomicBoolean submitted=new AtomicBoolean();
 final AnkiApi.Card card;final ExecutorService worker;
 boolean failed,disposed,renderReady; final long started=SystemClock.elapsedRealtime();
 ReviewPane(Context c,AnkiApi.Card card,MediaAccess media,ExecutorService worker,ReviewBatch batch,Runnable success,Runnable end,Runnable stop){
  super(c);this.card=card;this.worker=worker;setOrientation(VERTICAL);setBackgroundColor(ShellUi.BACKGROUND);
  int padding=ShellUi.dp(c,14);setPadding(padding,padding,padding,padding);
  ShellUi.safeInsets(this);
  LinearLayout heading=new LinearLayout(c);heading.setGravity(android.view.Gravity.CENTER_VERTICAL);addView(heading);
  heading.addView(ShellUi.title(c,"本轮 "+batch.position()),new LayoutParams(0,LayoutParams.WRAP_CONTENT,1));
  heading.addView(ShellUi.button(c,"结束本轮",v->end.run()));
  heading.addView(ShellUi.button(c,"停止全部介入",v->stop.run()));
  message=ShellUi.text(c,"先回忆，再显示答案",14);addView(message);
  web=new WebView(c);addView(web,new LayoutParams(LayoutParams.MATCH_PARENT,0,1));
  web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
  web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.getSettings().setMediaPlaybackRequiresUserGesture(false);
  web.addJavascriptInterface(new Object(){
   @JavascriptInterface public void command(String value){post(()->play(value));}
   @JavascriptInterface public void mediaFailed(String value){post(()->error("音频/图片加载失败："+value,stop));}
  },"GateAudio");
  web.setWebViewClient(new WebViewClient(){
   @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
    Uri url=r.getUrl();android.util.Log.d("AnkiGateWeb","request scheme="+url.getScheme()+" host="+url.getHost()+" mainFrame="+r.isForMainFrame());
    if("anki.invalid".equals(url.getHost())){
     if("/favicon.ico".equals(url.getPath())&&!media.files.containsKey("favicon.ico"))return new WebResourceResponse("image/x-icon",null,204,"No Content",java.util.Collections.emptyMap(),new java.io.ByteArrayInputStream(new byte[0]));
     WebResourceResponse response=media.read(url);if(response.getStatusCode()>=400)post(()->warning("部分附属资源不可读："+url.getLastPathSegment()));return response;
    }
    return null;
   }
   @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){String url=r.getUrl().toString();if(url.startsWith("playsound:")){play(Uri.decode(url.substring(10)));return true;}return true;}
   @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){post(()->{
    if(r.isForMainFrame())error("卡面加载失败："+e.getDescription(),stop);
    else warning("部分附属资源未加载；必需音频或图片失败时会停止评分");
   });}
   @Override public void onPageFinished(WebView v,String url){if(!failed&&!disposed)renderReady=true;}
  });
  controls=new LinearLayout(c);controls.setOrientation(VERTICAL);addView(controls);
  controls.addView(ShellUi.button(c,"显示答案",v->{if(disposed||failed||!renderReady)return;load(card.answer);controls.removeAllViews();
   String[] labels={"重来","困难","良好","简单"};LinearLayout row=new LinearLayout(c);controls.addView(row);
   for(int i=0;i<4;i++){final int ease=i+1;Button b=ShellUi.button(c,labels[i],view->{
    if(c instanceof ReviewActivity&&!((ReviewActivity)c).canSubmit())return;
    if(c instanceof WakeReviewActivity&&!((WakeReviewActivity)c).canSubmit())return;
    if(c instanceof GateService&&!((GateService)c).active())return;
    if(disposed||failed||!renderReady||!submitted.compareAndSet(false,true))return;
    if(!card.selection.beginScore()){error("本次评分未提交：复习正在结束，请退出重新取卡。",stop);return;}
    for(int k=0;k<row.getChildCount();k++)row.getChildAt(k).setEnabled(false);message.setText("提交并确认AnkiDroid复习记录…");
    try{worker.execute(()->{try{new AnkiApi(c).answer(card,ease,SystemClock.elapsedRealtime()-started);post(()->{if(!disposed)success.run();});}
     catch(Exception ex){post(()->error((ex instanceof AnkiApi.ScoreNotSubmittedException?"本次评分未提交。\n":"评分结果未确认，不会重试。请检查AnkiDroid历史。\n")+ex,stop));}
     finally{card.selection.scoreEnded();}});}catch(java.util.concurrent.RejectedExecutionException ex){card.selection.scoreEnded();error("本次评分未提交：复习已经结束。",stop);}
   });row.addView(b,new LayoutParams(0,LayoutParams.WRAP_CONTENT,1));}
  }));
  load(card.question);
  card.selection.handedOff=true;
 }
 void error(String reason,Runnable stop){if(disposed||failed)return;failed=true;getContext().getSharedPreferences("gate",0).edit().putString("error",reason).apply();message.setText(reason);controls.removeAllViews();controls.addView(ShellUi.button(getContext(),"停止并退出",v->stop.run()));}
 void warning(String reason){if(!disposed&&!failed&&!submitted.get())message.setText(reason);}
 void play(String command){if(disposed)return;Matcher m=Pattern.compile("(?:play:)?(?:[qa]:)?(\\d+)$").matcher(command);if(m.find()){
  web.evaluateJavascript("(function(){var a=document.querySelectorAll('audio');var x=a["+m.group(1)+"];if(x)x.play();})()",null);
 }else if(!command.contains(":")){web.evaluateJavascript("new Audio("+org.json.JSONObject.quote("https://anki.invalid/"+Uri.encode(command))+ ").play()",null);}}
 void load(String html){
  renderReady=false;
  web.loadDataWithBaseURL("https://anki.invalid/",CardHtml.render(html),"text/html","UTF-8",null);
 }
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();android.util.Log.d("AnkiGatePane","attached instance="+System.identityHashCode(this)+" context="+getContext().getClass().getSimpleName());}
 @Override protected void onDetachedFromWindow(){android.util.Log.d("AnkiGatePane","detached instance="+System.identityHashCode(this)+" disposed="+disposed+" submitted="+submitted.get());super.onDetachedFromWindow();}
 @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);android.util.Log.d("AnkiGatePane","visibility instance="+System.identityHashCode(this)+" value="+visibility);}
 void dispose(){dispose(null);}
 void dispose(Runnable after){if(disposed)return;disposed=true;CardRead.closeSelection(getContext(),card.selection,worker,after);web.stopLoading();web.loadUrl("about:blank");web.removeJavascriptInterface("GateAudio");web.destroy();}
}
