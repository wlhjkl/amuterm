package com.example.amuterm;

import java.lang.Thread;
import java.lang.Runnable;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Arrays;
import java.util.TimeZone;
import java.text.SimpleDateFormat;
import java.io.File;
import java.io.FileOutputStream;

import android.app.TabActivity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
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
import android.text.TextWatcher;
import android.text.Editable;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.bluetooth.BluetoothAdapter;

import com.dodo.tools.*;



public class MainActivity extends TabActivity {
    TabActivity mActivity = this;
    
    private CommGeneric mChannel;  // connected channel
    
    private static final int CH_USB = 0, CH_TCP = 1, CH_BT = 2;
    
    private EditText mTextTx, mTextRx;
    private boolean mTxCr,mTxLf,mTxHex,mRxEn,mRxCrLf,mRxHex;
    
    private String  mToastMsg;
    private boolean mToastLong;
    private void toast(String msg, boolean isLong){ mToastMsg = msg; mToastLong = isLong;
        runOnUiThread(new Runnable(){ @Override public void run(){
            (Toast.makeText(getApplicationContext(), mToastMsg, mToastLong ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT)).show(); }
        });
    }
    
    // socket poll timer/task
    private String  mTxString;
    private boolean mRxClear = false;
    private int mRxLine = 0;
    private TextView mRxLineText;
    private final ConcurrentLinkedQueue<String> mRxQueue = new ConcurrentLinkedQueue<String>();
    private final ConcurrentLinkedQueue<byte[]> mTxQueue = new ConcurrentLinkedQueue<byte[]>();
    private final byte[] mTxBuf = new byte[8192];
    private final Timer pollTimer = new Timer();
    private final TimerTask pollTask = new TimerTask(){
        private byte[] rxBuf = new byte[8192];
        private StringBuilder sb = new StringBuilder();
        @Override public void run(){
            if((mChannel != null)&&(mChannel.isOpen.get())){
                int len = mChannel.read(rxBuf);
                if(len > 0){
                    sb.setLength(0);
                    for(int i=0; i<len; i++){
                        if(rxBuf[i] == '\n'){
                            if(mRxCrLf) sb.append("\\n");
                            sb.append('\n');
                            mRxLine++;
                        }else if(rxBuf[i] == '\r'){
                            if(mRxCrLf) sb.append("\\r");
                        }else if((rxBuf[i] < ' ')||(rxBuf[i] > 127)){
                            if(mRxHex)sb.append(String.format("%02X", rxBuf[i])).append(",");//.append("\\x")
                        }else{
                            if(mRxHex)sb.append(String.format("%02X", rxBuf[i])).append(",");//.append("\\x")
                            else sb.append((char)rxBuf[i]);
                        }
                    }
                    if(mRxEn){
                        mRxQueue.add(sb.toString());
                    }
                }
            }
            // update RX text
            if(!mRxQueue.isEmpty() || mRxClear){
                runOnUiThread(new Runnable(){ @Override public void run(){
                    if(mRxClear){
                        mRxClear = false;
                        mRxQueue.clear();
                        mTextRx.setText("");
                        mRxLine = 0;
                    }
                    if(!mRxQueue.isEmpty()){
                        StringBuilder s = new StringBuilder();
                        while(!mRxQueue.isEmpty()) s.append(mRxQueue.poll());
                        mTextRx.append(s.toString());
                    }
                    mRxLineText.setText(String.format("% 4d",mRxLine));
                }});
            }
            // send bytes from TX queue
            if(!mTxQueue.isEmpty()){
                byte[] data = mTxQueue.poll();
                if((mChannel != null)&&(mChannel.isOpen.get())){
//StringBuilder sb = new StringBuilder();
//for(int i=0; i<data.length; i++) sb.append(String.format("%02X", data[i])).append(',');
//toastMsg("send: " + sb.toString(), true);
                    mChannel.write(data);
//toastMsg("send: " + mChannel.stateString, true);
                }
            }
        }
    };

    private static final String PREFS_NAME = "amuterm";
    private void prefsLoad(){
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, 0);
        
        ((Spinner)findViewById(R.id.channelType)).setSelection(prefs.getInt("channel.type", 0));

        ((Spinner)findViewById(R.id.serialBauds)).setSelection(
            ((ArrayAdapter<String>)((Spinner)findViewById(R.id.serialBauds)).getAdapter()).getPosition(prefs.getString("serial.baud", "9600")));
        ((Spinner)findViewById(R.id.serialParity)).setSelection(
                ((ArrayAdapter<String>)((Spinner)findViewById(R.id.serialParity)).getAdapter()).getPosition(prefs.getString("serial.parity", "none")));
        ((Spinner)findViewById(R.id.serialStops)).setSelection(
                ((ArrayAdapter<String>)((Spinner)findViewById(R.id.serialStops)).getAdapter()).getPosition(prefs.getString("serial.stops", "1.0")));
        
        ((EditText)findViewById(R.id.tcpAddr)).setText(prefs.getString("tcp.addr", "127.0.0.1"));
        ((EditText)findViewById(R.id.tcpPort)).setText(prefs.getString("tcp.port", "8080"));
        
        mTxCr     = prefs.getBoolean("tx.cr",  false);
        mTxLf     = prefs.getBoolean("tx.lf",  false);
        mTxHex    = prefs.getBoolean("tx.hex", false);
        mTxString = prefs.getString("tx.text", "");
        mRxEn     = prefs.getBoolean("rx.en",  true);
        mRxCrLf   = prefs.getBoolean("rx.crlf",  false);
        mRxHex    = prefs.getBoolean("rx.hex", false);
    }
    private void prefsSave(){
        getSharedPreferences(PREFS_NAME, 0).edit()
        .putInt("channel.type", ((Spinner)findViewById(R.id.channelType)).getSelectedItemPosition())
        .putString("serial.baud", (String)((Spinner)findViewById(R.id.serialBauds)).getSelectedItem())
        .putString("serial.parity", (String)((Spinner)findViewById(R.id.serialParity)).getSelectedItem())
        .putString("serial.stops", (String)((Spinner)findViewById(R.id.serialStops)).getSelectedItem())
        .putString("tcp.addr", ((EditText)findViewById(R.id.tcpAddr)).getText().toString())
        .putString("tcp.port", ((EditText)findViewById(R.id.tcpPort)).getText().toString())
        .putBoolean("tx.cr",  mTxCr)
        .putBoolean("tx.lf",  mTxLf)
        .putBoolean("tx.hex", mTxHex)
        .putString("tx.text", mTextTx.getText().toString())
        .putBoolean("rx.en",  mRxEn)
        .putBoolean("rx.crlf",mRxCrLf)
        .putBoolean("rx.hex", mRxHex)
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
            if(mChannel != null){
                mChannel.close();
                mChannel = null;
                runOnUiThread(new Runnable(){ @Override public void run(){
                    ((Button)findViewById(R.id.bConnect)).setVisibility(View.VISIBLE);
                    ((Button)findViewById(R.id.bDisconnect)).setVisibility(View.GONE);
                }});
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
        
        mTextTx     = ((EditText)findViewById(R.id.txText));
        mTextRx     = ((EditText)findViewById(R.id.rxText));
        mRxLineText = ((TextView)findViewById(R.id.rxLine));

        // channel connect
        ((Button)findViewById(R.id.bConnect)).setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View arg0){
                if(mChannel == null){
                    new Thread(new Runnable(){ public void run(){
                        runOnUiThread(new Runnable(){ @Override public void run(){
                            ((Button)findViewById(R.id.bConnect)).setEnabled(false);
                        }});
                        CommGeneric ch = null;
                        switch(spChannelType.getSelectedItemPosition()){
                        case CH_USB:
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
                            mChannel = ch;
                            runOnUiThread(new Runnable(){ @Override public void run(){
                                // fix screen rotation
                                int rot = mActivity.getWindowManager().getDefaultDisplay().getRotation();
                                mActivity.setRequestedOrientation( (rot == Surface.ROTATION_270) ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE :
                                    (rot == Surface.ROTATION_90) ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE :
                                    (rot == Surface.ROTATION_180) ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT :
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                );
                                ((Button)findViewById(R.id.bConnect)).setVisibility(View.GONE);
                                ((Button)findViewById(R.id.bDisconnect)).setVisibility(View.VISIBLE);
                                mRxClear = true; // clear RX
                            }});
                        }
                        runOnUiThread(new Runnable(){ @Override public void run(){
                            ((Button)findViewById(R.id.bConnect)).setEnabled(true);
                        }});
                    }}){{ start(); }};
                }
            }
        });
        // channel disconnect
        ((Button)findViewById(R.id.bDisconnect)).setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View arg0){
                if(mChannel != null){
                    // unfix screen orientation
                    mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                    channelDisconnect(); // disconnect
                }
            }
        });
        // channel type
        spChannelType = (Spinner)findViewById(R.id.channelType);
        ((Spinner)findViewById(R.id.channelType)).setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id){
                ((LinearLayout)findViewById(R.id.lChUsb)).setVisibility((position == 0) ? View.VISIBLE : View.GONE);
                ((LinearLayout)findViewById(R.id.lChTcp)).setVisibility((position == 1) ? View.VISIBLE : View.GONE);
                ((LinearLayout)findViewById(R.id.lChBt)).setVisibility((position == 2)  ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent){}
        });

        // serial ports
        ((Spinner)findViewById(R.id.serialPorts)).setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>()));
        updateUsb();
        ((Button)findViewById(R.id.bUsbReload)).setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View arg0){ updateUsb(); }
        });
        
        // bluetooth devices
        ((Spinner)findViewById(R.id.btDevs)).setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>()));
        updateBluetooth();
        ((Button)findViewById(R.id.bBtReload)).setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View arg0){ updateBluetooth(); }
        });
        
        // TX/RX options
        ((CheckBox)findViewById(R.id.txCr)).setChecked(mTxCr);
        ((CheckBox)findViewById(R.id.txCr)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override public void onCheckedChanged(CompoundButton buttonView,boolean isChecked){ mTxCr = isChecked; }
        });
        ((CheckBox)findViewById(R.id.txLf)).setChecked(mTxLf);
        ((CheckBox)findViewById(R.id.txLf)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override public void onCheckedChanged(CompoundButton buttonView,boolean isChecked){ mTxLf = isChecked; }
        });
        ((CheckBox)findViewById(R.id.txHex)).setChecked(mTxHex);
        ((CheckBox)findViewById(R.id.txHex)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override public void onCheckedChanged(CompoundButton buttonView,boolean isChecked){ mTxHex = isChecked; }
        });
        ((Button)findViewById(R.id.txClear)).setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View arg0){ mTextTx.setText(""); }
        });
        ((Button)findViewById(R.id.txSend)).setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View arg0){
                if((mChannel != null)&&(mTextTx.length() > 0)){
                    String s = mTextTx.getText().toString();
                    int len = 0;
                    for(int i=0; i<s.length(); i++){
                        int c = (int)s.charAt(i);
                        if((c >= 32)&&(c <= 127)) mTxBuf[len++] = (byte)c;
                        else if((c == 10)||(c == 13)) mTxBuf[len++] = (byte)c;
                    }
                    if(mTxCr) mTxBuf[len++] = 13;
                    if(mTxLf) mTxBuf[len++] = 10;
//StringBuilder sb = new StringBuilder();
//for(int i=0; i<len; i++) sb.append(String.format("%02X", mTxBuf[i])).append(',');
//toastMsg("send: " + sb.toString(), true);
                    if(len > 0) mTxQueue.add(Arrays.copyOf(mTxBuf, len));
toast("send: " + mTxQueue.size(), false);
                }
            }
        });
        mTextTx.setText(mTxString);
 
        ((CheckBox)findViewById(R.id.rxEn)).setChecked(mRxEn);
        ((CheckBox)findViewById(R.id.rxEn)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override public void onCheckedChanged(CompoundButton buttonView,boolean isChecked){ mRxEn = isChecked; }
        });
        ((CheckBox)findViewById(R.id.rxCrLf)).setChecked(mRxCrLf);
        ((CheckBox)findViewById(R.id.rxCrLf)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override public void onCheckedChanged(CompoundButton buttonView,boolean isChecked){ mRxCrLf = isChecked; }
        });
        ((CheckBox)findViewById(R.id.rxHex)).setChecked(mRxHex);
        ((CheckBox)findViewById(R.id.rxHex)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override public void onCheckedChanged(CompoundButton buttonView,boolean isChecked){ mRxHex = isChecked; }
        });
        ((Button)findViewById(R.id.rxClear)).setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View arg0){ mRxClear = true; }
        });
        ((Button)findViewById(R.id.rxSave)).setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View arg0){
                if(mTextRx.length() > 0){
                    if(Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())){
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd_HH.mm.ss");
                        sdf.setTimeZone(TimeZone.getDefault());
                        String name = "log_" + sdf.format(new java.util.Date()) + ".txt";
                        File file = new File( Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), name);
                        try{
                            FileOutputStream fo = new FileOutputStream(file);
                            fo.write(mTextRx.getText().toString().getBytes("US-ASCII"));
                            fo.close();
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
    }
    @Override protected void onPause(){
        prefsSave();
        channelDisconnect();
        super.onPause();
    }
    @Override protected void onDestroy(){
        pollTimer.cancel();
        super.onPause();
    }

    // add new tab
    private void addTab(String tag, String title, int contentID){
        TabHost.TabSpec tabSpec = getTabHost().newTabSpec(tag);
        tabSpec.setIndicator(title, getResources().getDrawable(android.R.drawable.star_on));
        tabSpec.setContent(contentID);
        getTabHost().addTab(tabSpec);
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings){
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
