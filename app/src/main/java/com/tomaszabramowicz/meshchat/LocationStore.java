package com.tomaszabramowicz.meshchat;
import android.content.Context;
import org.json.*;
import java.util.*;
public final class LocationStore {
 public static final class Point {public String id,name;public double lat,lon;public long time;public float accuracy;public boolean mine;}
 public static synchronized ArrayList<Point> read(Context c){ArrayList<Point> a=new ArrayList<>();try{JSONArray j=new JSONArray(c.getSharedPreferences("offline_locations",0).getString("points","[]"));for(int i=0;i<Math.min(20,j.length());i++){JSONObject o=j.getJSONObject(i);Point p=new Point();p.id=o.getString("id");p.name=o.getString("name");p.lat=o.getDouble("lat");p.lon=o.getDouble("lon");p.time=o.getLong("time");p.accuracy=(float)o.getDouble("accuracy");p.mine=o.optBoolean("mine");a.add(p);}}catch(Exception ignored){}return a;}
 public static synchronized void save(Context c,String id,String name,LocationCodec.Record r,boolean mine){if(r==null||r.request)return;ArrayList<Point> a=read(c);for(Point p:a)if(p.id.equals(id)&&p.time>r.time)return;a.removeIf(p->p.id.equals(id));Point p=new Point();p.id=id;p.name=name;p.lat=r.lat;p.lon=r.lon;p.time=r.time;p.accuracy=r.accuracy;p.mine=mine;a.add(p);a.sort((x,y)->Long.compare(y.time,x.time));JSONArray j=new JSONArray();try{for(int i=0;i<Math.min(a.size(),20);i++){Point x=a.get(i);j.put(new JSONObject().put("id",x.id).put("name",x.name).put("lat",x.lat).put("lon",x.lon).put("time",x.time).put("accuracy",x.accuracy).put("mine",x.mine));}c.getSharedPreferences("offline_locations",0).edit().putString("points",j.toString()).apply();}catch(Exception ignored){}}
 public static String age(long t){long d=System.currentTimeMillis()-t;if(d< -120000)return "clock mismatch";long m=Math.max(0,d)/60000;return m<60?m+" min old":m<1440?m/60+" h old":m/1440+" days old";}
 public static void clear(Context c){c.getSharedPreferences("offline_locations",0).edit().remove("points").apply();}
}
