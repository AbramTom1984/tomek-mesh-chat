package com.tomaszabramowicz.meshchat;
import android.Manifest;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import android.widget.Toast;
import java.util.UUID;
@SuppressWarnings("deprecation")
public final class LocationFeature {
 public static final int PERMISSION=303;
 private final MainActivity host;private final Handler h=new Handler(Looper.getMainLooper());
 private LocationManager manager;private AlertDialog progress;private boolean waiting,permissionPending;private String nonce;private long lastPrompt;
 public LocationFeature(MainActivity a){host=a;}
 private String token(){return UUID.randomUUID().toString().replace("-","").substring(0,12);}
 private void toast(String s){Toast.makeText(host,s,Toast.LENGTH_LONG).show();}
 private void send(String s){String error=host.sendLocationPayload(s);if(error!=null)toast(error);}
 public void menu(){new AlertDialog.Builder(host).setTitle("Location — shared channel").setItems(new String[]{"Share my location once","Request a location","Open offline map","Clear saved points"},(d,w)->{
  if(w==0)share(token());
  if(w==1)new AlertDialog.Builder(host).setTitle("Request on shared channel?").setMessage("Everyone with channel access can read this request and any reply. It is NOT a private DM. No GPS will be activated without approval.").setPositiveButton("Send request",(a,b)->send(LocationCodec.request(token()))).setNegativeButton("Cancel",null).show();
  if(w==2)host.startActivity(new Intent(host,OfflineMapActivity.class));
  if(w==3)new AlertDialog.Builder(host).setMessage("Delete local map points? Chat history is separate.").setPositiveButton("Delete",(a,b)->LocationStore.clear(host)).setNegativeButton("Cancel",null).show();
 }).setNegativeButton("Close",null).show();}
 public void share(String n){if(waiting||permissionPending){toast("GPS request already active");return;}nonce=n;if(host.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){permissionPending=true;host.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},PERMISSION);return;}acquire();}
 public void permissionResult(){permissionPending=false;if(host.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)acquire();else toast("Precise location permission is required for satellite-only GPS. Nothing was sent.");}
 private void acquire(){
  manager=(LocationManager)host.getSystemService(Context.LOCATION_SERVICE);
  try{if(manager==null||!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)){toast("Enable phone GPS and retry outdoors.");return;}
   waiting=true;progress=new AlertDialog.Builder(host).setTitle("Waiting for satellite GPS").setMessage("Up to 90 seconds. Go outdoors. Nothing will be sent until you approve the preview.").setNegativeButton("Cancel",(d,w)->cancel()).setOnCancelListener(d->cancel()).show();
   manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,listener,Looper.getMainLooper());h.postDelayed(()->{if(waiting){cancel();toast("No fresh fix. Nothing was sent.");}},90000);
  }catch(Exception e){cancel();toast("GPS error: "+e.getMessage());}
 }
 private final LocationListener listener=new LocationListener(){
  public void onLocationChanged(Location l){if(!waiting||!l.hasAccuracy())return;long age=(SystemClock.elapsedRealtimeNanos()-l.getElapsedRealtimeNanos())/1000000L;if(age<0||age>60000)return;String n=nonce;cancel();try{
   String raw=LocationCodec.location(n,l.getLatitude(),l.getLongitude(),l.getTime(),l.getAccuracy());LocationCodec.Record r=LocationCodec.parse(raw);LocationStore.save(host,"self","Me",r,true);
   new AlertDialog.Builder(host).setTitle("Share this position?").setMessage(LocationCodec.describe(r)+"\n\nWARNING: shared channel, NOT a private message. All channel users may read your coordinates.").setPositiveButton("Send to channel",(d,w)->send(raw)).setNegativeButton("Keep local only",null).show();
  }catch(Exception e){toast("Invalid GPS fix. Nothing sent.");}}
  public void onProviderDisabled(String p){if(waiting){cancel();toast("GPS disabled. Nothing sent.");}}
  public void onProviderEnabled(String p){}public void onStatusChanged(String p,int s,Bundle b){}
 };
 public String receive(String key,String sender,String raw){LocationCodec.Record r=LocationCodec.parse(raw);if(r==null)return null;
  if(!r.request){String[] k=key.split(":");LocationStore.save(host,"node:"+(k.length>2?k[2]:sender),sender,r,false);}
  else {long now=SystemClock.elapsedRealtime();if(lastPrompt==0||now-lastPrompt>60000){lastPrompt=now;new AlertDialog.Builder(host).setTitle(sender+" requests your location").setMessage("Optional. Your reply goes to the SHARED CHANNEL, not just this sender.").setPositiveButton("Share once",(d,w)->share(r.nonce)).setNegativeButton("Decline",null).show();}}
  return LocationCodec.describe(r);
 }
 public void cancel(){waiting=false;h.removeCallbacksAndMessages(null);if(manager!=null)try{manager.removeUpdates(listener);}catch(Exception ignored){}if(progress!=null){progress.dismiss();progress=null;}}
}
