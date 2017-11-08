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



public class MainActivity extends TabActivity {
    TabActivity mActivity = this;
    private static final String PREFS_NAME = "AMuTermPrefs";
    
    private AtomicBoolean mConnected = new AtomicBoolean(false); // connection flag
    private CommChannel   mChannel;  // connected channel
    
    private static final int CHANNEL_USB = 0;
    private static final int CHANNEL_SOCKET = 1;
    private static final int CHANNEL_BLUETOOTH = 2;
    
    // spinners adapters
    private ArrayAdapter<String> mChannelTypeAdapter,mSerialPortsAdapter,mSerialBaudsAdapter,mSerialParityAdapter,mSerialStopsAdapter,mBluetoothDevicesAdapter;
    private String mSerialPort,mSerialBaud,mSerialParity,mSerialStops; // USB serial port parameters
    private int    mChannelType;     // communication channel type: USB/Socket/Bluetooth
    private String mSocketAddress;   // socket IP address
    private int    mSocketPort;      // socket IP port
    private String mBluetoothDevice; // name of selected Bluetooth device
    private EditText mTextTx, mTextRx;
    private boolean mTxCr,mTxLf,mTxHex,mRxCr,mRxLf,mRxHex;
    
    private Handler mUiHandler;
    private String  mToastMsg;
    private boolean mToastLong;
    private void toastMsg(String msg, boolean isLong){ mToastMsg = msg; mToastLong = isLong;
        mUiHandler.post(new Runnable(){ @Override public void run(){
            (Toast.makeText(getApplicationContext(), mToastMsg, mToastLong ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT)).show(); }
        });
    }
    
    // socket poll timer/task
    private String mStatusString, mRxString, mTxString;
    private final AtomicBoolean mRxClear = new AtomicBoolean(false);
    private final AtomicInteger mRxLine = new AtomicInteger(0);
    private TextView mRxLineText;
    private final ConcurrentLinkedQueue<String> mRxQueue = new ConcurrentLinkedQueue<String>();
    private final ConcurrentLinkedQueue<byte[]> mTxQueue = new ConcurrentLinkedQueue<byte[]>();
    private final byte[] mTxBuf = new byte[8192];
    private final Timer pollTimer = new Timer();
    private final TimerTask pollTask = new TimerTask(){
        private byte[] rxBuf = new byte[8192];
        private StringBuilder s = new StringBuilder();
        @Override public void run(){
            if(mConnected.get()){
                int len = mChannel.read(rxBuf);
                if(len > 0){
                    s.setLength(0);
                    for(int i=0; i<len; i++){
                        if(rxBuf[i] == '\n'){
                            if(mRxLf) s.append("\\n");
                            s.append('\n');
                            mRxLine.incrementAndGet();
                        }else if(rxBuf[i] == '\r'){
                            if(mRxCr) s.append("\\r");
                        }else if((rxBuf[i] < ' ')||(rxBuf[i] > 127)){
                            if(mRxHex)s.append("\\x").append(String.format("%02X", rxBuf[i]));
                        }else{
                            s.append((char)rxBuf[i]);
                        }
                    }
                    mStatusString = "" + len;
                    mRxString = s.toString();
                    mRxQueue.add(mRxString);
                }
            }
            // update RX text
            if(!mRxQueue.isEmpty() || mRxClear.get()){
                mUiHandler.post(new Runnable(){ @Override public void run(){
                    if(mRxClear.get()){
                        mTextRx.setText("");
                        mRxClear.set(false);
                        mRxLine.set(0);
                    }
                    if(!mRxQueue.isEmpty()){
                        StringBuilder s = new StringBuilder();
                        while(!mRxQueue.isEmpty()) s.append(mRxQueue.poll());
                        mTextRx.append(s.toString());
                    }
                    mRxLineText.setText("" + mRxLine.get());
                }});
            }
            // send bytes from TX queue
            if(!mTxQueue.isEmpty()){
                byte[] data = mTxQueue.poll();
                if(mConnected.get()){
StringBuilder sb = new StringBuilder();
for(int i=0; i<data.length; i++) sb.append(String.format("%02X", data[i])).append(',');
//toastMsg("send: " + sb.toString(), true);
//                    mChannel.write(data);
toastMsg("send: " + mChannel.stateString, true);
                }
            }
        }
    };

    
    private void prefsLoad(){
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, 0);
        mChannelType = prefs.getInt("channelType", 0);
        mSerialBaud = prefs.getString("baud", "9600");
        mSerialParity = prefs.getString("parity", "none");
        mSerialStops = prefs.getString("stops", "1.0");
        mSocketAddress = prefs.getString("socketAddr", "127.0.0.1");
        mSocketPort = prefs.getInt("socketPort", 8080);
        mTxCr = prefs.getBoolean("txCr", false);
        mTxLf = prefs.getBoolean("txLf", false);
        mTxHex = prefs.getBoolean("txHex", false);
        mRxCr = prefs.getBoolean("rxCr", false);
        mRxLf = prefs.getBoolean("rxLf", false);
        mRxHex = prefs.getBoolean("rxHex", false);
        mTxString = prefs.getString("txText", "");
    }
    private void prefsSave(){
        SharedPreferences.Editor ed = getSharedPreferences(PREFS_NAME, 0).edit();
        ed.putInt("channelType", mChannelType);
        ed.putString("baud", mSerialBaud);
        ed.putString("parity", mSerialParity);
        ed.putString("stops", mSerialStops);
        ed.putString("socketAddr", mSocketAddress);
        ed.putInt("socketPort", mSocketPort);
        ed.putBoolean("txCr", mTxCr);
        ed.putBoolean("txLf", mTxLf);
        ed.putBoolean("txHex", mTxHex);
        ed.putBoolean("rxCr", mRxCr);
        ed.putBoolean("rxLf", mRxLf);
        ed.putBoolean("rxHex", mRxHex);
        ed.putString("txText", mTextTx.getText().toString());
        ed.commit();
    }

    // update spinner of USB devices
    private void updateUsb(){
        mSerialPortsAdapter.clear();
        mSerialPortsAdapter.addAll(CommSerial.getPorts(this));
    }
    // update spinner of Bluetooth devices
    private void updateBluetooth(){
        mBluetoothDevicesAdapter.clear();
        mBluetoothDevicesAdapter.addAll(CommBluetooth.pairedNames());
    }

    // disconnect connected channel
    private void channelDisconnect(){
        if(mConnected.get()){ Thread closeThread = new Thread(new Runnable(){ public void run(){
            mChannel.close();
            mConnected.set(false);
            mUiHandler.post(new Runnable(){ @Override public void run(){
                ((Button)findViewById(R.id.buttonConnect)).setVisibility(View.VISIBLE);
                ((Button)findViewById(R.id.buttonDisconnect)).setVisibility(View.GONE);
            }});
        }});
        closeThread.start();
    }}
    
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mUiHandler = new Handler(Looper.getMainLooper());
        prefsLoad();

        // tabs
        this.setNewTab("tab1", R.string.title_tab1, android.R.drawable.star_on, R.id.tab1);
        this.setNewTab("tab2", R.string.title_tab2, android.R.drawable.star_on, R.id.tab2);
        
        mTextTx = ((EditText)findViewById(R.id.textTx));
        mTextRx = ((EditText)findViewById(R.id.textRx));
        mRxLineText = ((TextView)findViewById(R.id.textRxLine));

        // channel connect
        ((Button)findViewById(R.id.buttonConnect)).setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View arg0){
                if(!mConnected.get()){
                    Thread openThread = new Thread(new Runnable(){ public void run(){
                        if(mChannelType == CHANNEL_USB){
                            mChannel = new CommSerial();
                            mChannel.open(mSerialPort);
toastMsg("Connecting to Serial: " + mSerialPort + " : " + mChannel.state + ((mChannel.stateString.length() > 0) ? (" : " + mChannel.stateString) : "") , false);
                            if(mChannel.isOpen.get()){
                                mChannel.configSerial(mSerialBaud, mSerialParity, mSerialStops);
                                mConnected.set(true);
                            }
                        } else if(mChannelType == CHANNEL_SOCKET){
                            if(mSocketAddress != null){
toastMsg("Connecting to Socket: " + mSocketAddress + ":" + mSocketPort, false);
                                mChannel = new CommSocket();
                                mChannel.open(mSocketAddress + ":" + mSocketPort);
//toastMsg("Connecting to Socket: " + mChannel.state + " : " + mChannel.stateString, true);
                                if(mChannel.isOpen.get()) mConnected.set(true);
                            }
                        } else if(mChannelType == CHANNEL_BLUETOOTH){
toastMsg("Connecting to Bluetooth: " + mBluetoothDevice, false);
                            mChannel = new CommBluetooth();
                            mChannel.open(mBluetoothDevice);
                            if(mChannel.isOpen.get()) mConnected.set(true);
                        }
                        if(mConnected.get()){
                            mUiHandler.post(new Runnable(){ @Override public void run(){
                                // fix screen rotation
                                int rot = mActivity.getWindowManager().getDefaultDisplay().getRotation();
                                mActivity.setRequestedOrientation( (rot == Surface.ROTATION_270) ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE :
                                    (rot == Surface.ROTATION_90) ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE :
                                    (rot == Surface.ROTATION_180) ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT :
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                );
                                ((Button)findViewById(R.id.buttonConnect)).setVisibility(View.GONE);
                                ((Button)findViewById(R.id.buttonDisconnect)).setVisibility(View.VISIBLE);
                                mRxClear.set(true); // clear RX
                            }});
                        }
                    }});
                    openThread.start();
                }
            }
        });
        // channel disconnect
        ((Button)findViewById(R.id.buttonDisconnect)).setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View arg0){
                if(mConnected.get()){
                    // unfix screen orientation
                    mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                    channelDisconnect(); // disconnect
                }
            }
        });
        // channel type
        mChannelTypeAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,
            ((BluetoothAdapter.getDefaultAdapter() == null) ? new String[]{"usb","socket"} : new String[]{"usb","socket","bluetooth"})
        );
        ((Spinner)findViewById(R.id.spinnerChannelType)).setAdapter(mChannelTypeAdapter);
        ((Spinner)findViewById(R.id.spinnerChannelType)).setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id){
                mChannelType = position;
                ((LinearLayout)findViewById(R.id.layoutChannelUsb)).setVisibility((position == 0) ? View.VISIBLE : View.GONE);
                ((LinearLayout)findViewById(R.id.layoutChannelSocket)).setVisibility((position == 1) ? View.VISIBLE : View.GONE);
                ((LinearLayout)findViewById(R.id.layoutChannelBluetooth)).setVisibility((position == 2) ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent){}
        });
        ((Spinner)findViewById(R.id.spinnerChannelType)).setSelection(mChannelType);
        ((LinearLayout)findViewById(R.id.layoutChannelUsb)).setVisibility((mChannelType == 0) ? View.VISIBLE : View.GONE);
        ((LinearLayout)findViewById(R.id.layoutChannelSocket)).setVisibility((mChannelType == 1) ? View.VISIBLE : View.GONE);
        ((LinearLayout)findViewById(R.id.layoutChannelBluetooth)).setVisibility((mChannelType == 2) ? View.VISIBLE : View.GONE);

        // serial ports
        mSerialPortsAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        ((Spinner)findViewById(R.id.spinnerSerialPorts)).setAdapter(mSerialPortsAdapter);
        ((Spinner)findViewById(R.id.spinnerSerialPorts)).setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id){
                mSerialPort = mSerialPortsAdapter.getItem(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent){ mSerialPort = null; }
        });

        // serial bauds
        mSerialBaudsAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,
            new String[] {"1200","2400","4800","9600","14400","19200","38400","57600","115200"});
        ((Spinner)findViewById(R.id.spinnerSerialBauds)).setAdapter(mSerialBaudsAdapter);
        ((Spinner)findViewById(R.id.spinnerSerialBauds)).setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id){
                mSerialBaud = mSerialBaudsAdapter.getItem(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent){}
        });
        ((Spinner)findViewById(R.id.spinnerSerialBauds)).setPrompt("Baud rate");
        ((Spinner)findViewById(R.id.spinnerSerialBauds)).setSelection(mSerialBaudsAdapter.getPosition(mSerialBaud));

        // serial parity
        mSerialParityAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,
            new String[] {"none","odd","even","mark","space"});
        ((Spinner)findViewById(R.id.spinnerSerialParity)).setAdapter(mSerialParityAdapter);
        ((Spinner)findViewById(R.id.spinnerSerialParity)).setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id){
                mSerialParity = mSerialParityAdapter.getItem(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent){}
        });
        ((Spinner)findViewById(R.id.spinnerSerialParity)).setSelection(mSerialParityAdapter.getPosition(mSerialParity));

        // serial stops
        mSerialStopsAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new String[] {"1.0","1.5","2.0"});
        ((Spinner)findViewById(R.id.spinnerSerialStops)).setAdapter(mSerialStopsAdapter);
        ((Spinner)findViewById(R.id.spinnerSerialStops)).setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id){
                mSerialStops = mSerialStopsAdapter.getItem(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent){}
        });
        ((Spinner)findViewById(R.id.spinnerSerialStops)).setSelection(mSerialStopsAdapter.getPosition(mSerialStops));

        // serial ports list update
        updateUsb();
        ((Button)findViewById(R.id.buttonUsbReload)).setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View arg0){ updateUsb(); }
        });
        
        // socket address
        ((EditText)findViewById(R.id.textSocketAddr)).setText(mSocketAddress);
        ((EditText)findViewById(R.id.textSocketAddr)).addTextChangedListener(new TextWatcher(){
            public void afterTextChanged(Editable s){ mSocketAddress = s.toString(); }
            public void beforeTextChanged(CharSequence s, int start, int count, int after){}
            public void onTextChanged(CharSequence s, int start, int before, int count){}
        });
        // socket port
        ((EditText)findViewById(R.id.textSocketPort)).setText("" + mSocketPort);
        ((EditText)findViewById(R.id.textSocketPort)).addTextChangedListener(new TextWatcher(){
            public void afterTextChanged(Editable s){ try { mSocketPort = java.lang.Integer.parseInt(s.toString()); }catch(Exception e){} }
            public void beforeTextChanged(CharSequence s, int start, int count, int after){}
            public void onTextChanged(CharSequence s, int start, int before, int count){}
        });
        
        // bluetooth devices
        mBluetoothDevicesAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        ((Spinner)findViewById(R.id.spinnerBluetoothPorts)).setAdapter(mBluetoothDevicesAdapter);
        ((Spinner)findViewById(R.id.spinnerBluetoothPorts)).setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id){
                mBluetoothDevice = mBluetoothDevicesAdapter.getItem(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent){ mBluetoothDevice = null; }
        });
        updateBluetooth();
        ((Button)findViewById(R.id.buttonBluetoothReload)).setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View arg0){ updateBluetooth(); }
        });
        
        // TX/RX options
        ((CheckBox)findViewById(R.id.checkTxCr)).setChecked(mTxCr);
        ((CheckBox)findViewById(R.id.checkTxCr)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override public void onCheckedChanged(CompoundButton buttonView,boolean isChecked){ mTxCr = isChecked; }
        });
        ((CheckBox)findViewById(R.id.checkTxLf)).setChecked(mTxLf);
        ((CheckBox)findViewById(R.id.checkTxLf)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override public void onCheckedChanged(CompoundButton buttonView,boolean isChecked){ mTxLf = isChecked; }
        });
        ((CheckBox)findViewById(R.id.checkTxHex)).setChecked(mTxHex);
        ((CheckBox)findViewById(R.id.checkTxHex)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override public void onCheckedChanged(CompoundButton buttonView,boolean isChecked){ mTxHex = isChecked; }
        });
        ((Button)findViewById(R.id.buttonTxClear)).setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View arg0){ mTextTx.setText(""); }
        });
        ((Button)findViewById(R.id.buttonTxSend)).setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View arg0){
                if(mConnected.get() && (mTextTx.length() > 0)){
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
toastMsg("send: " + mTxQueue.size(), false);
                }
            }
        });
        mTextTx.setText(mTxString);
        ((CheckBox)findViewById(R.id.checkRxCr)).setChecked(mRxCr);
        ((CheckBox)findViewById(R.id.checkRxCr)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override public void onCheckedChanged(CompoundButton buttonView,boolean isChecked){ mRxCr = isChecked; }
        });
        ((CheckBox)findViewById(R.id.checkRxLf)).setChecked(mRxLf);
        ((CheckBox)findViewById(R.id.checkRxLf)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override public void onCheckedChanged(CompoundButton buttonView,boolean isChecked){ mRxLf = isChecked; }
        });
        ((CheckBox)findViewById(R.id.checkRxHex)).setChecked(mRxHex);
        ((CheckBox)findViewById(R.id.checkRxHex)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override public void onCheckedChanged(CompoundButton buttonView,boolean isChecked){ mRxHex = isChecked; }
        });
        ((Button)findViewById(R.id.buttonRxClear)).setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View arg0){ mRxClear.set(true); }
        });
        ((Button)findViewById(R.id.buttonRxSave)).setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View arg0){
                if(mTextRx.length() > 0){
                    if(Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())){
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd_HH.mm.ss");
                        sdf.setTimeZone(TimeZone.getDefault());
                        String name = "log_" + sdf.format(new java.util.Date()) + ".txt";
                        File file = new File( Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), name);
                        try{
                            FileOutputStream fo = new FileOutputStream(file);
                            fo.write(mTextRx.getText().toString().getBytes("UTF-8"));
                            fo.close();
toastMsg("Saved to Downloads/" + name, true);
                        }catch(Exception e){
toastMsg("Failed save to Downloads/" + name + "\n" + e.toString(), true);
                        }
                    }else{
toastMsg("External media not available", true); 
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

    // add new tab
    private void setNewTab(String tag, int title, int icon, int contentID){
        TabHost.TabSpec tabSpec = getTabHost().newTabSpec(tag);
        String titleString = getString(title);
        tabSpec.setIndicator(titleString, getResources().getDrawable(android.R.drawable.star_on));
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
