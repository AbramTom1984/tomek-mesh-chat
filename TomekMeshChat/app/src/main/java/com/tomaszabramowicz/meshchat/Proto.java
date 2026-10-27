// Copyright (c) 2026 Tomasz Abramowicz
// SPDX-License-Identifier: MIT
package com.tomaszabramowicz.meshchat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Small bounded protobuf wire codec, not a full Meshtastic SDK. */
public final class Proto {
    public static final class Field {
        public final int number, wire;
        public final long value;
        public final byte[] data;
        Field(int n, int w, long v, byte[] d) { number=n; wire=w; value=v; data=d; }
        public String text() { return new String(data, StandardCharsets.UTF_8); }
    }
    public static final class Writer {
        private final ByteArrayOutputStream out=new ByteArrayOutputStream();
        private void raw(long v) {
            while ((v & ~0x7fL)!=0) { out.write(((int)v & 127)|128); v >>>= 7; }
            out.write((int)v);
        }
        public Writer var(int n,long v) { raw(((long)n<<3)); raw(v); return this; }
        public Writer fixed(int n,long v) {
            raw(((long)n<<3)|5);
            for(int i=0;i<4;i++) out.write((int)(v >>> (8*i)) & 255);
            return this;
        }
        public Writer bytes(int n,byte[] b) {
            raw(((long)n<<3)|2); raw(b.length); out.write(b,0,b.length); return this;
        }
        public byte[] build() { return out.toByteArray(); }
    }
    private static long readVar(byte[] b,int[] p) {
        long v=0;
        for(int i=0;i<10;i++) {
            if(p[0]>=b.length) throw new IllegalArgumentException("Truncated varint");
            int x=b[p[0]++]&255;
            if(i==9 && x>1) throw new IllegalArgumentException("Varint overflow");
            v |= (long)(x & 127) << (7*i);
            if((x&128)==0) return v;
        }
        throw new IllegalArgumentException("Invalid varint");
    }
    public static List<Field> parse(byte[] b) {
        if(b.length>4096) throw new IllegalArgumentException("Oversized protobuf");
        ArrayList<Field> fields=new ArrayList<>();
        int[] p={0};
        while(p[0]<b.length) {
            long tag=readVar(b,p);
            if(tag<=0 || tag>Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid tag");
            int n=(int)(tag>>>3),w=(int)(tag&7);
            if(n==0) throw new IllegalArgumentException("Field zero");
            long v=0; byte[] data=new byte[0];
            if(w==0) v=readVar(b,p);
            else if(w==5 || w==1) {
                int len=w==5?4:8;
                if(b.length-p[0]<len) throw new IllegalArgumentException("Truncated fixed field");
                for(int i=0;i<len;i++) v |= (long)(b[p[0]++]&255) << (8*i);
            } else if(w==2) {
                long len=readVar(b,p);
                if(len<0 || len>b.length-p[0]) throw new IllegalArgumentException("Invalid length");
                data=Arrays.copyOfRange(b,p[0],p[0]+(int)len); p[0]+=(int)len;
            } else throw new IllegalArgumentException("Unsupported wire type");
            fields.add(new Field(n,w,v,data));
        }
        return fields;
    }
    public static Field get(List<Field> f,int n) {
        for(int i=f.size()-1;i>=0;i--) if(f.get(i).number==n) return f.get(i);
        return null;
    }
    public static long num(List<Field> f,int n) { Field x=get(f,n); return x==null?0:x.value; }
    public static byte[] blob(List<Field> f,int n) { Field x=get(f,n); return x==null?new byte[0]:x.data; }
    public static String text(List<Field> f,int n) { return new String(blob(f,n),StandardCharsets.UTF_8); }
    public static byte[] config(long nonce) { return new Writer().var(3,nonce).build(); }
    public static byte[] textPacket(String text,int channel,long id) {
        byte[] data=new Writer().var(1,1).bytes(2,text.getBytes(StandardCharsets.UTF_8)).build();
        byte[] packet=new Writer().fixed(2,0xffffffffL).var(3,channel)
            .bytes(4,data).fixed(6,id).var(9,3).build();
        return new Writer().bytes(1,packet).build();
    }
    public static byte[] frame(byte[] b) {
        if(b.length>512) throw new IllegalArgumentException("Frame too large");
        byte[] out=new byte[b.length+4];
        out[0]=(byte)0x94; out[1]=(byte)0xc3;
        out[2]=(byte)(b.length>>>8); out[3]=(byte)b.length;
        System.arraycopy(b,0,out,4,b.length); return out;
    }
    public interface Receiver { void receive(byte[] bytes); }
    public static final class Framer {
        private int state=0,length=0;
        private final ByteArrayOutputStream body=new ByteArrayOutputStream();
        public void accept(byte[] b,Receiver receiver) {
            for(byte raw:b) {
                int x=raw&255;
                switch(state) {
                    case 0: if(x==0x94) state=1; break;
                    case 1: state=x==0xc3?2:(x==0x94?1:0); break;
                    case 2: length=x<<8; state=3; break;
                    case 3:
                        length|=x; body.reset();
                        if(length==0 || length>512) state=0; else state=4;
                        break;
                    case 4:
                        body.write(x);
                        if(body.size()==length) { state=0; receiver.receive(body.toByteArray()); }
                        break;
                }
            }
        }
    }
}
