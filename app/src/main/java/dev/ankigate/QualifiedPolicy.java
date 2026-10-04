package dev.ankigate;
final class QualifiedPolicy {
 static int threshold(int value){return value==1||value==4?value:3;}
 static boolean accepts(int threshold,int ease){return ease>=QualifiedPolicy.threshold(threshold)&&ease<=4;}
}
