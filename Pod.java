import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Timer;
import java.util.TimerTask;
/**
 * Pod class simulating pod.
 * @author omkar sarde
 * @author sharwari salunkhe
 * version 1.0
 * */

public class Pod extends Thread {
    private PodAttributes attributes;
    static int podId;

    Pod(int pod_id, String targetIp, String File) {
        podId = pod_id;
        attributes = new PodAttributes(podId, targetIp, File);
    }

    //startThreads

    /**
     * driver function of a pod, creates threads for listening on multicast
     * and for checking updates
     */
    void begin() {
        Thread thread = new Thread(this::listen);
        thread.start();
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() { attributes.sendDefultMessage(); }},0,5000);
        Thread udpThread = new Thread(()->{startServer();});
        udpThread.start();
    }

    /**
     * method to listen on multicast port and update pod self routing table
     */
    void listen() {
        try {
            byte[] buff = new byte[256];
            InetAddress group = InetAddress.getByName(attributes.multicastIp);
            attributes.multicastSocket.joinGroup(group);
            while (true) {
                DatagramPacket datagramPacket = new DatagramPacket(buff, buff.length);
                //receive data and populate table
                attributes.multicastSocket.receive(datagramPacket);
                Table receivedTable = new Table();
                receivedTable.populateTable(datagramPacket);
                int received_id = receivedTable.id;
                if (received_id == podId) {
                    continue;
                }
                String hopIp = datagramPacket.getAddress().getHostAddress();
                attributes.table.addEntry(received_id, hopIp);
                attributes.startTimer(datagramPacket.getAddress().getHostAddress(), received_id);
                attributes.table.updateSelfTable(receivedTable, datagramPacket.getAddress().getHostAddress());
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Listen for incoming packets and relay hops using udp
     */
    void startServer(){
        try {
            attributes.udpSocket = new DatagramSocket(attributes.udpPort);
            byte[] buff = new byte[5056];
            while (true){
                DatagramPacket datagramPacket = new DatagramPacket(buff,buff.length);
                attributes.udpSocket.receive(datagramPacket);
                Message messages = attributes.message.genMsgFromBytes(datagramPacket.getData());
                System.out.println("Received Message");
                System.out.println(messages);
                String destinationAddress = messages.destIp;
                String selfIp = "10.0."+podId+".0";
                if(!selfIp.equals(destinationAddress)){
                    System.out.println("Forwarding Message");
                    attributes.send(messages);
                }else {
                    System.out.println("Accepting Message");
                   attributes.accept(messages);
                }

            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Driver function for the pod instance
     * @param args input in the form
     *            1 10.0.1.0 testFile.txt
     *       or:  2 "none" "none"
     */
    public static void main(String[] args) {
        Integer pod_id = Integer.parseInt(args[0]);
        String destinationIp = args[1];
        String fileName = args[2];
        Pod pod = new Pod(pod_id,destinationIp,fileName);
        pod.begin();
        pod.attributes.beginToSendData();
    }


}
