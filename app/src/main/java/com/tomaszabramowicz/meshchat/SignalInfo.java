// Copyright (c) 2026 Tomasz Abramowicz
// SPDX-License-Identifier: MIT
package com.tomaszabramowicz.meshchat;

import java.util.List;

/** Read-only receive metadata. Never sends or changes radio settings. */
public final class SignalInfo {
    public final int rssi;
    public final Float snr;
    public final long receivedAtSeconds;
    private SignalInfo(int rssi,Float snr,long receivedAtSeconds) {
        this.rssi=rssi;this.snr=snr;this.receivedAtSeconds=receivedAtSeconds;
    }
    public static SignalInfo read(byte[] packet) {
        List<Proto.Field> fields=Proto.parse(packet);
        if(Proto.num(fields,14)!=0) return null; // Exclude packets marked as MQTT.
        Proto.Field rf=Proto.get(fields,12);
        if(rf==null || rf.wire!=0) return null;
        int rssi=(int)rf.value;
        if(rssi==0) return null; // Default is not a measured 0 dBm.
        Proto.Field sf=Proto.get(fields,8);
        Float snr=null;
        if(sf!=null && sf.wire==5) {
            float value=Float.intBitsToFloat((int)sf.value);
            if(!Float.isNaN(value) && !Float.isInfinite(value)) snr=value;
        }
        // A missing SNR field is not invented; proto3 can omit a zero value.
        return new SignalInfo(rssi,snr,Proto.num(fields,7));
    }
    public long ageMs(long nowMs) {
        if(receivedAtSeconds<=0) return -1;
        long timestamp=receivedAtSeconds*1000L;
        if(timestamp<946684800000L || timestamp>nowMs+120000L) return -1;
        return Math.max(0,nowMs-timestamp);
    }
}
