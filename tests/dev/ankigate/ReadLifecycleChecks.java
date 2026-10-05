package dev.ankigate;
public final class ReadLifecycleChecks {
 static void expect(boolean yes,String why){if(!yes)throw new AssertionError(why);}
 public static void main(String[] args){
  ReadLifecycle normal=new ReadLifecycle(1000);expect(normal.accepts(30999)&&!normal.expired(30999),"deadline before edge");expect(!normal.accepts(31000)&&normal.expired(31000),"deadline exact edge rejects late display");
  expect(!normal.released(),"active real reader occupies slot");expect(normal.cancel("读取超时（30秒）"),"timeout requests cancellation once");expect(!normal.cancel("用户停止"),"duplicate cancel cannot spawn another cancel thread");expect(normal.status(32000).contains("取消读取中")&&normal.status(32000).contains("等待接口结束")&&normal.status(32000).contains("31秒"),"honest stage and elapsed after timeout");
  normal.cancelEnded();expect(!normal.released(),"cancel returning does not prove I/O returned; reject next request");normal.readerEnded();expect(normal.released()&&!normal.accepts(32000),"real read done releases slot but old card never accepted");
  ReadLifecycle slowCancel=new ReadLifecycle(1);slowCancel.cancel("页面已离开");slowCancel.readerEnded();expect(!slowCancel.released(),"read return does not allow another blocking cancel thread");slowCancel.cancelEnded();expect(slowCancel.released(),"both real calls must return before next read");
  ReadLifecycle next=new ReadLifecycle(40000);expect(next.accepts(40001)&&!next.cancelled(),"new request after release independent of old cancellation");next.readerEnded();expect(next.released()&&next.accepts(40002),"normal result can display with no cancel call");
  ReadLifecycle scoreIndependent=new ReadLifecycle(0);scoreIndependent.stage="校验媒体";scoreIndependent.cancel("设备已锁屏");expect(scoreIndependent.stage.equals("校验媒体")&&!scoreIndependent.accepts(1),"media cancellation retains stage and prevents display");
  ReadLifecycle manualStop=new ReadLifecycle(50000);expect(manualStop.cancel("用户停止全部介入")&&!manualStop.accepts(50001),"global STOP immediately rejects active manual read result");manualStop.cancelEnded();expect(!manualStop.released(),"STOP retains actual read until it returns");manualStop.readerEnded();expect(manualStop.released()&&!manualStop.accepts(51000),"STOP finishes slot without ever accepting late card");
  System.out.println("30s boundary, late result rejection, real-read/cancel completion, bounded cancellation and next-request lifecycle checks passed");
 }
}
