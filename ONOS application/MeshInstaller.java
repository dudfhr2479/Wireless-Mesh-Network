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

                if(temp.equals("T") || temp.equals("S") || temp.equals("E")){
                    info = line.split(":");
                    type = info[0];
                    data = info[1];

                    switch (type){
                        //Traffic data
                        case "T":
                            CLILog("<" + receivePacket.getAddress().getHostAddress() + "> : " + data + " bytes");
                            addCount(receivePacket.getAddress().getHostAddress());
                            break;
                        //Connected station data
                        case "S":
                            CLILog("<" + receivePacket.getAddress().getHostAddress() + "> : " + data + " STAs");
                            break;
                        //etc
                        case "E":
                            CLILog("<" + receivePacket.getAddress().getHostAddress() + "> : " + data);
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
        try {
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
                String str = warningList.toString();
                byte[] arr = str.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(arr, arr.length, InetAddress.getByName("192.168.0.0"), 8901);
                dSocket.send(sendPacket);
                CLILog("High traffic load : " + new String(arr, 0, arr.length));
            }
        }catch (Exception e){
            CLILog("RouteChange Error" + e.getMessage());
        }
    }
}
