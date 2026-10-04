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
  long note,reps;int ord,buttons;String question,answer,css;List<String> media=new ArrayList<>();
  Uri uri(){return BASE.buildUpon().appendPath("notes").appendPath(""+note).appendPath("cards").appendPath(""+ord).build();}
 }
 interface Read<T>{T consume(Cursor c)throws Exception;}
 // All cursor work stays inside the unstable-client lifetime. Only failed reads recover once.
 <T>T read(Uri uri,String[] projection,String selection,String[] args,Read<T> read)throws Exception{
  for(int attempt=0;attempt<2;attempt++){
   try(ContentProviderClient provider=resolver.acquireUnstableContentProviderClient(BASE)){
    if(provider==null)throw new IllegalStateException("无法连接AnkiDroid接口；请检查AnkiDroid关联启动或打开原应用后再试");
    try(Cursor c=provider.query(uri,projection,selection,args,null)){
     if(c==null)throw new IllegalStateException("AnkiDroid接口返回null，不能判定为无到期卡");
     return read.consume(c);
    }
   }catch(DeadObjectException e){if(attempt==1)throw e;android.util.Log.w("AnkiGateApi","unstable provider died; retry read once");}
  }
  throw new IllegalStateException("AnkiDroid读取失败");
 }
 Map<Long,String> decks()throws Exception{
  return read(Uri.withAppendedPath(BASE,"decks"),new String[]{"deck_id","deck_name"},null,null,c->{
   Map<Long,String> result=new LinkedHashMap<>();while(c.moveToNext())result.put(c.getLong(0),c.getString(1));return result;
  });
 }
 Card next(long deck)throws Exception{
  long started=android.os.SystemClock.elapsedRealtime();android.util.Log.d("AnkiGateApi","schedule begin deck="+deck);
  Card card=read(Uri.withAppendedPath(BASE,"schedule"),new String[]{"note_id","ord","button_count","media_files"},"limit=?,deckID=?",new String[]{"1",""+deck},c->{
   android.util.Log.d("AnkiGateApi","schedule unstable provider rows="+c.getCount()+" elapsedMs="+(android.os.SystemClock.elapsedRealtime()-started));
   if(!c.moveToFirst())return null;
   Card result=new Card();result.note=c.getLong(0);result.ord=c.getInt(1);result.buttons=c.getInt(2);
   JSONArray files=new JSONArray(c.getString(3));for(int i=0;i<files.length();i++)result.media.add(files.getString(i));return result;
  });
  if(card==null)return null;
  read(card.uri(),new String[]{"question","answer","reps"},null,null,c->{
   if(!c.moveToFirst())throw new IllegalStateException("无法读取卡面");card.question=c.getString(0);card.answer=c.getString(1);card.reps=c.getLong(2);return null;
  });
  long model=read(BASE.buildUpon().appendPath("notes").appendPath(""+card.note).build(),new String[]{"mid"},null,null,c->{if(!c.moveToFirst())throw new IllegalStateException("无法读取卡片模板ID");return c.getLong(0);});
  card.css=read(BASE.buildUpon().appendPath("models").appendPath(""+model).build(),new String[]{"css"},null,null,c->{if(!c.moveToFirst())throw new IllegalStateException("无法读取卡片样式");return c.getString(0);});
  card.question="<style>"+card.css+"</style>"+card.question;card.answer="<style>"+card.css+"</style>"+card.answer;
  if(card.question.contains("[anki:")||card.answer.contains("[anki:"))throw new IllegalStateException("本卡包含未支持的Anki语音/媒体标签，未开始复习");
  if(card.buttons!=4)throw new IllegalStateException("不支持的评分按钮数量："+card.buttons);return card;
 }
 long reps(Card card)throws Exception{return read(card.uri(),new String[]{"reps"},null,null,c->{if(!c.moveToFirst())throw new IllegalStateException("无法确认复习次数");return c.getLong(0);});}
 void answer(Card card,int ease,long elapsed)throws Exception{
  if(reps(card)!=card.reps)throw new IllegalStateException("卡片已在其他地方复习，请退出后重新取卡");
  ContentValues v=new ContentValues();v.put("note_id",card.note);v.put("ord",card.ord);v.put("answer_ease",ease);v.put("time_taken",elapsed);
  // Never recover/retry this write: remote death may happen after the score committed.
  try(ContentProviderClient provider=resolver.acquireUnstableContentProviderClient(BASE)){
   if(provider==null)throw new IllegalStateException("评分接口无法连接，本次未提交");
   provider.update(Uri.withAppendedPath(BASE,"schedule"),v,null,null);
  }
  if(reps(card)!=card.reps+1)throw new IllegalStateException("提交结果未确认；不再重试，请在AnkiDroid检查卡片历史");
  ReviewStats.confirmed(context,ease);android.util.Log.d("AnkiGateApi","answer confirmed repsDelta=1 ease="+ease+" elapsedMs="+elapsed);
 }
}
