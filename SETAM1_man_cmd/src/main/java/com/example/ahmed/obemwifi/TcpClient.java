package com.example.ahmed.obemwifi;


import android.os.Handler;
import android.util.Log;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.concurrent.Semaphore;



public class TcpClient {


    private String SERVER_IP; //server IP address
    private int SERVER_PORT;
    private RecvThread rt = null;
    private Socket socket;
    private HashMap<Byte, String> states;
    private Semaphore lock = new Semaphore(1);
    // message to send to the server
    private int MAX_MSG = 2012;
    private byte sidw = (byte) 0, dim = (byte)100, sidr = sidw;
    private byte srv_node_no, cli_node_no;
    // sends message received notifications
    private OnMessageReceived mMessageListener = null;
    // used to send messages
    private DataOutputStream outToServer;
    // used to read messages from the server
    private DataInputStream inFromServer;
    private Handler handler;

    /**
     * Constructor of the class. OnMessagedReceived listens for the messages received from server
     */
    public TcpClient(OnMessageReceived listener, String ip, int port, Handler h, HashMap<Byte, String> map)
    {
        mMessageListener = listener;
        SERVER_IP = ip;
        SERVER_PORT = port;
        handler = h;
        states = map;
    }


    public void receiveMessage() {
        rt = new RecvThread("Receive THread");
        rt.start();
        //if(MainActivity.debug_ema)Log.d("TCP","receiveMessage thread started...");
    }



    public void interruptThread() {
        if(rt.isAlive()){
            rt.val = false;
        }
        //if(MainActivity.debug_ema)Log.d("TCP","receiveMessage thread interrupted...");
    }



    public void getLock() {
        try {
            lock.acquire();
        } catch (InterruptedException e) {
            mMessageListener.setDisConnected("Connection problem");
        }
    }

    public void releaseLock() {
        lock.release();
    }

    /**
     * Sends the message entered by client to the server
     *
     * @param message text entered by client
     */

    public void sendMessage(final byte message) {
        //new SendData().execute(message);
        new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] fins_cmnd2 = new byte[MAX_MSG];
                //TCP header

                fins_cmnd2[0] = (byte) 0x46; /* Header */
                fins_cmnd2[1] = (byte) 0x49;
                fins_cmnd2[2] = (byte) 0x4E;
                fins_cmnd2[3] = (byte) 0x53;
                fins_cmnd2[4] = (byte) 0x00; /* Length */
                fins_cmnd2[5] = (byte) 0x00;
                fins_cmnd2[6] = (byte)0x00;
                fins_cmnd2[7] = (byte) (8 + 18 + 2); /* Length of data from Command onward to the end of FINS frame */
                fins_cmnd2[8] = (byte) 0x00; /* Command */
                fins_cmnd2[9] = (byte) 0x00;
                fins_cmnd2[10] = (byte) 0x00;
                fins_cmnd2[11] = (byte) 0x02;
                fins_cmnd2[12] = (byte) 0x00; /* Error Code */
                fins_cmnd2[13] = (byte)0x00;
                fins_cmnd2[14] = (byte) 0x00;
                fins_cmnd2[15] = (byte) 0x00;

                //OMRON header
                fins_cmnd2[16 + 0] = (byte)0x80; /*ICF*/
                fins_cmnd2[16 + 1] = (byte)0x00; /*RSV*/
                fins_cmnd2[16 + 2] = (byte)0x02; /*GCT*/
                fins_cmnd2[16 + 3] = (byte)0x00; /*DNA*/
                //	fins_cmnd[4]=0xB9; /*DA1*/ /*Ethernet Unit FINS NODE NUMBER*/
                fins_cmnd2[16 + 4] = srv_node_no;
                fins_cmnd2[16 + 5] = (byte)0x00; /*DA2*/
                fins_cmnd2[16 + 6] = (byte)0x00; /*SNA*/
                //	fins_cmnd[7]=0x50; /*SA1*/ /*WS FINS NODE NUMBER*/
                fins_cmnd2[16 + 7] = cli_node_no;
                fins_cmnd2[16 + 8] = (byte)0x00; /*SA2*/


                /******************** FINS command *******************/
                fins_cmnd2[16 + 10] = (byte)0x01; /*MRC*/
                fins_cmnd2[16 + 11] = (byte)0x02; /*SRC*/

                /************* FINS parameters and data **************/
                fins_cmnd2[16 + 12] = (byte)0x82; /*VARIABLE TYPE: DM*/

                fins_cmnd2[16 + 13] = (byte)0x06; // (1636 & 0xff00) >> 8; /*WRITE START ADDRESS*/
                fins_cmnd2[16 + 14] = (byte)0x64; // (1636 & 0x00ff);
                fins_cmnd2[16 + 15] = (byte)0x00;

                fins_cmnd2[16 + 16] = (byte)0x00; // (*write_DM_num & 0xff00) >> 8; /*WORDS TO WRITE*/
                fins_cmnd2[16 + 17] = (byte)0x01; // (*write_DM_num & 0x00ff);

                fins_cmnd2[16 + 18] = (byte)0x00;
                fins_cmnd2[16 + 19] = message;

                try {
                    getLock();
                    if (sidw == dim) // SID RESET
                        sidw = (byte)1;
                    fins_cmnd2[16 + 9] = sidw;/*SID*/
                    sidw++;
                    outToServer.write(fins_cmnd2, 0, 36);
                    outToServer.flush();
                    //if(MainActivity.debug_ema)Log.d("TCP","sendMessage...");
                    inFromServer.read(fins_cmnd2);
                    releaseLock();
                }catch(Exception e){
                    //if(MainActivity.debug_ema)Log.e("TCP", "SendData error..."+ e.getMessage());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            mMessageListener.setDisConnected("Connection problem: Check the signal strength");
                        }
                    });
                }

            }
        }).start();
    }



    public void stopClient() {
        interruptThread();
    }


    public void run() throws Exception{
        Thread thread = new Thread(InitCon, "Init connexion");
        thread.start();
        try {
            thread.join();
        }catch(Exception e){

            //if(MainActivity.debug_ema)Log.e("TCP", "thread client error "+ e.getMessage());
            mMessageListener.setDisConnected(null);
            return;
        }
        receiveMessage();
    }


    //Declare the interface. The method messageReceived(String message) will must be implemented in the MyActivity
    //class at on asynckTask doInBackground

    public interface OnMessageReceived {
        void messageReceived(String[] message);
        void setDisConnected(String message);
    }




    public class RecvThread extends Thread {

        public boolean val = true;
        private String[] tab = new String[5];
        private long posX0 = 0, posY0 = 0, posZ0 = 0;
        private String strStatus0 = " ", strState0 = " ";
        private byte counter = (byte)1;
        private int timer = 0;


        public RecvThread(String str) {
            super(str);
        }


        public void sendUpdate(byte cmd) throws  IOException{
            byte[] fins_cmnd2 = new byte[MAX_MSG];
            //TCP header

            fins_cmnd2[0] = (byte) 0x46; /* Header */
            fins_cmnd2[1] = (byte) 0x49;
            fins_cmnd2[2] = (byte) 0x4E;
            fins_cmnd2[3] = (byte) 0x53;
            fins_cmnd2[4] = (byte) 0x00; /* Length */
            fins_cmnd2[5] = (byte) 0x00;
            fins_cmnd2[6] = (byte)0x00;
            fins_cmnd2[7] = (byte) (8 + 18 + 2); /* Length of data from Command onward to the end of FINS frame */
            fins_cmnd2[8] = (byte) 0x00; /* Command */
            fins_cmnd2[9] = (byte) 0x00;
            fins_cmnd2[10] = (byte) 0x00;
            fins_cmnd2[11] = (byte) 0x02;
            fins_cmnd2[12] = (byte) 0x00; /* Error Code */
            fins_cmnd2[13] = (byte)0x00;
            fins_cmnd2[14] = (byte) 0x00;
            fins_cmnd2[15] = (byte) 0x00;

            //OMRON header
            fins_cmnd2[16 + 0] = (byte)0x80; /*ICF*/
            fins_cmnd2[16 + 1] = (byte)0x00; /*RSV*/
            fins_cmnd2[16 + 2] = (byte)0x02; /*GCT*/
            fins_cmnd2[16 + 3] = (byte)0x00; /*DNA*/
            //	fins_cmnd[4]=0xB9; /*DA1*/ /*Ethernet Unit FINS NODE NUMBER*/
            fins_cmnd2[16 + 4] = srv_node_no;
            fins_cmnd2[16 + 5] = (byte)0x00; /*DA2*/
            fins_cmnd2[16 + 6] = (byte)0x00; /*SNA*/
            //	fins_cmnd[7]=0x50; /*SA1*/ /*WS FINS NODE NUMBER*/
            fins_cmnd2[16 + 7] = cli_node_no;
            fins_cmnd2[16 + 8] = (byte)0x00; /*SA2*/


            /******************** FINS command *******************/
            fins_cmnd2[16 + 10] = (byte)0x01; /*MRC*/
            fins_cmnd2[16 + 11] = (byte)0x02; /*SRC*/

            /************* FINS parameters and data **************/
            fins_cmnd2[16 + 12] = (byte)0x82; /*VARIABLE TYPE: DM*/

            fins_cmnd2[16 + 13] = (byte)0x07; // (2021 & 0xff00) >> 8; /*WRITE START ADDRESS*/
            fins_cmnd2[16 + 14] = (byte)0xE5; // (2021 & 0x00ff);
            fins_cmnd2[16 + 15] = (byte)0x00;

            fins_cmnd2[16 + 16] = (byte)0x00; // (*write_DM_num & 0xff00) >> 8; /*WORDS TO WRITE*/
            fins_cmnd2[16 + 17] = (byte)0x01; // (*write_DM_num & 0x00ff);

            fins_cmnd2[16 + 18] = (byte)0x00;
            fins_cmnd2[16 + 19] = cmd;

            getLock();
            if (sidw == dim) // SID RESET
                sidw = (byte)1;
            fins_cmnd2[16 + 9] = sidw;/*SID*/
            sidw++;
            outToServer.write(fins_cmnd2, 0, 36);
            //Log.d("TCP thread Client", "thread: sent update counter...");
            outToServer.flush();
            inFromServer.read(fins_cmnd2);
            releaseLock();

        }

        public void updateGui(byte[] message){
            long posX, posY, posZ;
            boolean flag = false;
                if(message[31] == (byte) 1)tab[0] = "Manual";
                else {
                    if(message[31] == (byte) 2) tab[0] = "Semiautomatic";
                    else {
                        if (message[31] == (byte) 3) tab[0] = "Automatic";
                        else tab[0] = " ";
                    }
                }

                tab[1] = states.get(message[33]);
                if(tab[1] == null)tab[1] = " ";

                int d0 = message[37] & (byte)0x0F, d1 = (message[37] & (byte)0xF0)>>4, d2 = message[36] & (byte)0x0F, d3 = (message[36] & (byte)0xF0)>>4;
                posX = d0  + (10*d1) + (100*d2) + (1000*d3);
                d0 = message[39] & (byte)0x0F; d1 = (message[39] & (byte)0xF0)>>4; d2 = message[38] & (byte)0x0F; d3 = (message[38] & (byte)0xF0)>>4;
                posX += 10000*( d0  + (10*d1) + (100*d2) + (1000*d3));
                tab[2] = String.valueOf(posX);

                d0 = message[41] & (byte)0x0F; d1 = (message[41] & (byte)0xF0)>>4; d2 = message[40] & (byte)0x0F; d3 = (message[40] & (byte)0xF0)>>4;
                posY = d0  + (10*d1) + (100*d2) + (1000*d3);
                d0 = message[43] & (byte)0x0F; d1 = (message[43] & (byte)0xF0)>>4; d2 = message[42] & (byte)0x0F; d3 = (message[42] & (byte)0xF0)>>4;
                posY += 10000*( d0  + (10*d1) + (100*d2) + (1000*d3));
                tab[3] = String.valueOf(posY);

                d0 = message[45] & (byte)0x0F; d1 = (message[45] & (byte)0xF0)>>4; d2 = message[44] & (byte)0x0F; d3 = (message[44] & (byte)0xF0)>>4;
                posZ = d0  + (10*d1) + (100*d2) + (1000*d3);
                d0 = message[47] & (byte)0x0F; d1 = (message[47] & (byte)0xF0)>>4; d2 = message[46] & (byte)0x0F; d3 = (message[46] & (byte)0xF0)>>4;
                posZ += 10000*( d0  + (10*d1) + (100*d2) + (1000*d3));
                tab[4] = String.valueOf(posZ);

                if(tab[0].compareTo(strStatus0) != 0){
                    strStatus0 = tab[0];
                    flag = true;
                }
                if(tab[1].compareTo(strState0) != 0){
                    strState0 = tab[1];
                    flag = true;
                }
                if(posX != posX0)
                {
                    posX0 = posX;
                    flag = true;
                }

                if(posY != posY0)
                {
                    posY0 = posY;
                    flag = true;
                }
                if(posZ != posZ0)
                {
                    posZ0 = posZ;
                    flag = true;
                }
                if(flag) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            mMessageListener.messageReceived(tab);
                        }
                    });
                    //if(MainActivity.debug_ema)Log.d("TCP thread Client", "thread: sent...");
                }

        }

        public void sendReadMessage() throws Exception{
            byte[] fins_cmnd = new byte[MAX_MSG];
            byte[] fins_resp = new byte[MAX_MSG];

            //TCP header
            fins_cmnd[0] = (byte) 0x46; /* Header */
            fins_cmnd[1] = (byte) 0x49;
            fins_cmnd[2] = (byte) 0x4E;
            fins_cmnd[3] = (byte) 0x53;
            fins_cmnd[4] = (byte) 0x00; /* Length */
            fins_cmnd[5] = (byte) 0x00;
            fins_cmnd[6] = (byte)0x00;
            fins_cmnd[7] = (byte) (8 + 18); /* Length of data from Command onward to the end of FINS frame */
            fins_cmnd[8] = (byte) 0x00; /* Command */
            fins_cmnd[9] = (byte) 0x00;
            fins_cmnd[10] = (byte) 0x00;
            fins_cmnd[11] = (byte) 0x02;
            fins_cmnd[12] = (byte) 0x00; /* Error Code */
            fins_cmnd[13] = (byte)0x00;
            fins_cmnd[14] = (byte) 0x00;
            fins_cmnd[15] = (byte) 0x00;

            //OMRON header
            fins_cmnd[16 + 0] = (byte)0x80; /*ICF*/
            fins_cmnd[16 + 1] = (byte)0x00; /*RSV*/
            fins_cmnd[16 + 2] = (byte)0x02; /*GCT*/
            fins_cmnd[16 + 3] = (byte)0x00; /*DNA*/
            //	fins_cmnd[4]=0xB9; /*DA1*/ /*Ethernet Unit FINS NODE NUMBER*/
            fins_cmnd[16 + 4] = srv_node_no;
            fins_cmnd[16 + 5] = (byte)0x00; /*DA2*/
            fins_cmnd[16 + 6] = (byte)0x00; /*SNA*/
            //	fins_cmnd[7]=0x50; /*SA1*/ /*WS FINS NODE NUMBER*/
            fins_cmnd[16 + 7] = cli_node_no;
            fins_cmnd[16 + 8] = (byte)0x00; /*SA2*/


            /******************** FINS command *******************/
            fins_cmnd[16 + 10] = (byte)0x01; /*MRC*/
            fins_cmnd[16 + 11] = (byte)0x01;//0x02; /*SRC*/

            /************* FINS parameters and data **************/
            fins_cmnd[16 + 12] = (byte)0x82; /*VARIABLE TYPE: DM*/

            fins_cmnd[16 + 13] = (byte)0x09; // (2500 & 0xff00) >> 8; /*READ START ADDRESS*/
            fins_cmnd[16 + 14] = (byte)0xC4; // (2500 & 0x00ff);
            fins_cmnd[16 + 15] = (byte)0x00;//0x00;

            fins_cmnd[16 + 16] = (byte)0x00; // (*read_DM_num & 0xff00) >> 8; /*WORDS TO READ*/
            fins_cmnd[16 + 17] = (byte)0x0C; // (*read_DM_num & 0x00ff);

            fins_cmnd[16 + 18] = (byte)0x00;


            getLock();
            if (sidr == dim)
                sidr = (byte)1;
            fins_cmnd[16 + 9] = sidr;/*SID*/
            sidr++;
            outToServer.write(fins_cmnd, 0, 34);
            outToServer.flush();
            int recvlen = inFromServer.read(fins_resp);
            //if(MainActivity.debug_ema)Log.d("TCP thread Client", "thread: sent read...");
            releaseLock();
            if (recvlen > 0 && mMessageListener != null) {
                updateGui(fins_resp);
                    }
            if(timer > 1000){
                sendUpdate(counter);
                timer=0;
            }else timer+=60;
            counter++;
        }


        @Override
        public void run() {

            while (val) {
                try {
                    sendReadMessage();
                    sleep(60);
                } catch (Exception e) {
                    val = false;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            mMessageListener.setDisConnected("Connection Problem: Check the signal strength");
                        }
                    });
                    //if(MainActivity.debug_ema)Log.e("TCP", "thread: sent disconnect..."+ e.getMessage());

                }

            }
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public Runnable InitCon = new Runnable(){

        @Override
        public void run(){
            try {
                InetAddress serverAddr = InetAddress.getByName(SERVER_IP);

                //if(MainActivity.debug_ema)Log.d("TCP", "C: Connecting...");

                //create a socket to make the connection with the server
                socket = new Socket(serverAddr, SERVER_PORT);
                socket.setSoTimeout(2000);
                //sends the message to the server

                outToServer = new DataOutputStream(socket.getOutputStream());

                //receives the message which the server sends back
                inFromServer = new DataInputStream(socket.getInputStream());
                //FINS header
                byte[] fins_cmnd = new byte[MAX_MSG], fins_resp = new byte[MAX_MSG];

                fins_cmnd[0] = (byte) 0x46; /* Header */
                fins_cmnd[1] = (byte) 0x49;
                fins_cmnd[2] = (byte) 0x4E;
                fins_cmnd[3] = (byte) 0x53;
                fins_cmnd[4] = (byte) 0x00; /* Length from Command onward */
                fins_cmnd[5] = (byte) 0x00;
                fins_cmnd[6] = (byte) 0x00;
                fins_cmnd[7] = (byte) 0x0C;
                fins_cmnd[8] = (byte) 0x00; /* Command */
                fins_cmnd[9] = (byte) 0x00;
                fins_cmnd[10] = (byte) 0x00;
                fins_cmnd[11] = (byte) 0x00;
                fins_cmnd[12] = (byte) 0x00; /* Error Code */
                fins_cmnd[13] = (byte) 0x00;
                fins_cmnd[14] = (byte) 0x00;
                fins_cmnd[15] = (byte) 0x00;
                fins_cmnd[17] = (byte) 0x00; /* Client Node Add */
                fins_cmnd[18] = (byte) 0x00; /* if 0 request for a free node number to server*/
                fins_cmnd[19] = (byte) 0x00;
                fins_cmnd[20] = (byte) 0x00;

                outToServer.write(fins_cmnd, 0, 20);
                outToServer.flush();
                //if(MainActivity.debug_ema)Log.d("TCP", "Message sent...");
                inFromServer.read(fins_resp);
                cli_node_no = fins_resp[19];
                srv_node_no = fins_resp[23];
            }
            catch(Exception e)
            {
                //if(MainActivity.debug_ema)Log.e("TCP", "Error creating socket.." + e.getMessage() );
                mMessageListener.setDisConnected("Can't open the connection");
            }
        }
    };

}

