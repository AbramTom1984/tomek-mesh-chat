// Copyright (c) 2026 Tomasz Abramowicz
// SPDX-License-Identifier: MIT
package com.tomaszabramowicz.meshchat;

import android.os.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

/** Legacy single-nonce config handshake, channel text only, no admin writes. */
public final class MeshSession {
    public interface Ui {
        void state(String text,boolean ready);
        void received(String key,String sender,String text);
        void outgoing(long id,String text,String status);
        void delivery(long id,String status);
        void failed(String reason);
        default void signal(String sender,SignalInfo sample) {}
    }
    private final Link link;
    private final Ui ui;
    private final Handler h=new Handler(Looper.getMainLooper());
    private final SecureRandom random=new SecureRandom();
    private final Map<Long,String> names=new HashMap<>();
    private final Map<Long,String> pending=new HashMap<>();
    private final ArrayDeque<byte[]> backlog=new ArrayDeque<>();
    private long nonce,myNode=0,lastSend=0;
    private int channel=-1;
    private String channelName="Primary channel",connection="";
    private boolean ready=false,closed=false;
    private final Runnable configTimeout=()->onConfigTimeout();
    private void onConfigTimeout() {
        if(!ready && !closed) ui.failed("No complete configuration received. Close the official app and reconnect.");
    }
    private final Runnable heartbeat=new Runnable() {
        @Override public void run() {
            if(closed) return;
            if(ready) link.send(new Proto.Writer().bytes(7,new byte[0]).build(),0);
            h.postDelayed(this,20000);
        }
    };
    public MeshSession(Link l,Ui u) { link=l; ui=u; }
    public void opened(String name) {
        if(closed) return;
        connection=name;
        do { nonce=random.nextInt()&0xffffffffL; } while(nonce==0 || nonce==69420 || nonce==69421);
        ui.state("Reading radio configuration…",false);
        link.send(Proto.config(nonce),0);
        h.postDelayed(configTimeout,60000);
    }
    public void receive(byte[] bytes) {
        if(closed) return;
        try {
            List<Proto.Field> f=Proto.parse(bytes);
            if(Proto.get(f,3)!=null) myNode=Proto.num(Proto.parse(Proto.blob(f,3)),1);
            if(Proto.get(f,4)!=null) {
                List<Proto.Field> n=Proto.parse(Proto.blob(f,4));
                List<Proto.Field> u=Proto.parse(Proto.blob(n,2));
                String name=Proto.text(u,2);
                if(name.isEmpty()) name=Proto.text(u,3);
                if(!name.isEmpty()) names.put(Proto.num(n,1),name);
            }
            if(Proto.get(f,10)!=null) {
                List<Proto.Field> c=Proto.parse(Proto.blob(f,10));
                if(Proto.num(c,3)==1) {
                    int index=(int)Proto.num(c,1);
                    if(index>=0 && index<8) {
                        channel=index;
                        String n=Proto.text(Proto.parse(Proto.blob(c,2)),3);
                        channelName=n.isEmpty()?"Primary channel":n;
                    }
                }
            }
            if(Proto.get(f,7)!=null && Proto.num(f,7)==nonce && !ready) {
                h.removeCallbacks(configTimeout);
                if(myNode==0 || channel<0) { ui.failed("Radio did not provide its node ID and primary channel."); return; }
                ready=true;
                ui.state(connection+"\n"+channelName+" · channel broadcast",true);
                while(!backlog.isEmpty()) packet(backlog.removeFirst());
                h.postDelayed(heartbeat,20000);
            }
            if(Proto.get(f,2)!=null) {
                byte[] p=Proto.blob(f,2);
                if(ready) packet(p);
                else { if(backlog.size()==100) backlog.removeFirst(); backlog.addLast(p); }
            }
            if(Proto.num(f,8)!=0 && ready) ui.failed("Radio restarted. Reconnecting…");
        } catch(IllegalArgumentException ignored) {
            // Ignore malformed records without displaying or logging payloads.
        }
    }
    private void packet(byte[] b) {
        List<Proto.Field> p=Proto.parse(b);
        if(Proto.get(p,4)==null) return;
        List<Proto.Field> d=Proto.parse(Proto.blob(p,4));
        long port=Proto.num(d,1),sender=Proto.num(p,1),id=Proto.num(p,6);
        if(port==5) {
            long request=Proto.num(d,6);
            if(pending.containsKey(request)) {
                List<Proto.Field> routing=Proto.parse(Proto.blob(d,2));
                long error=Proto.num(routing,3);
                if(error!=0) { pending.remove(request); ui.delivery(request,"Radio error "+error+" · not delivered"); }
            }
            return;
        }
        if(port==4) {
            List<Proto.Field> u=Proto.parse(Proto.blob(d,2));
            String name=Proto.text(u,2);
            if(!name.isEmpty()) names.put(sender,name);
            return;
        }
        if(port!=1 || Proto.num(p,3)!=channel || Proto.num(p,2)!=0xffffffffL) return;
        if(sender==myNode && pending.containsKey(id)) return;
        String text=Proto.text(d,2);
        if(text.isEmpty()) return;
        String label=names.get(sender);
        if(label==null) label=String.format(Locale.US,"!%08x",sender);
        // Read receive metadata only. No ping, reply, or other on-air transmission.
        if(sender!=myNode) {
            SignalInfo sample=SignalInfo.read(b);
            if(sample!=null) ui.signal(label,sample);
        }
        String key=myNode+":"+channel+":"+sender+":"+id;
        if(id==0) key += ":"+Proto.num(p,7)+":"+text.hashCode();
        ui.received(key,label,text);
    }
    public String send(String text) {
        if(!ready || closed) return "Connect to a radio first.";
        if(text.trim().isEmpty()) return "Type a message first.";
        if(text.getBytes(StandardCharsets.UTF_8).length>180) return "Keep the message within 180 UTF-8 bytes.";
        long now=SystemClock.elapsedRealtime();
        if(lastSend!=0 && now-lastSend<4000) return "Wait a few seconds before sending again.";
        long id;
        do { id=random.nextInt()&0xffffffffL; } while(id==0 || pending.containsKey(id));
        byte[] bytes=Proto.textPacket(text,channel,id);
        if(link instanceof BleLink && bytes.length>((BleLink)link).maxPacket())
            return "Bluetooth MTU is too small for this message. Shorten it, reconnect, or use USB.";
        lastSend=now; pending.put(id,text);
        ui.outgoing(id,text,"Sending to local radio…");
        link.send(bytes,id);
        return null;
    }
    public void written(long id) {
        if(id!=0 && pending.containsKey(id)) ui.delivery(id,"Written to radio · recipient delivery unconfirmed");
    }
    public void close() {
        closed=true; ready=false; h.removeCallbacksAndMessages(null);
        for(long id:pending.keySet()) ui.delivery(id,"Session ended · recipient delivery unconfirmed");
        pending.clear(); backlog.clear();
    }
}
