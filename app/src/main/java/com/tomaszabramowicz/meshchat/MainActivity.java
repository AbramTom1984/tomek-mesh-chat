// Copyright (c) 2026 Tomasz Abramowicz
// SPDX-License-Identifier: MIT
package com.tomaszabramowicz.meshchat;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.usb.*;
import android.location.LocationManager;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.widget.*;
import com.hoho.android.usbserial.driver.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@SuppressWarnings("deprecation")
public final class MainActivity extends Activity {
    private final Handler h=new Handler(Looper.getMainLooper());
    private TextView status,counter;
    private EditText input;
    private Button send;
    private ScrollView scroll;
    private LinearLayout messages;
    private final ArrayList<JSONObject> history=new ArrayList<>();
    private final Map<Long,TextView> statusViews=new HashMap<>();
    private final Set<String> seen=new HashSet<>();
    private android.content.SharedPreferences prefs;
    private BluetoothAdapter adapter;
    private UsbManager usb;
    private Link link;
    private MeshSession session;
    private int generation=0;
    private boolean visible=false,auto=true,scanning=false,choosing=false,usbPending=false;
    private String lastUsbId="";
    private UsbSerialDriver pendingUsb;
    private final LinkedHashMap<String,BluetoothDevice> found=new LinkedHashMap<>();
    private static final String USB_PERMISSION="com.tomaszabramowicz.meshchat.USB_PERMISSION";
    private final Runnable retry=new Runnable() {
        @Override public void run() {
            if(visible && auto && link==null && !scanning && !choosing && !usbPending) autoConnect(false);
            if(visible) h.postDelayed(this,10000);
        }
    };
    private final Runnable endScan=()->finishScan();
    private final BroadcastReceiver usbReceiver=new BroadcastReceiver() {
        @Override public void onReceive(Context c,Intent intent) {
            String action=intent.getAction();
            if(USB_PERMISSION.equals(action)) {
                UsbDevice device=intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if(pendingUsb==null || (device!=null && !device.equals(pendingUsb.getDevice()))) return;
                UsbSerialDriver d=pendingUsb; pendingUsb=null; usbPending=false;
                if(usb.hasPermission(d.getDevice())) {
                    if(visible) openUsb(d);
                }
                else { auto=false; showStatus("USB permission denied. Tap Connect to retry.",false); }
            } else if(UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                if(visible && link==null && auto) autoConnect(true);
            } else if(UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice d=intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if(d!=null && d.getDeviceName().equals(lastUsbId) && link instanceof UsbLink)
                    disconnect("USB unplugged. Waiting to reconnect…");
            }
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=getSharedPreferences("settings",MODE_PRIVATE);
        usb=(UsbManager)getSystemService(USB_SERVICE);
        BluetoothManager bm=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        adapter=bm==null?null:bm.getAdapter();
        buildUi(); loadHistory();
        IntentFilter f=new IntentFilter(USB_PERMISSION);
        f.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        f.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(usbReceiver,f,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(usbReceiver,f);
    }
    private int dp(float value) { return (int)(value*getResources().getDisplayMetrics().density+0.5f); }
    private GradientDrawable background(int color,int radius) {
        GradientDrawable b=new GradientDrawable(); b.setColor(color); b.setCornerRadius(dp(radius)); return b;
    }
    private TextView text(String value,int size,int color) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); return t;
    }
    private Button button(String label) {
        Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(14); return b;
    }
    private void buildUi() {
        LinearLayout page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(14),dp(12),dp(14),dp(8)); page.setBackgroundColor(Color.rgb(16,24,34));
        TextView title=text("Tomek Mesh Chat",25,Color.WHITE); title.setTypeface(null,Typeface.BOLD); page.addView(title);
        TextView subtitle=text("Simple LoRa messaging",13,Color.rgb(153,177,194)); page.addView(subtitle);
        status=text("Not connected",14,Color.rgb(255,200,130)); status.setPadding(0,dp(12),0,dp(8)); page.addView(status);
        LinearLayout actions=new LinearLayout(this);
        Button connect=button("Connect"); Button disconnect=button("Disconnect"); Button more=button("More");
        actions.addView(connect,new LinearLayout.LayoutParams(0,dp(48),1));
        actions.addView(disconnect,new LinearLayout.LayoutParams(0,dp(48),1));
        actions.addView(more,new LinearLayout.LayoutParams(dp(75),dp(48)));
        connect.setOnClickListener(v->chooseConnection());
        disconnect.setOnClickListener(v->{ auto=false; stopScan(); disconnect("Disconnected. Tap Connect to resume."); });
        more.setOnClickListener(v->showMore()); page.addView(actions);
        TextView warning=text("Primary-channel chat — not a private direct message",12,Color.rgb(153,177,194));
        warning.setPadding(0,dp(6),0,dp(8)); page.addView(warning);
        messages=new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL);
        scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.addView(messages);
        page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        counter=text("0 / 180 UTF-8 bytes",12,Color.rgb(153,177,194)); page.addView(counter);
        LinearLayout composer=new LinearLayout(this); composer.setGravity(Gravity.BOTTOM);
        input=new EditText(this); input.setHint("Write a message…"); input.setHintTextColor(Color.GRAY);
        input.setTextColor(Color.WHITE); input.setTextSize(16); input.setMaxLines(4);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setPadding(dp(10),dp(8),dp(10),dp(8)); input.setBackground(background(Color.rgb(33,45,60),10));
        composer.addView(input,new LinearLayout.LayoutParams(0,-2,1));
        send=button("Send"); send.setEnabled(false); composer.addView(send,new LinearLayout.LayoutParams(dp(78),dp(52)));
        send.setOnClickListener(v->{
            if(session==null) { toast("Connect first."); return; }
            String error=session.send(input.getText().toString());
            if(error==null) input.setText(""); else toast(error);
        });
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a) {}
            public void onTextChanged(CharSequence s,int st,int before,int count) {
                int n=s.toString().getBytes(StandardCharsets.UTF_8).length;
                counter.setText(n+" / 180 UTF-8 bytes");
                counter.setTextColor(n>180?Color.rgb(255,130,130):Color.rgb(153,177,194));
            }
            public void afterTextChanged(Editable s) {}
        });
        page.addView(composer); setContentView(page);
    }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    private void showStatus(String s,boolean ready) {
        status.setText(s); status.setTextColor(ready?Color.rgb(88,217,177):Color.rgb(255,200,130)); send.setEnabled(ready);
    }
    private void showMore() {
        new AlertDialog.Builder(this).setTitle("Tomek Mesh Chat")
            .setItems(new String[]{"Forget saved Bluetooth device","Clear chat history","USB device information","About / prototype limits"},(d,w)->{
                if(w==0) {
                    prefs.edit().remove("ble").remove("bleName").apply();
                    auto=false; disconnect("Saved Bluetooth device forgotten.");
                } else if(w==1) new AlertDialog.Builder(this).setMessage("Delete local messages on this phone?")
                    .setPositiveButton("Delete",(a,b)->{ history.clear(); seen.clear(); statusViews.clear(); messages.removeAllViews(); saveHistory(); })
                    .setNegativeButton("Cancel",null).show();
                else if(w==2) {
                    StringBuilder details=new StringBuilder();
                    for(UsbDevice device:usb.getDeviceList().values()) {
                        UsbSerialDriver driver=UsbSerialProber.getDefaultProber().probeDevice(device);
                        details.append(String.format(Locale.US,"VID %04x / PID %04x\n%s\n\n",device.getVendorId(),device.getProductId(),driver==null?"No matching serial driver":driver.getClass().getSimpleName()));
                    }
                    if(details.length()==0) details.append("No USB devices detected. Check OTG, cable and power.");
                    new AlertDialog.Builder(this).setTitle("USB devices").setMessage(details.toString()).setPositiveButton("OK",null).show();
                }
                else new AlertDialog.Builder(this).setTitle("Prototype 0.1.0")
                    .setMessage("Copyright (c) 2026 Tomasz Abramowicz\nMIT License\n\nIndependent Meshtastic-compatible client. No firmware changes.\n\nPrimary-channel broadcasts only, not private DMs. Radio write does not prove remote delivery. History is stored locally without app-level encryption. No Internet permission.\n\nKeep the app visible for this first version. No guaranteed background connection, screen-off reception or notifications. Close the official app's radio connection first. USB needs OTG/data support and power.\n\nThis build requires real-device testing.")
                    .setPositiveButton("OK",null).show();
            }).setNegativeButton("Close",null).show();
    }
    private void chooseConnection() {
        auto=true;
        new AlertDialog.Builder(this).setTitle("Connect to your local radio")
            .setItems(new String[]{"Auto: USB or saved Bluetooth","Find Bluetooth radio","Use USB"},(d,w)->{
                if(w==0) { disconnect("Looking for a radio…"); autoConnect(true); }
                if(w==1) { disconnect("Looking for Bluetooth…"); if(permissions()) startScan(); }
                if(w==2) { disconnect("Looking for USB…"); chooseUsb(true); }
            }).setNegativeButton("Cancel",null).show();
    }
    private boolean permissions() {
        ArrayList<String> needed=new ArrayList<>();
        if(Build.VERSION.SDK_INT>=31) {
            if(checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.BLUETOOTH_SCAN);
            if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if(!needed.isEmpty()) { requestPermissions(needed.toArray(new String[0]),100); return false; }
        return true;
    }
    @Override public void onRequestPermissionsResult(int code,String[] p,int[] results) {
        super.onRequestPermissionsResult(code,p,results);
        if(code==100) {
            for(int r:results) if(r!=PackageManager.PERMISSION_GRANTED) { auto=false; showStatus("Bluetooth permission denied. USB is still available.",false); return; }
            auto=true; autoConnect(true);
        }
    }
    private void autoConnect(boolean first) {
        if(link!=null || scanning || choosing || usbPending || !visible) return;
        if(chooseUsb(false)) return;
        if(!permissions()) return;
        if(adapter==null) { showStatus("Bluetooth not available. Try USB.",false); return; }
        if(!adapter.isEnabled()) {
            showStatus("Turn on Bluetooth, then tap Connect.",false);
            if(first) try { startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),101); } catch(Exception e) { toast("Enable Bluetooth in Android settings."); }
            return;
        }
        String address=prefs.getString("ble","");
        if(!address.isEmpty()) {
            try { openBle(adapter.getRemoteDevice(address)); }
            catch(Exception e) { showStatus("Saved device unavailable. Use Find Bluetooth radio.",false); }
        } else if(first) startScan();
        else showStatus("Tap Connect to choose a radio.",false);
    }
    @Override protected void onActivityResult(int code,int result,Intent data) {
        super.onActivityResult(code,result,data);
        if(code==101 && result==RESULT_OK) autoConnect(true);
    }
    private final ScanCallback scanCallback=new ScanCallback() {
        @Override public void onScanResult(int type,ScanResult result) {
            h.post(()->{
                if(!scanning) return;
                BluetoothDevice d=result.getDevice();
                ScanRecord record=result.getScanRecord();
                boolean mesh=record!=null && record.getServiceUuids()!=null && record.getServiceUuids().contains(new ParcelUuid(BleLink.SERVICE));
                String name=record==null?null:record.getDeviceName();
                if(mesh || (name!=null && name.toLowerCase(Locale.US).contains("meshtastic"))) found.put(d.getAddress(),d);
            });
        }
        @Override public void onScanFailed(int error) {
            h.post(()->{ stopScan(); showStatus("Bluetooth scan failed ("+error+"). Tap Connect to retry.",false); });
        }
    };
    private void startScan() {
        if(!visible || scanning || !permissions()) return;
        if(adapter==null || !adapter.isEnabled()) { autoConnect(true); return; }
        if(Build.VERSION.SDK_INT<31) {
            LocationManager location=(LocationManager)getSystemService(LOCATION_SERVICE);
            boolean on=false;
            try { on=location.isProviderEnabled(LocationManager.GPS_PROVIDER)||location.isProviderEnabled(LocationManager.NETWORK_PROVIDER); } catch(Exception ignored) {}
            if(!on) {
                auto=false; showStatus("For Bluetooth scanning on this Android, turn on Location.",false);
                new AlertDialog.Builder(this).setMessage("Android 8–11 may require Location to be enabled for BLE scans. The app does not read GPS coordinates.")
                    .setPositiveButton("Open settings",(d,w)->startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                    .setNegativeButton("Cancel",null).show(); return;
            }
        }
        found.clear(); scanning=true; showStatus("Finding Meshtastic radios…",false);
        try {
            adapter.getBluetoothLeScanner().startScan(null,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanCallback);
            h.postDelayed(endScan,6500);
        } catch(Exception e) { stopScan(); showStatus("Scan error: "+e.getMessage(),false); }
    }
    private void stopScan() {
        h.removeCallbacks(endScan);
        if(scanning) try { if(adapter!=null && adapter.getBluetoothLeScanner()!=null) adapter.getBluetoothLeScanner().stopScan(scanCallback); } catch(Exception ignored) {}
        scanning=false;
    }
    private void finishScan() {
        if(!scanning) return; stopScan();
        if(!visible) return;
        ArrayList<BluetoothDevice> devices=new ArrayList<>(found.values());
        if(devices.isEmpty()) { showStatus("No radio found. Close the official app connection, then retry.",false); return; }
        String[] labels=new String[devices.size()];
        for(int i=0;i<devices.size();i++) {
            BluetoothDevice d=devices.get(i); String n=d.getName();
            labels[i]=(n==null?"Meshtastic radio":n)+"\n"+d.getAddress();
        }
        choosing=true;
        new AlertDialog.Builder(this).setTitle("Choose YOUR local radio")
            .setItems(labels,(dialog,which)->{
                choosing=false; BluetoothDevice d=devices.get(which);
                prefs.edit().putString("ble",d.getAddress()).putString("bleName",d.getName()).apply(); openBle(d);
            }).setNegativeButton("Cancel",(d,w)->{ choosing=false; auto=false; })
            .setOnCancelListener(d->{ choosing=false; auto=false; }).show();
    }
    private boolean chooseUsb(boolean manual) {
        List<UsbSerialDriver> drivers=UsbSerialProber.getDefaultProber().findAllDrivers(usb);
        if(drivers.isEmpty()) { if(manual) toast("No supported USB serial device. Check OTG, data cable and power."); return false; }
        if(drivers.size()==1) { requestUsb(drivers.get(0)); return true; }
        if(!manual) { showStatus("Multiple USB devices. Use Connect → Use USB.",false); return false; }
        choosing=true; String[] labels=new String[drivers.size()];
        for(int i=0;i<labels.length;i++) labels[i]=drivers.get(i).getDevice().getDeviceName()+" · "+drivers.get(i).getClass().getSimpleName();
        new AlertDialog.Builder(this).setTitle("Choose Meshtastic USB device").setItems(labels,(d,w)->{ choosing=false; requestUsb(drivers.get(w)); })
            .setNegativeButton("Cancel",(d,w)->choosing=false).setOnCancelListener(d->choosing=false).show();
        return true;
    }
    private void requestUsb(UsbSerialDriver d) {
        if(usbPending) return;
        if(usb.hasPermission(d.getDevice())) { openUsb(d); return; }
        pendingUsb=d; usbPending=true; showStatus("Allow USB access in the Android dialog.",false);
        Intent intent=new Intent(USB_PERMISSION).setPackage(getPackageName());
        PendingIntent p=PendingIntent.getBroadcast(this,10,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        usb.requestPermission(d.getDevice(),p);
    }
    private void openBle(BluetoothDevice device) {
        stopScan(); disconnect("Connecting over Bluetooth…");
        int token=++generation;
        link=new BleLink(this,device,events(token));
        session=new MeshSession(link,sessionUi(token));
        ((BleLink)link).connect();
    }
    private void openUsb(UsbSerialDriver d) {
        stopScan(); disconnect("Connecting over USB…"); lastUsbId=d.getDevice().getDeviceName();
        int token=++generation;
        link=new UsbLink(usb,d,events(token));
        session=new MeshSession(link,sessionUi(token));
        ((UsbLink)link).connect();
    }
    private Link.Events events(int token) { return new Link.Events() {
        public void onOpen(String name) { if(token==generation && session!=null) session.opened(name); }
        public void onBytes(byte[] b) { if(token==generation && session!=null) session.receive(b); }
        public void onClosed(String reason) { if(token==generation) disconnect(reason); }
        public void onWritten(long tag) { if(token==generation && session!=null) session.written(tag); }
    }; }
    private MeshSession.Ui sessionUi(int token) { return new MeshSession.Ui() {
        public void state(String s,boolean ready) { if(token==generation) showStatus(s,ready); }
        public void failed(String s) { if(token==generation) disconnect(s); }
        public void received(String key,String sender,String value) {
            if(token!=generation || seen.contains(key)) return;
            seen.add(key); addMessage(key,sender,value,false,0,"Received");
        }
        public void outgoing(long id,String value,String state) {
            if(token==generation) addMessage("out:"+System.currentTimeMillis()+":"+id,"You",value,true,id,state);
        }
        public void delivery(long id,String state) {
            if(token!=generation) return;
            for(int i=history.size()-1;i>=0;i--) {
                JSONObject m=history.get(i);
                if(m.optBoolean("out") && m.optLong("id")==id) { try { m.put("status",state); } catch(Exception ignored) {} break; }
            }
            TextView view=statusViews.get(id); if(view!=null) view.setText(state);
            saveHistory();
        }
    }; }
    private void disconnect(String reason) {
        if(session!=null) session.close();
        generation++;
        if(link!=null) link.close();
        link=null; session=null; showStatus(reason,false);
    }
    private void addMessage(String key,String who,String body,boolean out,long id,String state) {
        JSONObject m=new JSONObject();
        try { m.put("key",key).put("who",who).put("body",body).put("out",out).put("id",id).put("status",state).put("time",System.currentTimeMillis()); }
        catch(JSONException ignored) {}
        history.add(m);
        if(history.size()>200) { history.remove(0); rebuildMessages(); }
        else render(m);
        saveHistory(); scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
    }
    private void render(JSONObject m) {
        boolean out=m.optBoolean("out");
        LinearLayout bubble=new LinearLayout(this); bubble.setOrientation(LinearLayout.VERTICAL); bubble.setPadding(dp(12),dp(9),dp(12),dp(9));
        bubble.setBackground(background(out?Color.rgb(26,79,69):Color.rgb(33,45,60),12));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(out?dp(30):0,dp(5),out?0:dp(30),dp(5));
        String time=new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(m.optLong("time")));
        TextView who=text(m.optString("who")+"  ·  "+time,12,Color.rgb(153,205,190)); bubble.addView(who);
        TextView body=text(m.optString("body"),17,Color.WHITE); body.setTextIsSelectable(true); bubble.addView(body);
        TextView state=text(m.optString("status"),11,Color.rgb(166,185,195)); bubble.addView(state);
        if(out) statusViews.put(m.optLong("id"),state);
        messages.addView(bubble,lp);
    }
    private void rebuildMessages() {
        messages.removeAllViews(); statusViews.clear(); seen.clear();
        for(JSONObject m:history) { if(!m.optBoolean("out")) seen.add(m.optString("key")); render(m); }
    }
    private void saveHistory() {
        try {
            JSONArray a=new JSONArray(); for(JSONObject m:history) a.put(m);
            try(FileOutputStream f=openFileOutput("chat.json",MODE_PRIVATE)) { f.write(a.toString().getBytes(StandardCharsets.UTF_8)); }
        } catch(Exception ignored) {}
    }
    private void loadHistory() {
        try(FileInputStream f=openFileInput("chat.json")) {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream(); byte[] b=new byte[4096]; int n;
            while((n=f.read(b))!=-1) { bytes.write(b,0,n); if(bytes.size()>2000000) throw new IOException("History too large"); }
            JSONArray a=new JSONArray(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
            for(int i=Math.max(0,a.length()-200);i<a.length();i++) {
                JSONObject m=a.getJSONObject(i);
                if(m.optBoolean("out")) m.put("status","Saved message · recipient delivery unconfirmed");
                history.add(m);
            }
            rebuildMessages(); scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
        } catch(Exception ignored) {}
    }
    @Override protected void onResume() {
        super.onResume(); visible=true; h.removeCallbacks(retry);
        h.postDelayed(()->{ if(visible && auto) autoConnect(true); },400);
        h.postDelayed(retry,10000);
    }
    @Override protected void onStop() {
        visible=false; h.removeCallbacks(retry); stopScan();
        disconnect("Paused. Reconnects when the app is opened.");
        super.onStop();
    }
    @Override protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        try { unregisterReceiver(usbReceiver); } catch(Exception ignored) {}
        disconnect("Closed"); super.onDestroy();
    }
}
