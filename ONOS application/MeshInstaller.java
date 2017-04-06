package org.iris4sdn.mesh;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.*;

@Component(immediate = true)
public class MeshInstaller {
    public static HashMap<String, Integer> overTrafficList = new HashMap<>();
    public static HashMap<String, String> macIPList = new HashMap<>();
    public DatagramSocket dSocket = null;
    public Boolean deactive = false;

    //Timer(check OVS information & delete traffic count)
    private Thread timer = new Thread(()->{
        try{
            while(!Thread.currentThread().isInterrupted()){
                if(!overTrafficList.isEmpty()) {
                    CLILog(overTrafficList.toString());
                    routeChange();
                    delCount();
                }
                Thread.sleep(2000);
            }
        }catch (Exception e){
            if(!deactive){
                CLILog("Timer Error : " + e.getMessage());
            }
        }
    });

    //UDP socket
    private Thread dataGramSocket = new Thread(()->{
        try{
            dSocket = new DatagramSocket(8901);
            dSocket.setBroadcast(true);
            String line, info[], type, data;

            while(!Thread.currentThread().isInterrupted()){
                byte[] buffer = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
                dSocket.receive(receivePacket);

                line = new String(receivePacket.getData(), 0, receivePacket.getLength());
                String temp = line.substring(0, 1);

                if(temp.equals("T") || temp.equals("S") || temp.equals("I") || temp.equals("R") || temp.equals("E")){
                    info = line.split("=");
                    type = info[0];
                    data = info[1];

                    String address = receivePacket.getAddress().getHostAddress();

                    switch (type){
                        //Traffic data
                        case "T":
                            CLILog("<" + address + "> : " + data + " bytes");
                            addCount(address);
                            break;
                        //Connected station data
                        case "S":
                            CLILog("<" + address + "> : " + data + " STAs");
                            break;
                        //Network Information data
                        case "I":
                            CLILog("<" + address + "> : was CONNECTED");
                            addNetInfo(address, data);
                            break;
                        //Request data
                        case "R":
                            macToIP(address, data);
                            break;
                        //etc
                        case "E":
                            CLILog("<" + address + "> : " + data);
                            break;
                    }
                }
            }
        }catch (Exception e){
            if(!deactive){
                e.printStackTrace();
                CLILog("UDP Socket Error : " + e.getMessage());
            }
        }
    });

    //executed automatically when the program start
    @Activate
    protected void activate() {
        CLILog("Started");
        timer.start();
        dataGramSocket.start();

        Scanner input = new Scanner(System.in);
        String keyInput;
        Boolean loop = true;

        while(loop){
            keyInput = input.next();
            switch (keyInput){
                case "q":
                    loop = false;
                    break;
                case "l":
                    CLILog("MACIPlist\n" + macIPList.toString());
                    break;
                default:
                    CLILog("Command ERROR");
            }
        }
    }

    //executed automatically when the program exit
    @Deactivate
    protected void deactivate() {
        deactive = true;

        timer.interrupt();
        dataGramSocket.interrupt();

        if(dSocket.isConnected()) {
            dSocket.disconnect();
        }

        dSocket.close();
        CLILog("Stopped");
    }

    //get current time
    private String getTime(){
        SimpleDateFormat dayTime = new SimpleDateFormat("[hh:mm:ss] ");
        String str = dayTime.format(new Date());
        return str;
    }

    //print CLI console
    private void CLILog(String str){
        System.out.println(getTime() + str);
    }

    //OVS infomation - more traffic
    private void addCount(String address){
        if(overTrafficList.containsKey(address)){
            overTrafficList.put(address, overTrafficList.get(address) + 1);
        }else{
            overTrafficList.put(address, 1);
        }
    }

    //OVS information - timer
    private void delCount(){
        Iterator<String> keySet = overTrafficList.keySet().iterator();
        while(keySet.hasNext()){
            String key = keySet.next();
            int value = overTrafficList.get(key);

            if(value != 0){
                overTrafficList.put(key, value - 1);
            }else{
                overTrafficList.remove(key);
            }
        }
    }

    private void routeChange(){
        Iterator<String> keySet = overTrafficList.keySet().iterator();
        ArrayList<String> warningList = new ArrayList<String>();
        String key = "";
        int value = 0;

        while (keySet.hasNext()) {
            key = keySet.next();
            value = overTrafficList.get(key);

            if (value > 5) {
                warningList.add(key);
            }
        }

        if (!warningList.isEmpty()) {
            sendMsg("W", warningList.toString(), "192.168.0.0");
        }
    }

    private void addNetInfo(String address, String data){
        macIPList.put(data, address);
    }

    private void macToIP(String address, String data){
        String[] list = data.split(",");
        ArrayList<String> sendList = new ArrayList<>();

        for(String mac : list){
            if(macIPList.containsKey(mac.substring(0, mac.length() - 2).toUpperCase())){
                sendList.add(macIPList.get(mac.substring(0, mac.length() - 2).toUpperCase()));
            }
        }

        if (!sendList.isEmpty()) {
            sendMsg("R", sendList.toString(), address);
        }
    }

    private void sendMsg(String type, String msg, String address){
        try {
            String str = type + "=" + msg;
            byte[] arr = str.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(arr, arr.length, InetAddress.getByName(address), 8901);
            dSocket.send(sendPacket);
        }catch (Exception e){
            CLILog("Send " + type + " message Error : " + e.getMessage());
        }
    }
}
