package dev.ankigate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ReviewSelectionChecks {
 static void expect(boolean yes,String why){if(!yes)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  ReviewSelection lease=new ReviewSelection(22);lease.opened(11);
  expect(lease.restoreWhen(22)&&!lease.restoreWhen(33),"restore only own unchanged target; preserve external selection");
  expect(lease.beginScore()&&!lease.beginScore(),"one accepted score");
  expect(lease.beginClose()&&!lease.beginClose(),"one close even if STOP and Activity both dispose");
  expect(!lease.beginScore(),"closing rejects new score");
  CountDownLatch started=new CountDownLatch(1),finished=new CountDownLatch(1);AtomicBoolean restored=new AtomicBoolean(),interrupted=new AtomicBoolean();
  Thread close=new Thread(()->{started.countDown();try{interrupted.set(lease.awaitScore());restored.set(true);}finally{finished.countDown();}});
  close.start();expect(started.await(1,TimeUnit.SECONDS),"close task started");expect(!finished.await(30,TimeUnit.MILLISECONDS)&&!restored.get(),"accepted score keeps restoration waiting without cancellation");
  close.interrupt();expect(!finished.await(30,TimeUnit.MILLISECONDS),"interrupt cannot release selection during an accepted score");
  lease.scoreEnded();expect(finished.await(1,TimeUnit.SECONDS)&&restored.get()&&interrupted.get(),"score completion permits restoration and preserves interruption information");close.join();
  ReviewSelection same=new ReviewSelection(22);same.opened(22);expect(!same.restoreWhen(22),"same original deck never reselects or resets on exit");
  ReviewSelection unopened=new ReviewSelection(22);expect(!unopened.restoreWhen(22),"failed original selection read cannot invent prior deck");
  System.out.println("selection CAS restore, single close, accepted-score ordering and external choice preservation passed");
 }
}
