// Copyright (c) 2026 Tomasz Abramowicz
// SPDX-License-Identifier: MIT
package com.tomaszabramowicz.meshchat;
public interface Link {
    interface Events {
        void onOpen(String name);
        void onBytes(byte[] bytes);
        void onClosed(String reason);
        void onWritten(long tag);
    }
    void send(byte[] bytes, long tag);
    void close();
}
