package dev.ankigate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;

final class CardHtml {
 static String render(String html){
  Matcher m=Pattern.compile("(?i)\\[sound:([^\\]]+)\\]").matcher(html);StringBuffer out=new StringBuffer();
  while(m.find()){
   String source=URLEncoder.encode(m.group(1),StandardCharsets.UTF_8).replace("+","%20");
   String audio="<span class='gate-audio'><audio preload='metadata' src='"+source+"'></audio><a href='#' class='replay-button soundLink' aria-label='播放音频' onclick=\"event.preventDefault();var a=this.previousElementSibling;document.querySelectorAll('audio').forEach(function(x){if(x!==a)x.pause()});a.currentTime=0;a.play();\"><svg viewBox='0 0 64 64'><circle cx='32' cy='32' r='29'/><path d='M24,18 L48,32 L24,46 Z'/></svg></a></span>";
   m.appendReplacement(out,Matcher.quoteReplacement(audio));
  }m.appendTail(out);
  String bridge="<script>window.pycmd=function(s){GateAudio.command(String(s));};window.anki={playSound:window.pycmd};document.addEventListener('error',function(e){var t=e.target;if(t&&(t.tagName==='AUDIO'||t.tagName==='VIDEO'||t.tagName==='IMG'))GateAudio.mediaFailed(t.currentSrc||t.src||t.tagName)},true);</script>";
  return "<!doctype html><html class='android'><head><meta name='viewport' content='width=device-width,initial-scale=1'><link rel='icon' href='data:,'>"+bridge+"</head><body class='card'><div id='qa'>"+out+"</div></body></html>";
 }
}
