package dev.ankigate;

import android.content.Context;

/** A finite, foreground round; only confirmed scores advance it. */
final class ReviewBatch {
 final long deck;final int limit;int completed;boolean ended;
 ReviewBatch(long deck,int limit){this.deck=deck;this.limit=limit(limit);}
 static int limit(int value){return value==3||value==5||value==10?value:1;}
 String position(){return (completed+1)+"/"+limit;}
 boolean confirmed(){if(ended)return false;completed++;return completed<limit;}
 void finish(Context context){if(ended)return;ended=true;ReviewStats.finished(context);DiagnosticLog.event(context,"本轮结束：已完成"+completed+"/"+limit);}
}
