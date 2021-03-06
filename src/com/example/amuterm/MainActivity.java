package com.example.amuterm;

import java.lang.Thread;
import java.lang.Runnable;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Arrays;
import java.util.TimeZone;
import java.text.SimpleDateFormat;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;

import android.app.TabActivity;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Surface;
import android.widget.TabHost;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;

import com.dodo.tools.*;

public class MainActivity extends TabActivity {
    private TabActivity activity = this;
    
    private CommGeneric channel;  // connected channel
    private int devBt,devUsb;
    
    private static final int CH_BT=0, CH_SERIAL=1, CH_TCP=2;
    
    private EditText txText, rxText;
    private boolean txCr,txLf,txHex,rxEn,rxCrLf,rxHex;
    
    private String  toastMsg;
    private boolean toastLong;
    private void toast(String msg, boolean isLong){ toastMsg = msg; toastLong = isLong;
        runOnUiThread(new Runnable(){ @Override public void run(){
            (Toast.makeText(getApplicationContext(), toastMsg, toastLong ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT)).show(); }
        });
    }
    
    // socket poll timer/task
    private String  txString;
    private boolean rxClear = false;
    private int rxLine,rxLinePrev, pollCnt = 0;
    private TextView rxLineText;
    private final ConcurrentLinkedQueue<String> rxQueue = new ConcurrentLinkedQueue<String>();
    private final ConcurrentLinkedQueue<byte[]> txQueue = new ConcurrentLinkedQueue<byte[]>();
    private byte[] txBuf = new byte[8192], rxBuf = new byte[8192];
    private final Timer pollTimer = new Timer();
    private final TimerTask pollTask = new TimerTask(){
        private StringBuilder sb = new StringBuilder();
        @Override public void run(){
            pollCnt++;
            if((channel != null)&&(channel.isOpen.get())){
                int len = channel.read(rxBuf);
if(channel.stateString.length() > 0) Log.e("AMuTerm", "RXE: " + channel.stateString);
                if(len > 0){
                    sb.setLength(0);
                    for(int i=0; i<len; i++){
                        if(rxBuf[i] == '\n'){
                            if(rxCrLf) sb.append("\\n");
                            sb.append('\n');
                            rxLine++;
                        }else if(rxBuf[i] == '\r'){
                            if(rxCrLf) sb.append("\\r");
                        }else if((rxBuf[i] < ' ')||(rxBuf[i] > 127)){
                            if(rxHex)sb.append(String.format("%02X", rxBuf[i])).append(",");//.append("\\x")
                        }else{
                            if(rxHex)sb.append(String.format("%02X", rxBuf[i])).append(",");//.append("\\x")
                            else sb.append((char)rxBuf[i]);
                        }
                    }
                    if(rxEn){
                        rxQueue.add(sb.toString());
                    }
                }
            }
            // update RX text
            if(!rxQueue.isEmpty() || rxClear){
                runOnUiThread(new Runnable(){ @Override public void run(){
                    if(rxClear){
                        rxClear = false;
                        rxQueue.clear();
                        rxText.setText("");
                        rxLine = 0;
                    }
                    if(!rxQueue.isEmpty()){
                        StringBuilder s = new StringBuilder();
                        while(!rxQueue.isEmpty()) s.append(rxQueue.poll());
                        rxText.append(s.toString());
                    }
                }});
            }
            // show RX line
            if(rxLine != rxLinePrev){
                runOnUiThread(new Runnable(){ @Override public void run(){
                    rxLineText.setText(String.format("% 4d",rxLine));
                }});
            }
            // send bytes from TX queue
            if(!txQueue.isEmpty()){
                byte[] data = txQueue.poll();
                if((channel != null)&&(channel.isOpen.get())){
                    channel.write(data);
if(channel.stateString.length() > 0) Log.e("AMuTerm", "TXE: " + channel.stateString);
                }
            }
        }
    };

    private static final String PREFS_NAME = "amuterm";
    private void prefsLoad(){
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, 0);
        
        ((Spinner)findViewById(R.id.channelType)).setSelection(prefs.getInt("channel.type", 0));
        devBt = prefs.getInt("dev.bt", 0);
        devUsb = prefs.getInt("dev.usb", 0);

        ((Spinner)findViewById(R.id.serialBauds)).setSelection(
            ((ArrayAdapter<String>)((Spinner)findViewById(R.id.serialBauds)).getAdapter()).getPosition(prefs.getString("serial.baud", "9600")));
        ((Spinner)findViewById(R.id.serialParity)).setSelection(
                ((ArrayAdapter<String>)((Spinner)findViewById(R.id.serialParity)).getAdapter()).getPosition(prefs.getString("serial.parity", "none")));
        ((Spinner)findViewById(R.id.serialStops)).setSelection(
                ((ArrayAdapter<String>)((Spinner)findViewById(R.id.serialStops)).getAdapter()).getPosition(prefs.getString("serial.stops", "1.0")));
        
        ((EditText)findViewById(R.id.tcpAddr)).setText(prefs.getString("tcp.addr", "127.0.0.1"));
        ((EditText)findViewById(R.id.tcpPort)).setText(prefs.getString("tcp.port", "8080"));
        
        txCr     = prefs.getBoolean("tx.cr",  false);
        txLf     = prefs.getBoolean("tx.lf",  false);
        txHex    = prefs.getBoolean("tx.hex", false);
        txString = prefs.getString("tx.text", "");
        rxEn     = prefs.getBoolean("rx.en",  true);
        rxCrLf   = prefs.getBoolean("rx.crlf",  false);
        rxHex    = prefs.getBoolean("rx.hex", false);
    }
    private void prefsSave(){
        getSharedPreferences(PREFS_NAME, 0).edit()
        .putInt("channel.type", ((Spinner)findViewById(R.id.channelType)).getSelectedItemPosition())
        .putInt("dev.bt", ((Spinner)findViewById(R.id.btDevs)).getSelectedItemPosition())
        .putInt("dev.usb", ((Spinner)findViewById(R.id.serialPorts)).getSelectedItemPosition())
        .putString("serial.baud", (String)((Spinner)findViewById(R.id.serialBauds)).getSelectedItem())
        .putString("serial.parity", (String)((Spinner)findViewById(R.id.serialParity)).getSelectedItem())
        .putString("serial.stops", (String)((Spinner)findViewById(R.id.serialStops)).getSelectedItem())
        .putString("tcp.addr", ((EditText)findViewById(R.id.tcpAddr)).getText().toString())
        .putString("tcp.port", ((EditText)findViewById(R.id.tcpPort)).getText().toString())
        .putBoolean("tx.cr",  txCr)
        .putBoolean("tx.lf",  txLf)
        .putBoolean("tx.hex", txHex)
        .putString("tx.text", txText.getText().toString())
        .putBoolean("rx.en",  rxEn)
        .putBoolean("rx.crlf",rxCrLf)
        .putBoolean("rx.hex", rxHex)
        .commit();
    }

    // update spinner of USB devices
    private void updateUsb(){
        ArrayAdapter<String> a = (ArrayAdapter<String>)((Spinner)findViewById(R.id.serialPorts)).getAdapter();
        a.clear();
        a.addAll(CommSerial.getPorts(this));
    }
    // update spinner of Bluetooth devices
    private void updateBluetooth(){
        ArrayAdapter<String> a = (ArrayAdapter<String>)((Spinner)findViewById(R.id.btDevs)).getAdapter();
        a.clear();
        a.addAll(CommBluetooth.pairedNames());
    }

    // disconnect connected channel
    private void channelDisconnect(){
        new Thread(new Runnable(){ public void run(){
            if(channel != null){
                channel.close();
                channel = null;
                enableUI();
            }
        }}){{ start(); }};
    }
    
    private Spinner spChannelType;
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        prefsLoad();

        // tabs
        addTab("tab1", "connection", R.id.tab1);
        addTab("tab2", "terminal",   R.id.tab2);
        
        txText     = ((EditText)findViewById(R.id.txText));
        rxText     = ((EditText)findViewById(R.id.rxText));
        rxLineText = ((TextView)findViewById(R.id.rxLine));

        // channel connect
        ((Button)findViewById(R.id.bConnect)).setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View arg0){
                if(channel == null){
                    new Thread(new Runnable(){ public void run(){
                        runOnUiThread(new Runnable(){ @Override public void run(){
                            ((Button)findViewById(R.id.bConnect)).setEnabled(false);
                        }});
                        CommGeneric ch = null;
                        switch(spChannelType.getSelectedItemPosition()){
                        case CH_SERIAL:
                            String sp = (String)((Spinner)findViewById(R.id.serialPorts)).getSelectedItem();
                            ch = new CommSerial(sp){{
                                open();
                                if(isOpen.get())
                                    configSerial((String)((Spinner)findViewById(R.id.serialBauds)).getSelectedItem(), 
                                        (String)((Spinner)findViewById(R.id.serialParity)).getSelectedItem(),
                                        (String)((Spinner)findViewById(R.id.serialStops)).getSelectedItem());
                            }};
toast("Connecting to Serial: " + sp + " : " + ch.state + ((ch.stateString.length() > 0) ? (" : " + ch.stateString) : "") , false);
                            break;
                        case CH_TCP:
                            String tcpAddr = ((EditText)findViewById(R.id.tcpAddr)).getText().toString();
                            String tcpPort = ((EditText)findViewById(R.id.tcpPort)).getText().toString();
toast("Connecting to socket: " + tcpAddr + ":" + tcpPort, false);
                            ch = new CommSocket(tcpAddr + ":" + tcpPort){{open();}};
//toastMsg("Connecting to Socket: " + mChannel.state + " : " + mChannel.stateString, true);
                            break;
                        case CH_BT:
                            String btDev = (String)((Spinner)findViewById(R.id.btDevs)).getSelectedItem();
toast("Connecting to Bluetooth: " + btDev, false);
                            ch = new CommBluetooth(btDev){{open();}};
                            break;
                        }
                        if((ch != null)&&(ch.isOpen.get())){
                            disableUI();
                            channel = ch;
                            runOnUiThread(new Runnable(){ @Override public void run(){
                                // fix screen rotation
                                int rot = activity.getWindowManager().getDefaultDisplay().getRotation();
                                activity.setRequestedOrientation( (rot == Surface.ROTATION_270) ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE :
                                    (rot == Surface.ROTATION_90) ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE :
                                    (rot == Surface.ROTATION_180) ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT :
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                );
                                rxClear = true; // clear RX
                            }});
                        }
                    }}){{ start(); }};
                }
            }
        });
        // channel disconnect
        ((Button)findViewById(R.id.bDisconnect)).setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View arg0){
                if(channel != null){
                    // unfix screen orientation
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                    channelDisconnect(); // disconnect
                }
            }
        });
        // channel type
        spChannelType = (Spinner)findViewById(R.id.channelType);
        ((Spinner)findViewById(R.id.channelType)).setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id){
                ((LinearLayout)findViewById(R.id.chSerial)).setVisibility((position == CH_SERIAL) ? View.VISIBLE : View.GONE);
                ((LinearLayout)findViewById(R.id.chTcp)).setVisibility((position == CH_TCP) ? View.VISIBLE : View.GONE);
                ((LinearLayout)findViewById(R.id.chBt)).setVisibility((position == CH_BT)   ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent){}
        });

        // serial ports
        ((Spinner)findViewById(R.id.serialPorts)).setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>()));
        updateUsb();
        ((Spinner)findViewById(R.id.serialPorts)).setSelection(devUsb);
        ((Button)findViewById(R.id.serialReload)).setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View arg0){ updateUsb(); }
        });
        
        // bluetooth devices
        ((Spinner)findViewById(R.id.btDevs)).setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>()));
        updateBluetooth();
        ((Spinner)findViewById(R.id.btDevs)).setSelection(devBt);
        ((Button)findViewById(R.id.btReload)).setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View arg0){ updateBluetooth(); }
        });
        
        // TX/RX options
        ((CheckBox)findViewById(R.id.txCr)).setChecked(txCr);
        ((CheckBox)findViewById(R.id.txCr)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override public void onCheckedChanged(CompoundButton buttonView,boolean isChecked){ txCr = isChecked; }
        });
        ((CheckBox)findViewById(R.id.txLf)).setChecked(txLf);
        ((CheckBox)findViewById(R.id.txLf)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override public void onCheckedChanged(CompoundButton buttonView,boolean isChecked){ txLf = isChecked; }
        });
        ((CheckBox)findViewById(R.id.txHex)).setChecked(txHex);
        ((CheckBox)findViewById(R.id.txHex)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override public void onCheckedChanged(CompoundButton buttonView,boolean isChecked){ txHex = isChecked; }
        });
        ((Button)findViewById(R.id.txClear)).setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View arg0){ txText.setText(""); }
        });
        ((Button)findViewById(R.id.txSend)).setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View arg0){
                if((channel != null)&&(txText.length() > 0)){
                    String s = txText.getText().toString();
                    int len = 0;
                    for(int i=0; i<s.length(); i++){
                        int c = (int)s.charAt(i);
                        if((c >= 32)&&(c <= 127)) txBuf[len++] = (byte)c;
                        else if((c == 10)||(c == 13)) txBuf[len++] = (byte)c;
                    }
                    if(txCr) txBuf[len++] = 13;
                    if(txLf) txBuf[len++] = 10;
//StringBuilder sb = new StringBuilder();
//for(int i=0; i<len; i++) sb.append(String.format("%02X", mTxBuf[i])).append(',');
//toastMsg("send: " + sb.toString(), true);
                    if(len > 0) txQueue.add(Arrays.copyOf(txBuf, len));
//toast("send: " + txQueue.size() + " " + pollCnt, false);
                }
            }
        });
        txText.setText(txString);
 
        ((CheckBox)findViewById(R.id.rxEn)).setChecked(rxEn);
        ((CheckBox)findViewById(R.id.rxEn)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override public void onCheckedChanged(CompoundButton buttonView,boolean isChecked){ rxEn = isChecked; }
        });
        ((CheckBox)findViewById(R.id.rxCrLf)).setChecked(rxCrLf);
        ((CheckBox)findViewById(R.id.rxCrLf)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override public void onCheckedChanged(CompoundButton buttonView,boolean isChecked){ rxCrLf = isChecked; }
        });
        ((CheckBox)findViewById(R.id.rxHex)).setChecked(rxHex);
        ((CheckBox)findViewById(R.id.rxHex)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override public void onCheckedChanged(CompoundButton buttonView,boolean isChecked){ rxHex = isChecked; }
        });
        ((Button)findViewById(R.id.rxClear)).setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View arg0){ rxClear = true; }
        });
        ((Button)findViewById(R.id.rxSave)).setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View arg0){
                if(rxText.length() > 0){
                    if(Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())){
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd_HH.mm.ss");
                        sdf.setTimeZone(TimeZone.getDefault());
                        String name = "log_" + sdf.format(new java.util.Date()) + ".txt";
                        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), name);
                        try{
                            BufferedOutputStream fo = new BufferedOutputStream(new FileOutputStream(file, false));
                            fo.write(rxText.getText().toString().getBytes("US-ASCII"));
                            fo.flush(); fo.close();
toast("Saved to Downloads/" + name, true);
                        }catch(Exception e){
toast("Failed save to Downloads/" + name + "\n" + e.toString(), true);
                        }
                    }else{
toast("External media not available", true); 
                    }
                }
            }
        });

        // start poll timer
        pollTimer.scheduleAtFixedRate(pollTask, 100, 50);
        
        // request write permission for Ver>22
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1){
            try{
                activity.getClass().getMethod("requestPermissions", new Class[]{ String[].class, int.class })
                    .invoke(activity, new String[]{"android.permission.READ_EXTERNAL_STORAGE","android.permission.WRITE_EXTERNAL_STORAGE"}, 200);
            }catch(Exception e){ }
        }
    }
    @Override protected void onPause(){
        prefsSave();
        super.onPause();
    }
    @Override protected void onDestroy(){
        channelDisconnect();
        pollTimer.cancel();
        super.onDestroy();
    }

    // add new tab
    private void addTab(String tag, String title, int contentID){
        TabHost.TabSpec tabSpec = getTabHost().newTabSpec(tag);
        tabSpec.setIndicator(title, getResources().getDrawable(android.R.drawable.star_on));
        tabSpec.setContent(contentID);
        getTabHost().addTab(tabSpec);
    }

    private int uiIds[] = { R.id.bConnect,R.id.channelType,R.id.btDevs,R.id.btReload,R.id.tcpAddr,R.id.tcpPort,
        R.id.serialPorts,R.id.serialBauds,R.id.serialParity,R.id.serialStops,R.id.serialReload
    };
    private void disableUI(){
        runOnUiThread(new Runnable(){ @Override public void run(){
            ((Button)findViewById(R.id.bConnect)).setVisibility(View.GONE);
            ((Button)findViewById(R.id.bDisconnect)).setVisibility(View.VISIBLE);
            for(int id : uiIds)findViewById(id).setEnabled(false);
        }});
    }
    private void enableUI(){
        runOnUiThread(new Runnable(){ @Override public void run(){
            ((Button)findViewById(R.id.bConnect)).setVisibility(View.VISIBLE);
            ((Button)findViewById(R.id.bDisconnect)).setVisibility(View.GONE);

            for(int id : uiIds)findViewById(id).setEnabled(true);
        }});
    }
}
