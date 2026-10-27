package com.tomaszabramowicz.meshchat;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.model.*;
import org.mapsforge.core.util.MercatorProjection;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.layer.Layer;
import org.mapsforge.map.layer.cache.InMemoryTileCache;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.InternalRenderTheme;
public final class OfflineMapActivity extends Activity {
 private LinearLayout holder;private TextView status;private Button importButton;private MapView map;private MapFile data;
 private final ExecutorService worker=Executors.newSingleThreadExecutor();private final Handler h=new Handler(Looper.getMainLooper());private boolean importing;
 private ArrayList<LocationStore.Point> points;
 private File file(){return new File(getFilesDir(),"edinburgh-dunfermline.map");}
 private TextView text(String s){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(13);return t;}
 public void onCreate(Bundle b){super.onCreate(b);AndroidGraphicFactory.createInstance(getApplication());points=LocationStore.read(this);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(12,12,12,12);root.setBackgroundColor(0xff101820);status=text("Offline map");root.addView(status);
  LinearLayout bar=new LinearLayout(this);importButton=new Button(this);importButton.setText("Import .map");Button show=new Button(this);show.setText("Show points");Button back=new Button(this);back.setText("Back");for(Button x:new Button[]{importButton,show,back}){x.setAllCaps(false);x.setTextSize(12);bar.addView(x,new LinearLayout.LayoutParams(0,-2,1));}root.addView(bar);
  importButton.setOnClickListener(v->{if(!importing){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,401);}});show.setOnClickListener(v->fit());back.setOnClickListener(v->finish());
  holder=new LinearLayout(this);holder.setOrientation(1);root.addView(holder,new LinearLayout.LayoutParams(-1,0,1));StringBuilder s=new StringBuilder();for(LocationStore.Point p:points)s.append(p.name).append(" · ").append(LocationStore.age(p.time)).append(String.format(Locale.US," · ±%.0f m\n",p.accuracy));s.append("Last-known points, not live tracking.\n© OpenStreetMap contributors · ODbL 1.0\nMapsforge 0.21.0 · LGPL-3.0 · local files only");root.addView(text(s.toString()));setContentView(root);open();
 }
 private void close(){if(map!=null){map.destroyAll();map=null;data=null;}else if(data!=null){data.close();data=null;}holder.removeAllViews();}
 private void open(){close();if(!file().isFile()){status.setText("No map imported");holder.addView(text("Import an extracted Mapsforge .map file. No map is bundled and no online tiles are used."));return;}try{
  data=new MapFile(file());map=new MapView(this);map.getMapScaleBar().setVisible(true);map.setBuiltInZoomControls(true);map.getModel().mapViewPosition.setZoomLevelMin((byte)6);map.getModel().mapViewPosition.setZoomLevelMax((byte)17);
  TileRendererLayer layer=new TileRendererLayer(new InMemoryTileCache(40),data,map.getModel().mapViewPosition,AndroidGraphicFactory.INSTANCE);layer.setXmlRenderTheme(InternalRenderTheme.DEFAULT);map.getLayerManager().getLayers().add(layer);map.getLayerManager().getLayers().add(new Pins(points));holder.addView(map,new LinearLayout.LayoutParams(-1,-1));map.setCenter(data.boundingBox().getCenterPoint());map.setZoomLevel((byte)10);status.setText(String.format(Locale.US,"Local offline map · %.1f MB",file().length()/1048576.0));map.post(this::fit);
 }catch(Exception e){close();status.setText("Map error: "+e.getMessage());}}
 private void fit(){if(map==null){StringBuilder s=new StringBuilder();for(LocationStore.Point p:points)s.append(p.name).append(String.format(Locale.US,"\n%.6f, %.6f\n",p.lat,p.lon)).append(LocationStore.age(p.time)).append("\n\n");new AlertDialog.Builder(this).setTitle("Saved coordinates").setMessage(s.length()==0?"No saved positions":s.toString()).setPositiveButton("OK",null).show();return;}if(points.isEmpty())return;
  double a=85,b=-85,c=180,d=-180;boolean outside=false;for(LocationStore.Point p:points){a=Math.min(a,p.lat);b=Math.max(b,p.lat);c=Math.min(c,p.lon);d=Math.max(d,p.lon);outside|=!data.boundingBox().contains(p.lat,p.lon);}map.setCenter(new LatLong((a+b)/2,(c+d)/2));double sx=Math.max(.000001,(d-c)/360),sy=Math.max(.000001,Math.abs(y(a)-y(b)));double z=Math.log(Math.min(Math.max(100,map.getWidth())*.75/sx,Math.max(100,map.getHeight())*.75/sy)/map.getModel().displayModel.getTileSize())/Math.log(2);map.setZoomLevel((byte)Math.max(6,Math.min(16,(int)Math.floor(z))));if(outside)status.setText("Some saved points are outside this offline map");
 }
 private static double y(double a){double r=Math.toRadians(a);return (1-Math.log(Math.tan(r)+1/Math.cos(r))/Math.PI)/2;}
 protected void onActivityResult(int r,int s,Intent i){super.onActivityResult(r,s,i);if(r==401&&s==RESULT_OK&&i!=null&&i.getData()!=null)load(i.getData());}
 private void load(Uri uri){importing=true;importButton.setEnabled(false);status.setText("Importing map...");worker.execute(()->{File temp=new File(getFilesDir(),"offline-import.tmp");try{
  try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temp)){if(in==null)throw new IOException("Cannot open file");byte[] buf=new byte[65536];long total=0;int n;while((n=in.read(buf))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("Import cancelled");total+=n;if(total>600L*1024*1024)throw new IOException("600 MB limit");out.write(buf,0,n);}}
  MapFile check=new MapFile(temp);check.close();h.post(()->{if(isFinishing()||isDestroyed()){temp.delete();return;}close();try{java.nio.file.Files.move(temp.toPath(),file().toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);importing=false;importButton.setEnabled(true);open();}catch(Exception e){temp.delete();importing=false;importButton.setEnabled(true);open();status.setText("Import failed: "+e.getMessage());}});
 }catch(Exception e){temp.delete();h.post(()->{if(!isFinishing()&&!isDestroyed()){importing=false;importButton.setEnabled(true);status.setText("Import failed: "+e.getMessage());}});}});}
 private static final class Pins extends Layer {
  final ArrayList<LocationStore.Point> p;final Paint fill=AndroidGraphicFactory.INSTANCE.createPaint(),label=AndroidGraphicFactory.INSTANCE.createPaint();Pins(ArrayList<LocationStore.Point> p){this.p=p;label.setColor(0xff101820);}
  public void draw(BoundingBox box,byte zoom,org.mapsforge.core.graphics.Canvas c,Point origin){long size=MercatorProjection.getMapSize(zoom,displayModel.getTileSize());float scale=displayModel.getScaleFactor();label.setTextSize(13*scale);for(LocationStore.Point a:p){int x=(int)(MercatorProjection.longitudeToPixelX(a.lon,size)-origin.x),y=(int)(MercatorProjection.latitudeToPixelY(a.lat,size)-origin.y);if(x< -100||y< -100||x>c.getWidth()+100||y>c.getHeight()+100)continue;fill.setColor(a.mine?0xff177ad6:0xffe35b32);c.drawCircle(x,y,(int)(7*scale),fill);String name=a.mine?"Me":a.name;c.drawText(name.length()>18?name.substring(0,18):name,x+(int)(10*scale),y-(int)(8*scale),label);}}
 }
 protected void onDestroy(){worker.shutdownNow();close();AndroidGraphicFactory.clearResourceMemoryCache();super.onDestroy();}
}
