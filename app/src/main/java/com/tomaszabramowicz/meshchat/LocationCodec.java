package com.tomaszabramowicz.meshchat;
import java.util.Locale;
public final class LocationCodec {
 public static final class Record { public boolean request; public String nonce; public double lat,lon; public long time; public float accuracy; }
 public static Record parse(String s){try{
  if(s==null || s.length()>180)return null;String[] p=s.split("\\|",-1);
  if(p.length<3 || !p[0].equals("TMC3") || !p[2].matches("[0-9a-f]{8,16}"))return null;
  Record r=new Record();r.nonce=p[2];
  if(p.length==3 && p[1].equals("R")){r.request=true;return r;}
  if(p.length!=7 || !p[1].equals("L"))return null;
  r.lat=Double.parseDouble(p[3]);r.lon=Double.parseDouble(p[4]);r.time=Long.parseLong(p[5]);r.accuracy=Float.parseFloat(p[6]);
  if(!Double.isFinite(r.lat)||!Double.isFinite(r.lon)||!Float.isFinite(r.accuracy)||Math.abs(r.lat)>85||Math.abs(r.lon)>180||r.accuracy<0||r.accuracy>100000||r.time<946684800000L||r.time>4102444800000L)return null;
  return r;
 }catch(Exception e){return null;}}
 public static String request(String n){return "TMC3|R|"+n;}
 public static String location(String n,double a,double b,long t,float c){String s=String.format(Locale.US,"TMC3|L|%s|%.6f|%.6f|%d|%.1f",n,a,b,t,c);if(parse(s)==null)throw new IllegalArgumentException("Invalid GPS fix");return s;}
 public static String describe(Record r){return r.request?"Location requested. Reply only if you agree. Use Location to share once.":String.format(Locale.US,"Shared location: %.6f, %.6f\nAccuracy: ±%.0f m\nMeasured: %s\nLast-known position, not live tracking.",r.lat,r.lon,r.accuracy,new java.util.Date(r.time));}
 private LocationCodec(){}
}
