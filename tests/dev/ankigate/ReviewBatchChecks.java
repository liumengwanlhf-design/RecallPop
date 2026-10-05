package dev.ankigate;
import android.content.*;
public final class ReviewBatchChecks {
 static void expect(boolean value,String why){if(!value)throw new AssertionError(why);}
 public static void main(String[] args){
  expect(ReviewBatch.limit(0)==1&&ReviewBatch.limit(2)==1&&ReviewBatch.limit(100)==1,"invalid settings default to one");
  for(int n:new int[]{1,3,5,10})expect(ReviewBatch.limit(n)==n,"supported upper bound");
  ReviewStatsChecks.Store store=new ReviewStatsChecks.Store();Context context=new Context(){public SharedPreferences getSharedPreferences(String name,int mode){return store;}};
  ReviewBatch batch=new ReviewBatch(42,3);expect(batch.position().equals("1/3"),"first position");
  ReviewStats.confirmed(context,2);expect(batch.confirmed()&&batch.position().equals("2/3"),"Hard counts toward batch");
  ReviewStats.confirmed(context,3);expect(batch.confirmed()&&batch.position().equals("3/3"),"Good advances");
  ReviewStats.confirmed(context,1);expect(!batch.confirmed(),"Again completes third slot");
  expect(ReviewStats.today(context)==3&&ReviewStats.qualifiedToday(context)==1,"batch scoring retains default qualified threshold");
  expect(store.getLong("protected_until",0)==0,"no protection between cards");
  batch.finish(context);long protection=store.getLong("protected_until",0);expect(protection>=System.currentTimeMillis()+89000,"round end starts ninety seconds");
  batch.finish(context);expect(store.getLong("protected_until",0)==protection&&!batch.confirmed()&&batch.completed==3,"one finish and no post-end progress");
  ReviewBatch single=new ReviewBatch(42,1);expect(!single.confirmed(),"default one finishes after one confirmed score");
  ReviewBatch exit=new ReviewBatch(42,5);exit.finish(context);expect(exit.ended&&exit.completed==0&&ReviewStats.today(context)==3,"unanswered/empty round exits without scoring");
  System.out.println("batch bounds, Hard/Good/Again advancement, qualified counts, one-card default and end-only protection passed");
 }
}
