// Copyright (c) 2026 Tomasz Abramowicz
// SPDX-License-Identifier: MIT
package com.tomaszabramowicz.meshchat;

import android.hardware.usb.*;
import android.os.*;
import com.hoho.android.usbserial.driver.*;
import com.hoho.android.usbserial.util.SerialInputOutputManager;
import java.util.concurrent.*;

public final class UsbLink implements Link, SerialInputOutputManager.Listener {
    private final UsbManager manager;
    private final UsbSerialDriver driver;
    private final Events events;
    private final Handler h=new Handler(Looper.getMainLooper());
    private final ExecutorService writer=Executors.newSingleThreadExecutor();
    private final Proto.Framer framer=new Proto.Framer();
    private UsbSerialPort port;
    private UsbDeviceConnection connection;
    private SerialInputOutputManager io;
    private volatile boolean closed=false;
    public UsbLink(UsbManager m,UsbSerialDriver d,Events e) { manager=m; driver=d; events=e; }
    public void connect() {
        writer.execute(()->{
            try {
                if(closed) return;
                connection=manager.openDevice(driver.getDevice());
                if(connection==null) throw new Exception("USB permission was not granted");
                port=driver.getPorts().get(0);
                port.open(connection);
                if(closed) { port.close(); connection.close(); return; }
                port.setParameters(115200,8,UsbSerialPort.STOPBITS_1,UsbSerialPort.PARITY_NONE);
                // Do not toggle DTR/RTS: some ESP32 boards use these for reset/boot.
                // Wake/resynchronize a stream left by a previous client.
                port.write(new byte[]{(byte)0x94,(byte)0x94,(byte)0x94,(byte)0x94},3000);
                if(closed) { port.close(); connection.close(); return; }
                io=new SerialInputOutputManager(port,this);
                io.start();
                h.post(()->{ if(!closed) events.onOpen("USB · "+driver.getClass().getSimpleName()); });
            } catch(Exception e) { fail("USB: "+e.getMessage()); }
        });
    }
    @Override public void send(byte[] bytes,long tag) {
        if(closed) return;
        try {
            writer.execute(()->{
                if(closed) return;
                try { port.write(Proto.frame(bytes),3000); h.post(()->{ if(!closed) events.onWritten(tag); }); }
                catch(Exception e) { fail("USB write: "+e.getMessage()); }
            });
        } catch(RejectedExecutionException ignored) {}
    }
    @Override public void onNewData(byte[] data) {
        framer.accept(data,b->h.post(()->{ if(!closed) events.onBytes(b); }));
    }
    @Override public void onRunError(Exception e) { fail("USB disconnected: "+e.getMessage()); }
    private void fail(String message) {
        h.post(()->{ if(!closed) { close(); events.onClosed(message); } });
    }
    @Override public void close() {
        closed=true;
        if(io!=null) io.stop();
        writer.shutdownNow();
        try { if(port!=null) port.close(); } catch(Exception ignored) {}
        if(connection!=null) connection.close();
    }
}
