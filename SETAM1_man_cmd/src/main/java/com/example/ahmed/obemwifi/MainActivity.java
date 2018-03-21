package com.example.ahmed.obemwifi;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MotionEventCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;

// CHANGE HISTORY
// mod. 002, 10/01/2018, E. Campra: modifiche per aggiunta pulsante ENABLE

public class MainActivity extends AppCompatActivity implements View.OnTouchListener, TcpClient.OnMessageReceived {

    public Button XPSlow,XPFast,XMSlow,XMFast, YPSlow,YPFast,YMSlow,YMFast, ZPSlow,ZPFast,ZMSlow,ZMFast, open_grip,
            close_grip, centr_up, centr_dw, connect_btn;
    public TextView message, status, state, x, y, z;
    public EditText IPtext;
    //public final  static boolean debug_ema = false;

    public Button OBEMEnableBtn; // mod. 002
    private static boolean OBEMEnableCmd; // mod. 002
    private static int cnt; // mod. 002


    private boolean connected = false;
    private String IPshuttle;
    private TcpClient tcpClient;
    private int SERVER_PORT = 9600;
    private HashMap<Byte, String> states;
    private Handler handler;


    static final int REQUEST_ENABLE_WF = 1;


    public boolean ipMatch(String ip){
        String pat = "(\\d{1,3}\\.){3}(\\d{1,3})";
        if(!ip.matches(pat))return false;
        String[] tab = ip.split("\\.");
        for(String s:tab)if(Integer.parseInt(s) > 255)return false;
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        OBEMEnableBtn = (Button) findViewById(R.id.buttonEnable); // mod. 002
        OBEMEnableCmd = false; // mod. 002
        cnt = 0; // mod. 002

        message = (TextView) findViewById(R.id.textView1);
        connect_btn = (Button) findViewById(R.id.button0);
        IPtext = (EditText) findViewById(R.id.editText);

        XPSlow = (Button) findViewById(R.id.btn2);
        XPFast = (Button) findViewById(R.id.btn1);
        XMSlow = (Button) findViewById(R.id.btn3);
        XMFast = (Button) findViewById(R.id.btn4);

        YPSlow = (Button) findViewById(R.id.btn6);
        YPFast = (Button) findViewById(R.id.btn5);
        YMSlow = (Button) findViewById(R.id.btn7);
        YMFast = (Button) findViewById(R.id.btn8);

        ZPSlow = (Button) findViewById(R.id.btn10);
        ZPFast = (Button) findViewById(R.id.btn9);
        ZMSlow = (Button) findViewById(R.id.btn11);
        ZMFast = (Button) findViewById(R.id.btn12);

        open_grip = (Button) findViewById(R.id.btn15);
        close_grip = (Button) findViewById(R.id.btn16);

        centr_up = (Button) findViewById(R.id.btn13);
        centr_dw = (Button) findViewById(R.id.btn14);
        status = (TextView) findViewById(R.id.etStatus);
        state = (TextView) findViewById(R.id.etState);
        x = (TextView) findViewById(R.id.etX);
        y = (TextView) findViewById(R.id.etY);
        z = (TextView) findViewById(R.id.etZ);
//        status.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
//        state.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
//        x.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
//        y.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
//        z.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);


        XPSlow.setOnTouchListener(this);
        XPFast.setOnTouchListener(this);
        XMSlow.setOnTouchListener(this);
        XMFast.setOnTouchListener(this);

        YPSlow.setOnTouchListener(this);
        YPFast.setOnTouchListener(this);
        YMSlow.setOnTouchListener(this);
        YMFast.setOnTouchListener(this);

        ZPSlow.setOnTouchListener(this);
        ZPFast.setOnTouchListener(this);
        ZMSlow.setOnTouchListener(this);
        ZMFast.setOnTouchListener(this);

        open_grip.setOnTouchListener(this);
        close_grip.setOnTouchListener(this);

        centr_up.setOnTouchListener(this);
        centr_dw.setOnTouchListener(this);

        // mod. 002
        OBEMEnableBtn.setOnTouchListener(
                new Button.OnTouchListener(){
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        int action = event.getActionMasked();
                        String actionString;

                        cnt++;

                        switch (action)
                        {
                            case MotionEvent.ACTION_DOWN:
                                OBEMEnableCmd = true;
                                actionString = "ACTION_DOWN, OBEMCommandEnabled: " + Boolean.toString(OBEMEnableCmd) + " " + Integer.toString(cnt);
                                v.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.button_pressed));
                                break;

                            case MotionEvent.ACTION_UP:
                                OBEMEnableCmd = false;
                                actionString = "ACTION_UP, OBEMCommandEnabled: " + Boolean.toString(OBEMEnableCmd) + " " + Integer.toString(cnt);
                                v.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.button_default));
                                break;

                            default:
                                actionString = "ACTION: " + Boolean.toString(OBEMEnableCmd) + " " + Integer.toString(cnt);
                        }

                        Log.i("ENABLE OnTouchListener", actionString);

                        return true;
                    }
                }
        );

        states = getStates();
        setDisConnected(null);
        handler = new Handler();

    }

    public HashMap<Byte, String> getStates(){
        byte[] DM = {(byte)0x00,(byte)0x11,
                (byte)0x30 ,(byte)0x31 ,(byte)0x32 ,(byte)0x33 ,
                (byte)0x34 ,(byte)0x35 ,(byte)0x36 ,(byte)0x37 ,
                (byte)0x38 ,(byte)0x39 ,(byte)0x40 ,(byte)0x41 ,
                (byte)0x42, (byte)0x43, (byte)0x44, (byte)0x45, (byte)0x46, (byte)0x47,
                (byte)0x48, (byte)0x49,
                (byte)0x50, (byte)0x51, (byte)0x52, (byte)0x53, (byte)0x54, (byte)0x55, (byte)0x56,
                (byte)0x57, (byte)0x58, (byte)0x59,
                (byte)0x60,
                (byte)0x61, (byte)0x62, (byte)0x63};
        String[] sNavetta = {" ","Ready for a new command",
                "X+ Fast",  "X+ Slow", "X- Fast",  "X- Slow",
                "Y+ Fast", "Y+ Slow", "Y- Fast", "Y- Slow",
                "Z- Fast", "Z- Slow", "Z+ Fast", "Z+ Slow",
                "X+", "X-", "Y+", "Y-", "Z-", "Z+",
                "End of spindles unloading operation", "End of spindles loading operation",
                "Centering device down for taking", "Centering device down for deposit", "Centering device down (manual)", "Open grip for deposit", "Open grip in manual", "Close grip for taking", "Close grip in manual",
                "Opening gate1", "Opening gate2", "Opening gate3",
                "Reset",
                "Centering device up end of taking", "Centering device up end of deposit", "Centering device up (manual)"};

        HashMap<Byte, String> map = new HashMap<>();
        try{
            for(int i = 0; i < DM.length; i++)
                map.put(DM[i], sNavetta[i]);
        }catch(Exception e){
            Toast.makeText(getApplicationContext(),"Error: States",
                    Toast.LENGTH_LONG).show();
        }
        return map;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if(id == R.id.action_disconnect){
            setDisConnected("Disconnected");
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void openConnection(){
        handler.removeCallbacksAndMessages(null);

        tcpClient = new TcpClient(this, IPshuttle, SERVER_PORT, handler, states);
        try {
            tcpClient.run();
            activateButtons();
            Toast.makeText(getApplicationContext(), IPshuttle + " connected ",
                    Toast.LENGTH_LONG).show();
        }catch (Exception e){
           // Log.e("TCP", "C: Error", e);
            setDisConnected(null);
        }
    }

    public void ping(){
        if(!ipMatch(IPshuttle)){
            setDisConnected(IPshuttle + " is not a valid ip address");
        }else{
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        connected = InetAddress.getByName(IPshuttle).isReachable(1000);
                    } catch (IOException e) {
                        e.printStackTrace();
                        connected = false;
                    }
                }
            }, "Ping");
            thread.start();
            try {
                thread.join();
            }catch(Exception e){
                //if(debug_ema)Log.e("TCP"," thread join error ");
                setDisConnected(null);
            }
        }
    }

    public void on(View view){
        if(connected)setDisConnected(null);
        IPshuttle = IPtext.getText().toString();
        if(!ipMatch(IPshuttle)){
            Toast.makeText(getApplicationContext(),IPshuttle + " is not a valid ip address",
                    Toast.LENGTH_LONG).show();
        }else{
            ping();
            if(connected) {
                openConnection();
            }
            else {
                Toast.makeText(getApplicationContext(),IPshuttle + " disconnected",
                        Toast.LENGTH_LONG).show();
                if(connected)setDisConnected(null);
                startActivityForResult(new Intent(Settings.ACTION_WIFI_SETTINGS), REQUEST_ENABLE_WF);
            }
        }}


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_ENABLE_WF){
            ping();
            if(connected) {
                openConnection();
            }
            else {
                setDisConnected("Can't connect to " + IPshuttle);
            }
        }
    }



    public void activateButtons(){
        XPSlow.setEnabled(true);
        XPFast.setEnabled(true);
        XMSlow.setEnabled(true);
        XMFast.setEnabled(true);

        YPSlow.setEnabled(true);
        YPFast.setEnabled(true);
        YMSlow.setEnabled(true);
        YMFast.setEnabled(true);

        ZPSlow.setEnabled(true);
        ZPFast.setEnabled(true);
        ZMSlow.setEnabled(true);
        ZMFast.setEnabled(true);

        open_grip.setEnabled(true);
        close_grip.setEnabled(true);

        centr_up.setEnabled(true);
        centr_dw.setEnabled(true);
        connected = true;
        message.setText("PLC connected");
        message.setBackgroundColor(Color.GREEN);
        state.setText(" ");
        status.setText(" ");
        x.setText("0");
        y.setText("0");
        z.setText("0");
        id0 = -1;
        //if(debug_ema)Log.d("TCP", "PLC connected");
    }

    public void resetComands(){
        XPSlow.setPressed(false);
        XPFast.setPressed(false);
        XMSlow.setPressed(false);
        XMFast.setPressed(false);

        YPSlow.setPressed(false);
        YPFast.setPressed(false);
        YMSlow.setPressed(false);
        YMFast.setPressed(false);
        ZPSlow.setPressed(false);
        ZPFast.setPressed(false);
        ZMSlow.setPressed(false);
        ZMFast.setPressed(false);

        open_grip.setPressed(false);
        close_grip.setPressed(false);

        centr_up.setPressed(false);
        centr_dw.setPressed(false);

        XPSlow.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));
        XPFast.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));
        XMSlow.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));
        XMFast.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));

        YPSlow.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));
        YPFast.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));
        YMSlow.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));
        YMFast.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));

        ZPSlow.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));
        ZPFast.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));
        ZMSlow.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));
        ZMFast.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));

        open_grip.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));
        close_grip.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));

        centr_up.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));
        centr_dw.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));
    }


    private int id0 = -1;
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int fingers = event.getPointerCount();
        final int action = MotionEventCompat.getActionMasked(event);
        int  id = v.getId();
        byte cmd = (byte) 0;;
        //pressed two button
        if (fingers > 1) {
            id = -1;
            resetComands();
        }else{
            if (action == MotionEvent.ACTION_DOWN){
                if (!OBEMEnableCmd) // mod. 002
                    return true;

                v.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.button_pressed));

                if(id != id0){

                    switch (id) {
                        case R.id.btn2: {
                            cmd = (byte) 3;
                            break;
                        }
                        case R.id.btn1: {
                            cmd = (byte) 4;
                            break;
                        }
                        case R.id.btn3: {
                            cmd = (byte) 5;
                            break;
                        }
                        case R.id.btn4: {
                            cmd = (byte) 6;
                            break;
                        }

                        case R.id.btn6: {
                            cmd = (byte) 7;
                            break;
                        }
                        case R.id.btn5: {
                            cmd = (byte) 8;
                            break;
                        }
                        case R.id.btn7: {
                            cmd = (byte) 9;
                            break;
                        }
                        case R.id.btn8: {
                            cmd = (byte) 0x10;
                            break;
                        }

                        case R.id.btn10: {
                            cmd = (byte) 0x11;
                            break;
                        }
                        case R.id.btn9: {
                            cmd = (byte) 0x12;
                            break;
                        }
                        case R.id.btn11: {
                            cmd = (byte) 0x13;
                            break;
                        }
                        case R.id.btn12: {
                            cmd = (byte) 0x14;
                            break;
                        }

                        case R.id.btn15: {
                            cmd = (byte) 0x18;
                            break;
                        }
                        case R.id.btn16: {
                            cmd = (byte) 0x17;
                            break;
                        }

                        case R.id.btn13: {
                            cmd = (byte) 0x15;
                            break;
                        }
                        case R.id.btn14: {
                            cmd = (byte) 0x16;
                            break;
                        }

                        default: {
                            id = id0;
                        }
                    }
                }
            }else{
                if(action == MotionEvent.ACTION_MOVE){
                    v.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.button_pressed));
                    id = id0;
                }else{
                    v.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.button_default));
                    id = -1;
                }
            }
        }
        if(id != id0){
            id0 = id;
            tcpClient.sendMessage(cmd);
            //if(debug_ema)Log.d("TCP", "cmd sent: "+ String.valueOf(cmd) + " "+ String.valueOf(action));
        }
        return true;

    }



    @Override
    public void messageReceived(String[] message){

        if(message[0].compareTo(status.getText().toString()) != 0){
            status.setText(message[0]);
            //strStatus0 = message[0];
        }
        if(message[1].compareTo(state.getText().toString()) != 0){
            state.setText(message[1]);
            //strState0 = message[1];
        }
        if(message[2].compareTo(x.getText().toString()) != 0){
            x.setText(message[2]);
            //posX0 = message[2];
        }


        if(message[3].compareTo(y.getText().toString()) != 0){
            y.setText(message[3]);
            //posY0 = message[3];
        }

        if(message[4].compareTo(z.getText().toString()) != 0){
            z.setText(message[4]);
            //posZ0 = message[4];
        }
    }


    @Override
    public void setDisConnected(String e) {
        XPSlow.setEnabled(false);
        XPFast.setEnabled(false);
        XMSlow.setEnabled(false);
        XMFast.setEnabled(false);

        YPSlow.setEnabled(false);
        YPFast.setEnabled(false);
        YMSlow.setEnabled(false);
        YMFast.setEnabled(false);

        ZPSlow.setEnabled(false);
        ZPFast.setEnabled(false);
        ZMSlow.setEnabled(false);
        ZMFast.setEnabled(false);

        open_grip.setEnabled(false);
        close_grip.setEnabled(false);

        centr_up.setEnabled(false);
        centr_dw.setEnabled(false);

        XPSlow.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));
        XPFast.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));
        XMSlow.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));
        XMFast.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));

        YPSlow.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));
        YPFast.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));
        YMSlow.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));
        YMFast.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));

        ZPSlow.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));
        ZPFast.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));
        ZMSlow.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));
        ZMFast.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));

        open_grip.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));
        close_grip.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));

        centr_up.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));
        centr_dw.setBackground( ContextCompat.getDrawable(getApplicationContext(),R.drawable.button_default));


        message.setText("PLC disconnected");
        message.setBackgroundColor(Color.RED);

        status.setText(" ");
        id0 = -1;

        state.setText(" ");

        x.setText("0");

        y.setText("0");

        z.setText("0");
        if(connected && tcpClient != null){
            tcpClient.stopClient();
            handler.removeCallbacksAndMessages(null);
        }
        connected = false;
        if(e != null)
            Toast.makeText(getApplicationContext(),e,
                    Toast.LENGTH_LONG).show();
    }

}
