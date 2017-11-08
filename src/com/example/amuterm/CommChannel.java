package com.example.amuterm;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class CommChannel {
    public static String type = "generic";
    public AtomicBoolean isOpen = new AtomicBoolean(false);
    public int state = 0;
    public String stateString = "";
    
    public void open(String name){ isOpen.set(true); }
    public void close(){ isOpen.set(false); }
    
    public byte[] read(){ return new byte[0]; }
    public int read(byte data[]){ return 0; }
    public void write(byte data[]){ }
    public void write(List<Byte> data){ }
    
    public void configSerial(String baud, String parity, String stops){};
}
