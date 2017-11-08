package com.example.amuterm;

import java.lang.Integer;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;
import java.util.Arrays;
import java.io.InputStream;
import java.io.OutputStream;

public class CommSocket extends CommChannel {
    public static String type = "socket";
    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    
    @Override public void open(String name){
state = 1;
        close();
state = 2;
        if((name != null)&&(name.length() > 0)){ try{
state = 3;
            int port = 8080;
            if(name.contains(":")){
                port = Integer.parseInt(name.substring(name.indexOf(":")+1));
                name = name.substring(0,name.indexOf(":"));
            }
state = 4;
stateString = name + ":" + port;
            socket = new Socket(InetAddress.getByName(name), port);
state = 5;
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
state = 6;
            isOpen.set(true);
        }catch(Exception e){
            stateString += " : " + e.toString();
        }}
    }
    @Override public void close(){
        if(isOpen.get()){ try{
            socket.close();
            isOpen.set(false);
        }catch(Exception e){}
        }
    }

    @Override public byte[] read(){
        if(isOpen.get()){ try{
            int len = inputStream.available();
            byte[] data = new byte[len];
            len = inputStream.read(data);
            if(len != data.length) return Arrays.copyOf(data, len);
            else return data;
        }catch(Exception e){ return new byte[0]; }}
        return new byte[0];
    }
    @Override public int read(byte data[]){
        if(isOpen.get()){ try{
            return inputStream.read(data);
        }catch(Exception e){ return 0; }}
        return 0;
    }
    @Override public void write(byte data[]){
        if(isOpen.get()){ try {
stateString = "";
            outputStream.write(data);
        }catch(Exception e){ stateString = e.toString(); }}
    }
    @Override public void write(List<Byte> data){
        if(isOpen.get()){ try {
            int i=0; byte[] ar = new byte[data.size()];
            for(byte b : data) ar[i++] = b;
            outputStream.write(ar);
        }catch(Exception e){}}
    }
}
