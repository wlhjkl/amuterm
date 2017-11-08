package com.example.amuterm;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.Context;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbDeviceConnection;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Arrays;

public class CommSerial extends CommChannel {
    public String type = "serial";
    private UsbSerialPort port;
    private byte[] rxBuf = new byte[8192];

    @Override public void configSerial(String baud, String parity, String stops){
        if(isOpen.get()){
            try{
                port.setParameters(java.lang.Integer.parseInt(baud), UsbSerialPort.DATABITS_8,
                    stops.equals("1.0") ? UsbSerialPort.STOPBITS_1 :
                    stops.equals("1.5") ? UsbSerialPort.STOPBITS_1_5 :
                    stops.equals("2.0") ? UsbSerialPort.STOPBITS_2 : UsbSerialPort.STOPBITS_1,
                    parity.equals("none") ? UsbSerialPort.PARITY_NONE :
                    parity.equals("odd") ? UsbSerialPort.PARITY_ODD :
                    parity.equals("even") ? UsbSerialPort.PARITY_EVEN :
                    parity.equals("mark") ? UsbSerialPort.PARITY_MARK :
                    parity.equals("space") ? UsbSerialPort.PARITY_SPACE : UsbSerialPort.PARITY_NONE
                );
            } catch (Exception e){
            }
        }
    };
    
    @Override public void open(String name){
state = 1;
        close();
state = 2;
        if((name != null)&&(name.length() > 0)&&(ports.containsKey(name))){
state = 3;
            port = ports.get(name);
state = 4;
            try{
state = 5;
                UsbDeviceConnection connection = manager.openDevice( drivers.get(name).getDevice() );
stateString = drivers.get(name) + " : " + connection + " : " + port;
state = 6;
                port.open(connection);
state = 7;
                isOpen.set(true);
state = 8;
            } catch (Exception e){
//                stateString = e.toString();
//state = 9;
                port = null;
            }
        }
    }
    @Override public void close(){
        if(isOpen.get()){ try {
            port.purgeHwBuffers(true, true);
            port.close();
            isOpen.set(false);
        } catch (java.io.IOException e){
        }}
    }

    @Override public byte[] read(){
        if(isOpen.get()){ try{
            int len = port.read(rxBuf, 0);
            return Arrays.copyOf(rxBuf, len);
        } catch (Exception e){ return new byte[0];
        }}
        return new byte[0];
    }
    @Override public int read(byte data[]){
        if(isOpen.get()){
            try{ return port.read(data, 0);
            } catch (Exception e){ return 0; }
        }
        return 0;
    }

    @Override public void write(byte data[]){
        if(isOpen.get()){ try {
            port.write(data, 0);
        } catch (Exception e) {}}
    }
    @Override public void write(List<Byte> data){
        if(isOpen.get()){ try {
            int i=0; byte[] ar = new byte[data.size()];
            for(byte b : data) ar[i++] = b;
            port.write(ar, 0);
        } catch (Exception e) {}}
    }
    
    
    public static String getShortName(String name){
        if(name.toLowerCase().contains("cdc")) return "cdc";
        else if(name.toLowerCase().contains("ch3")) return "ch34x";
        else if(name.toLowerCase().contains("cp21")) return "cp21xx";
        else if(name.toLowerCase().contains("ftdi")) return "ftdi";
        else if(name.toLowerCase().contains("prolific")) return "prolific";
        else return "";
    }
    public static String getPortName(UsbSerialPort port){
        return getShortName(port.getClass().getSimpleName()) + "." + port.getPortNumber();
    }
    private static HashMap<String,UsbSerialPort> ports = new HashMap<String,UsbSerialPort>();
    private static HashMap<String,UsbSerialDriver> drivers = new HashMap<String,UsbSerialDriver>();
    private static UsbManager manager;
    public static List<String> getPorts(Activity activity){
        ports.clear(); drivers.clear();
        List<String> portsNames = new ArrayList<String>();
        manager = (UsbManager)activity.getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        for(UsbSerialDriver drv : availableDrivers){
            for(UsbSerialPort port : drv.getPorts()){
                portsNames.add(getPortName(port));
                ports.put(getPortName(port), port);
                drivers.put(getPortName(port), drv);
            }
        }
        return portsNames;
    }
    public static UsbSerialPort findPort(Activity activity, String name){
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers((UsbManager)activity.getSystemService(Context.USB_SERVICE));
        for(UsbSerialDriver drv : availableDrivers){
            for(UsbSerialPort port : drv.getPorts())
                if(getPortName(port).equals(name)) return port;
        }
        return null;
    }
}
