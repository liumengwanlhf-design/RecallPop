package dev.ankigate;

import android.content.Context;
import android.content.SharedPreferences;
import java.time.LocalDate;

/** Application-confirmed completions only; AnkiDroid remains the review authority. */
final class ReviewStats {
 static synchronized void finished(Context context){
  SharedPreferences p=context.getSharedPreferences("gate",0);
  p.edit().putLong("protected_until",Math.max(p.getLong("protected_until",0),System.currentTimeMillis()+GatePolicy.PROTECTION_MS)).apply();
 }
 static synchronized void confirmed(Context context,int ease){
  SharedPreferences p=context.getSharedPreferences("gate",0);String day=LocalDate.now().toString();
  int count=day.equals(p.getString("complete_day",""))?p.getInt("complete_count",0):0;
  SharedPreferences.Editor edit=p.edit().putString("complete_day",day).putInt("complete_count",count+1);
  if(QualifiedPolicy.accepts(p.getInt("qualified_threshold",3),ease)){int q=day.equals(p.getString("qualified_day",""))?p.getInt("qualified_count",0):0;edit.putString("qualified_day",day).putInt("qualified_count",q+1);}
  edit.apply();
 }
 static int today(Context context){SharedPreferences p=context.getSharedPreferences("gate",0);return LocalDate.now().toString().equals(p.getString("complete_day",""))?p.getInt("complete_count",0):0;}
 static int qualifiedToday(Context context){SharedPreferences p=context.getSharedPreferences("gate",0);return LocalDate.now().toString().equals(p.getString("qualified_day",""))?p.getInt("qualified_count",0):0;}
}
