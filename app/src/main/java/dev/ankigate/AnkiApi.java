package dev.ankigate;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.DeadObjectException;
import org.json.JSONArray;
import java.util.*;

final class AnkiApi {
 static final String PERMISSION="com.ichi2.anki.permission.READ_WRITE_DATABASE";
 static final Uri BASE=Uri.parse("content://com.ichi2.anki.flashcards");
 final ContentResolver resolver;final Context context;
 AnkiApi(Context context){this.context=context.getApplicationContext();resolver=context.getContentResolver();}
 static final class Card {
  long note,reps,deck;int ord,buttons;String question,answer,css;List<String> media=new ArrayList<>();ReviewSelection selection;
  Uri uri(){return BASE.buildUpon().appendPath("notes").appendPath(""+note).appendPath("cards").appendPath(""+ord).build();}
 }
 interface Read<T>{T consume(Cursor c)throws Exception;}
 // All cursor work stays inside the unstable-client lifetime. Only failed reads recover once.
 <T>T read(Uri uri,String[] projection,String selection,String[] args,Read<T> read)throws Exception{
  return read(uri,projection,selection,args,null,read);
 }
 <T>T read(Uri uri,String[] projection,String selection,String[] args,CardRead.Request request,Read<T> read)throws Exception{
  for(int attempt=0;attempt<2;attempt++){
   if(request!=null)request.check();
   try(ContentProviderClient provider=resolver.acquireUnstableContentProviderClient(BASE)){
    if(provider==null)throw new IllegalStateException("无法连接AnkiDroid接口；请检查AnkiDroid关联启动或打开原应用后再试");
    try(Cursor c=provider.query(uri,projection,selection,args,null,request==null?null:request.signal)){
     if(request!=null)request.check();
     if(c==null)throw new IllegalStateException("AnkiDroid接口返回null，不能判定为无到期卡");
     return read.consume(c);
    }
   }catch(DeadObjectException e){if(request!=null)request.check();if(attempt==1)throw e;android.util.Log.w("AnkiGateApi","unstable provider died; retry read once");}
  }
  throw new IllegalStateException("AnkiDroid读取失败");
 }
 Map<Long,String> decks()throws Exception{
  return read(Uri.withAppendedPath(BASE,"decks"),new String[]{"deck_id","deck_name"},null,null,c->{
   Map<Long,String> result=new LinkedHashMap<>();while(c.moveToNext())result.put(c.getLong(0),c.getString(1));return result;
  });
 }
 Card next(long deck,CardRead.Request request)throws Exception{
  request.stage("读取Anki队列");
  try(ContentProviderClient provider=resolver.acquireUnstableContentProviderClient(BASE)){
   if(provider==null)throw new IllegalStateException("无法连接AnkiDroid接口");
   try(Cursor current=provider.query(SELECTED,new String[]{"deck_id"},null,null,null,request.signal)){
    request.check();if(current==null||!current.moveToFirst())throw new IllegalStateException("无法确认Anki当前选中牌组");
    request.selection.opened(current.getLong(0));
   }
   if(request.selection.previous!=deck)select(provider,deck);
   request.check();
  }
  long started=android.os.SystemClock.elapsedRealtime();android.util.Log.d("AnkiGateApi","schedule begin deck="+deck);
  Card card=read(Uri.withAppendedPath(BASE,"schedule"),new String[]{"note_id","ord","button_count","media_files"},"limit=?",new String[]{"1"},request,c->{
   android.util.Log.d("AnkiGateApi","schedule unstable provider rows="+c.getCount()+" elapsedMs="+(android.os.SystemClock.elapsedRealtime()-started));
   request.check();if(!c.moveToFirst())return null;request.check();
   Card result=new Card();result.deck=deck;result.selection=request.selection;result.note=c.getLong(0);result.ord=c.getInt(1);result.buttons=c.getInt(2);
   JSONArray files=new JSONArray(c.getString(3));for(int i=0;i<files.length();i++){request.check();result.media.add(files.getString(i));}return result;
  });
  if(card==null)return null;
  request.stage("读取卡面");
  read(card.uri(),new String[]{"question","answer","reps"},null,null,request,c->{
   request.check();if(!c.moveToFirst())throw new IllegalStateException("无法读取卡面");request.check();card.question=c.getString(0);card.answer=c.getString(1);card.reps=c.getLong(2);return null;
  });
  request.stage("读取模板");
  long model=read(BASE.buildUpon().appendPath("notes").appendPath(""+card.note).build(),new String[]{"mid"},null,null,request,c->{request.check();if(!c.moveToFirst())throw new IllegalStateException("无法读取卡片模板ID");request.check();return c.getLong(0);});
  card.css=read(BASE.buildUpon().appendPath("models").appendPath(""+model).build(),new String[]{"css"},null,null,request,c->{request.check();if(!c.moveToFirst())throw new IllegalStateException("无法读取卡片样式");request.check();return c.getString(0);});
  card.question="<style>"+card.css+"</style>"+card.question;card.answer="<style>"+card.css+"</style>"+card.answer;
  if(card.question.contains("[anki:")||card.answer.contains("[anki:"))throw new IllegalStateException("本卡包含未支持的Anki语音/媒体标签，未开始复习");
  if(card.buttons!=4)throw new IllegalStateException("不支持的评分按钮数量："+card.buttons);return card;
 }
 long reps(Card card)throws Exception{return read(card.uri(),new String[]{"reps"},null,null,c->{if(!c.moveToFirst())throw new IllegalStateException("无法确认复习次数");return c.getLong(0);});}
 static final Uri SELECTED=Uri.withAppendedPath(BASE,"selected_deck");
 static final class ScoreNotSubmittedException extends IllegalStateException {ScoreNotSubmittedException(String reason){super(reason);}}
 long selected(ContentProviderClient provider)throws Exception{try(Cursor c=provider.query(SELECTED,new String[]{"deck_id"},null,null,null)){if(c==null||!c.moveToFirst())throw new IllegalStateException("无法确认Anki当前选中牌组");return c.getLong(0);}}
 void select(ContentProviderClient provider,long deck)throws Exception{ContentValues value=new ContentValues();value.put("deck_id",deck);if(provider.update(SELECTED,value,null,null)!=1)throw new IllegalStateException("无法选择本次卡片牌组");}
 void restoreSelection(ContentProviderClient provider,ReviewSelection selection){if(selection.previous==selection.target)return;try{if(selection.restoreWhen(selected(provider))){select(provider,selection.previous);android.util.Log.d("AnkiGateApi","temporary deck selection restored");}else{DiagnosticLog.event(context,"临时牌组选择未恢复：其他操作已改变选择");}}catch(Exception e){DiagnosticLog.event(context,"临时牌组选择恢复未确认："+e.getClass().getSimpleName());}}
 void releaseSelection(ReviewSelection selection){if(!selection.opened||selection.previous==selection.target)return;try(ContentProviderClient provider=resolver.acquireUnstableContentProviderClient(BASE)){
   if(provider==null)throw new IllegalStateException("恢复牌组接口不可用");restoreSelection(provider,selection);
  }catch(Exception e){DiagnosticLog.event(context,"临时牌组选择恢复未确认："+e.getClass().getSimpleName());}}
 void answer(Card card,int ease,long elapsed)throws Exception{
  if(reps(card)!=card.reps){DiagnosticLog.event(context,"评分前拒绝：复习次数已变化；未提交");throw new ScoreNotSubmittedException("卡片已在其他地方复习；本次未提交，请退出重新取卡");}
  ContentValues v=new ContentValues();v.put("note_id",card.note);v.put("ord",card.ord);v.put("answer_ease",ease);v.put("time_taken",elapsed);
  // Never recover/retry this write: remote death may happen after the score committed.
  try(ContentProviderClient provider=resolver.acquireUnstableContentProviderClient(BASE)){
   if(provider==null)throw new IllegalStateException("评分接口无法连接，本次未提交");
   long previous=selected(provider);
   DiagnosticLog.event(context,"评分前检查：目标牌组与当前选择相同="+(previous==card.deck)+"；等待="+(elapsed/1000)+"秒");
    if(previous!=card.deck){DiagnosticLog.event(context,"评分前拒绝：选中牌组已变化；未提交");throw new ScoreNotSubmittedException("Anki选中牌组已被其他操作改变；本次未提交，请退出重新取卡");}
    // AnkiDroid 2.24's legacy answer path uses the CURRENT queue's first scheduling state.
    // Keep the deck selected since reading; still reject external queue changes before writing.
    try(Cursor queue=provider.query(Uri.withAppendedPath(BASE,"schedule"),new String[]{"note_id","ord"},"limit=?",new String[]{"1"},null)){
     if(queue==null){DiagnosticLog.event(context,"评分前拒绝：队列接口返回null；未提交");throw new ScoreNotSubmittedException("Anki队列接口未返回结果；本次未提交，请退出重新取卡");}
     if(!queue.moveToFirst()){DiagnosticLog.event(context,"评分前拒绝：队列为空；未提交");throw new ScoreNotSubmittedException("Anki当前队列为空；本次未提交，请退出重新取卡");}
     if(queue.getLong(0)!=card.note||queue.getInt(1)!=card.ord){DiagnosticLog.event(context,"评分前拒绝：首卡不匹配；未提交");throw new ScoreNotSubmittedException("当前复习首卡已改变；本次未提交，请退出重新取卡");}
    }
    if(reps(card)!=card.reps){DiagnosticLog.event(context,"评分前拒绝：复习次数已变化；未提交");throw new ScoreNotSubmittedException("卡片已被其他操作复习；本次未提交");}
    provider.update(Uri.withAppendedPath(BASE,"schedule"),v,null,null);
    if(reps(card)!=card.reps+1)throw new IllegalStateException("提交结果未确认；不再重试，请在AnkiDroid检查卡片历史");
    ReviewStats.confirmed(context,ease);android.util.Log.d("AnkiGateApi","answer confirmed repsDelta=1 ease="+ease+" elapsedMs="+elapsed);
  }
 }
}
