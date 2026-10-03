package dev.ankigate;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.WindowInsets;
import android.graphics.Insets;
import android.widget.*;

final class ShellUi {
 static final int BACKGROUND = Color.rgb(247,248,250);
 static final int INK = Color.rgb(28,39,54);
 static final int ACCENT = Color.rgb(56,92,155);
 static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
 static void safeInsets(View view){
  int left=view.getPaddingLeft(),top=view.getPaddingTop(),right=view.getPaddingRight(),bottom=view.getPaddingBottom();
  view.setOnApplyWindowInsetsListener((v,windowInsets)->{
   Insets insets=windowInsets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
   v.setPadding(left+insets.left,top+insets.top,right+insets.right,bottom+insets.bottom);
   return WindowInsets.CONSUMED;
  });
  view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
   public void onViewAttachedToWindow(View v){v.requestApplyInsets();}
   public void onViewDetachedFromWindow(View v){}
  });
 }
 static LinearLayout column(Context c){
  LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);
  int p=dp(c,20);v.setPadding(p,p,p,p);v.setBackgroundColor(BACKGROUND);return v;
 }
 static TextView text(Context c,String s,int size){TextView v=new TextView(c);v.setText(s);v.setTextSize(size);v.setTextColor(INK);v.setPadding(0,dp(c,6),0,dp(c,10));return v;}
 static TextView title(Context c,String s){TextView v=text(c,s,25);v.setTypeface(null,Typeface.BOLD);return v;}
 static Button button(Context c,String s,View.OnClickListener action){Button v=new Button(c);v.setText(s);v.setAllCaps(false);v.setTextColor(ACCENT);v.setOnClickListener(action);return v;}
}
