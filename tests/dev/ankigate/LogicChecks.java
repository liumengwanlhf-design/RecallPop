package dev.ankigate;

public final class LogicChecks {
 static void check(boolean value,String detail){if(!value)throw new AssertionError(detail);}
 public static void main(String[] args){
  check(GatePolicy.allows(1000,0,0),"first intervention");
  check(!GatePolicy.allows(90999,91000,0),"protection lower bound");
  check(GatePolicy.allows(91000,91000,149),"protection exact bound");
  check(!GatePolicy.allows(900000,1000,150),"daily limit");
  check(!GatePolicy.allows(900,1000,0),"clock rollback");
  String html="<style>.card{color:red}</style><ruby>語<rt>ご</rt></ruby>[sound:あ '$.mp3]<script>window.test=1</script>";
  String rendered=CardHtml.render(html);
  check(rendered.contains("<ruby>語<rt>ご</rt></ruby>"),"ruby preserved");
  check(rendered.contains(".card{color:red}"),"CSS preserved");
  check(rendered.contains("window.test=1"),"template JS preserved");
  check(!rendered.contains("[sound:"),"sound converted");
  check(rendered.contains("%27%24.mp3"),"filename encoded safely");
  check(rendered.contains("class='android'")&&rendered.contains("id='qa'"),"AnkiDroid DOM shape");
  System.out.println("10 checks passed");
 }
}
