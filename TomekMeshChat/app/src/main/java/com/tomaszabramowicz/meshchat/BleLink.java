// Copyright (c) 2026 Tomasz Abramowicz
// SPDX-License-Identifier: MIT
package com.tomaszabramowicz.meshchat;

import android.bluetooth.*;
import android.content.*;
import android.os.*;
import java.util.*;

/** Serialized GATT operations; FromNum notifies trigger mailbox draining. */
@SuppressWarnings("deprecation")
public final class BleLink implements Link {
    public static final UUID SERVICE=UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd");
    private static final UUID FROM=UUID.fromString("2c55e69e-4993-11ed-b878-0242ac120002");
    private static final UUID TO=UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7");
    private static final UUID NUM=UUID.fromString("ed9da18c-a800-4f66-a670-aa7547e34453");
    private static final UUID CCC=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private final Context context;
    private final BluetoothDevice device;
    private final Events events;
    private final Handler h=new Handler(Looper.getMainLooper());
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic from,to;
    private BluetoothGattDescriptor ccc;
    private final ArrayDeque<Op> queue=new ArrayDeque<>();
    private Op current;
    private boolean closed=false,discovery=false,draining=false,opened=false,receiverRegistered=false;
    private int mtu=23;
    private static final class Op {
        int type; byte[] data; long tag;
        Op(int t,byte[] b,long id) { type=t; data=b; tag=id; }
    }
    private final Runnable timeout=()->fail("Bluetooth operation timed out. Close other radio apps and retry.");
    private final Runnable poll=new Runnable() {
        @Override public void run() { if(!closed && opened) { drain(); h.postDelayed(this,2500); } }
    };
    private final BroadcastReceiver bondReceiver=new BroadcastReceiver() {
        @Override public void onReceive(Context c,Intent i) {
            BluetoothDevice d=i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if(d==null || !d.equals(device) || closed) return;
            int state=i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,-1);
            if(state==BluetoothDevice.BOND_BONDED && current!=null) {
                h.removeCallbacks(timeout); h.postDelayed(()->startCurrent(),500);
            }
            if(state==BluetoothDevice.BOND_NONE &&
               i.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,-1)==BluetoothDevice.BOND_BONDING)
                fail("Bluetooth pairing was cancelled or failed.");
        }
    };
    public BleLink(Context ctx,BluetoothDevice d,Events e) { context=ctx; device=d; events=e; }
    public int maxPacket() { return Math.min(512,mtu-3); }
    public void connect() {
        try {
            IntentFilter filter=new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            if(Build.VERSION.SDK_INT>=33) context.registerReceiver(bondReceiver,filter,Context.RECEIVER_EXPORTED);
            else context.registerReceiver(bondReceiver,filter);
            receiverRegistered=true;
            gatt=device.connectGatt(context,false,callback,BluetoothDevice.TRANSPORT_LE);
            if(gatt==null) fail("Could not start Bluetooth connection.");
            else h.postDelayed(timeout,25000);
        } catch(Exception ex) { fail("Bluetooth: "+ex.getMessage()); }
    }
    private void discover() {
        if(closed || discovery || gatt==null) return;
        discovery=true;
        try { if(!gatt.discoverServices()) fail("Could not discover Bluetooth services."); }
        catch(Exception e) { fail("Bluetooth service discovery failed."); }
    }
    private void add(Op op) { if(!closed) { queue.add(op); next(); } }
    private void next() {
        if(closed || current!=null || queue.isEmpty()) return;
        current=queue.removeFirst(); startCurrent();
    }
    private void startCurrent() {
        if(closed || current==null) return;
        try {
            boolean ok;
            if(current.type==0) {
                ccc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                ok=gatt.writeDescriptor(ccc);
            } else if(current.type==1) ok=gatt.readCharacteristic(from);
            else {
                to.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                to.setValue(current.data); ok=gatt.writeCharacteristic(to);
            }
            if(!ok) { fail("Bluetooth operation could not start."); return; }
            h.removeCallbacks(timeout); h.postDelayed(timeout,30000);
        } catch(Exception e) { fail("Bluetooth: "+e.getMessage()); }
    }
    private boolean check(int status) {
        if(status==BluetoothGatt.GATT_SUCCESS) return true;
        if((status==5 || status==15) && device.getBondState()!=BluetoothDevice.BOND_BONDED) {
            try {
                if(device.getBondState()!=BluetoothDevice.BOND_BONDING && !device.createBond())
                    fail("Pair the radio in Android Bluetooth settings and retry.");
            } catch(Exception e) { fail("Pairing requires Bluetooth permission."); }
            return false;
        }
        fail("Bluetooth GATT error "+status+". Disconnect the official app and retry.");
        return false;
    }
    private void done() { h.removeCallbacks(timeout); current=null; next(); }
    private void drain() {
        if(!closed && opened && !draining) { draining=true; add(new Op(1,null,0)); }
    }
    @Override public void send(byte[] bytes,long tag) {
        h.post(()->{
            if(closed || !opened) return;
            if(bytes.length>maxPacket()) { fail("Negotiated Bluetooth MTU is too small. Retry or use USB."); return; }
            add(new Op(2,bytes,tag));
        });
    }
    private final BluetoothGattCallback callback=new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g,int status,int state) {
            h.post(()->{
                if(closed || g!=gatt) return;
                if(status!=0 || state==BluetoothProfile.STATE_DISCONNECTED) { fail("Bluetooth disconnected ("+status+")."); return; }
                if(state==BluetoothProfile.STATE_CONNECTED) {
                    h.removeCallbacks(timeout); h.postDelayed(timeout,25000);
                    try { if(!g.requestMtu(512)) discover(); }
                    catch(Exception e) { discover(); }
                    h.postDelayed(()->discover(),2500);
                }
            });
        }
        @Override public void onMtuChanged(BluetoothGatt g,int value,int status) {
            h.post(()->{ if(!closed && g==gatt) { if(status==0) mtu=value; discover(); } });
        }
        @Override public void onServicesDiscovered(BluetoothGatt g,int status) {
            h.post(()->{
                if(closed || g!=gatt) return;
                if(status!=0) { fail("Bluetooth service discovery failed: "+status); return; }
                BluetoothGattService service=g.getService(SERVICE);
                if(service==null) { fail("This device does not expose the Meshtastic service."); return; }
                from=service.getCharacteristic(FROM); to=service.getCharacteristic(TO);
                BluetoothGattCharacteristic num=service.getCharacteristic(NUM);
                if(from==null || to==null || num==null) { fail("Required Meshtastic characteristics are missing."); return; }
                ccc=num.getDescriptor(CCC);
                if(ccc==null || !g.setCharacteristicNotification(num,true)) { fail("Could not enable radio notifications."); return; }
                add(new Op(0,null,0));
            });
        }
        @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int status) {
            h.post(()->{
                if(closed || g!=gatt || current==null || current.type!=0) return;
                if(!check(status)) return;
                opened=true; done();
                events.onOpen("Bluetooth · "+safeName());
                drain(); h.postDelayed(poll,2500);
            });
        }
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c) {
            h.post(()->{ if(!closed && g==gatt && c.getUuid().equals(NUM)) drain(); });
        }
        @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,int status) {
            byte[] b=c.getValue()==null?new byte[0]:c.getValue().clone();
            h.post(()->{
                if(closed || g!=gatt || current==null || current.type!=1) return;
                if(!check(status)) return;
                if(b.length>0) events.onBytes(b);
                done();
                if(b.length>0 && !closed) add(new Op(1,null,0)); else draining=false;
            });
        }
        @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int status) {
            h.post(()->{
                if(closed || g!=gatt || current==null || current.type!=2) return;
                if(!check(status)) return;
                long tag=current.tag; done(); events.onWritten(tag); drain();
            });
        }
    };
    private String safeName() {
        try { String n=device.getName(); return n==null?device.getAddress():n; }
        catch(Exception e) { return "Meshtastic radio"; }
    }
    private void fail(String reason) { if(!closed) { close(); events.onClosed(reason); } }
    @Override public void close() {
        if(closed) return;
        closed=true; h.removeCallbacksAndMessages(null);
        queue.clear(); current=null;
        if(receiverRegistered) { try { context.unregisterReceiver(bondReceiver); } catch(Exception ignored) {} }
        if(gatt!=null) { try { gatt.disconnect(); gatt.close(); } catch(Exception ignored) {} gatt=null; }
    }
}
