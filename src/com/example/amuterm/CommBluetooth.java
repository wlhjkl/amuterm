package com.example.amuterm;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.ParcelUuid;

public class CommBluetooth extends CommChannel {
    private static final ParcelUuid UUID_SPP = ParcelUuid.fromString("00001101-0000-1000-8000-00805F9B34FB");
    public static final String type = "bluetooth";

    private BluetoothDevice device;
    private BluetoothSocket socket;
    private InputStream inputStream;
    private OutputStream outputStream;

    @Override public void open(String name){
        close();
        if((name != null)&&(name.length() > 0)){try{
            for(BluetoothDevice dev : pairedDevices()){
                if(name.equals(deviceName(dev))){
                    device = dev;
                    socket = device.createRfcommSocketToServiceRecord(UUID_SPP.getUuid());
                    socket.connect();
                    inputStream = socket.getInputStream();
                    outputStream = socket.getOutputStream();
                    isOpen.set(true);
                }
            }
        }catch(Exception e){}}
    }
    @Override public void close(){
        if(isOpen.get()){
            try{ socket.close();
            }catch(Exception e){}
            finally{ isOpen.set(false); }
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
            outputStream.write(data);
        }catch(Exception e){}}
    }
    @Override public void write(List<Byte> data){
        if(isOpen.get()){ try {
            int i=0; byte[] ar = new byte[data.size()];
            for(byte b : data) ar[i++] = b;
            outputStream.write(ar);
        }catch(Exception e){}}
    }
    
    public static String deviceName(BluetoothDevice dev){
        return dev.getName() + " (" + dev.getAddress() + ")";
    }
    public static List<BluetoothDevice> pairedDevices(){
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if(adapter != null){
            ArrayList<BluetoothDevice> devs = new ArrayList<BluetoothDevice>();
            for(BluetoothDevice dev : adapter.getBondedDevices()){
                ParcelUuid[] uuids = dev.getUuids();
                if((uuids != null)&&(uuids.length > 0)&&(uuids[0].equals(UUID_SPP))) devs.add(dev);
            }
            return devs;
        } else return new ArrayList<BluetoothDevice>();
    }
    public static List<String> pairedNames(){
        ArrayList<String> names = new ArrayList<String>();
        for(BluetoothDevice dev : pairedDevices()) names.add(deviceName(dev));
        Collections.sort(names);
        return names;
    }
}
