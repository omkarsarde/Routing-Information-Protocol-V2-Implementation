import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.*;

/**
 * Handles all the properties of pod, separating the view and controller from the model
 * implements most of the helper functions required to handle routing tables and packets
 * @author omkar sarde
 * @author sharwari salunkhe
 */
public class PodAttributes {
    DatagramSocket datagramSocket, udpSocket;
    MulticastSocket multicastSocket;
    Message message;
    protected final int multicastPort = 20011;
    protected int p_id, acknowledgementNum, sequenceNum;
    protected long lengthSize;
    protected final int p_port = 32768;
    protected final int udpPort = 6868;
    protected FileOutputStream fileOutput;
    protected DataInputStream dataInput;
    protected HashMap<Integer, Timer> messageTimer;
    protected HashMap<Integer, Message> window;
    protected HashMap<String, Timer> timers;
    protected final String multicastIp = "233.31.31.31";
    String destIpAddress, file, ipAddress, multiAddress;
    protected Table table;

    /**
     * Constructor
     *
     * @param id       pod id
     * @param targetIp destination ip or "none"
     * @param File     file name or "none"
     */
    PodAttributes(int id, String targetIp, String File) {
        acknowledgementNum = 0;
        if (!targetIp.toLowerCase().equals("none")) {
            destIpAddress = targetIp;
            file = File;
        } else {
            destIpAddress = null;
            file = null;
        }
        multiAddress = multicastIp;
        p_id = id;
        sequenceNum = 0;
        lengthSize = 0;
        messageTimer = new HashMap<>();
        timers = new HashMap<>();
        table = new Table(p_id);
        window = new HashMap<>();
        message = new Message();
        try {
            datagramSocket = new DatagramSocket();
            datagramSocket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            ipAddress = datagramSocket.getLocalAddress().getHostAddress();
            datagramSocket.close();
            multicastSocket = new MulticastSocket(multicastPort);
            datagramSocket = new DatagramSocket(p_port);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Reduce size of contents by trimming extra characters when content size
     * is larger than data read from input stream
     *
     * @param buffer contents
     * @return trimmed contents
     */
    byte[] trimContents(byte[] buffer) {
        int size = 0;
        for (int i = 0; i < buffer.length; i++) {
            if (buffer[i] == 0) {
                size = i;
                break;
            }
        }

        byte[] returnArray = new byte[size];
        System.arraycopy(buffer, 0, returnArray, 0, size);
        return returnArray;
    }

    /**
     * Add message to message sequence
     *
     * @param destination Destination IP to transmit message
     */
    void addToSequence(String destination) {
        try {
            int type = 1;
            byte[] buffer = new byte[5000];
            if (dataInput.read(buffer) == -1) {
                type = 0;
            }
            if (lengthSize > 5000) {
                lengthSize -= 5000;
            } else {
                buffer = trimContents(buffer);
            }
            String localIp = "10.0." + p_id + ".0";
            Message messageToSend = new Message(destination, localIp, type, sequenceNum, buffer.length, buffer);
            window.put(sequenceNum, messageToSend);
            sequenceNum++;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Check if message sent reaches in time, if not resend
     *
     * @param number sequence number of message
     */
    void beginTimerofMsg(int number) {
        try {
            Timer timer = new Timer();
            messageTimer.put(number, timer);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Message newMessage = window.get(number);
                    System.out.println("Packet time out, resending");
                    if (newMessage != null) {
                        send(newMessage);
                        beginTimerofMsg(newMessage.num);
                    }
                }
            }, 1000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Acknowledgement of message transmitted in time to remove it from message sequence
     *
     * @param number sequence of message
     */
    void shutTimerofMsg(int number) {
        window.remove(number);
        Timer timer = messageTimer.get(number);
        timer.cancel();
        ;
        messageTimer.remove(number);
    }

    /**
     * Send a message
     *
     * @param message
     */
    void send(Message message) {
        try {
            Table.Entry entry = table.retrieveEntryFromDestinationIp(message.destIp);
            if (entry == null) {
                System.out.println("Couldnt find entry for: " + destIpAddress);
            } else {
                System.out.println("Sending message to: " + entry.hop);
                byte[] contents = message.genBytesFromMsg();
                DatagramSocket datagramSocket = new DatagramSocket();
                DatagramPacket datagramPacket = new DatagramPacket(contents, contents.length,
                        InetAddress.getByName(entry.hop), udpPort);
                datagramSocket.send(datagramPacket);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Receive a message and check for its type and process the message contents based on its type
     *
     * @param message
     */
    void accept(Message message) {
        try {
            int type = message.type;
            boolean expected = true;
            String localIp = "10.0." + p_id + ".0";
            String destination = message.sourceIp;
            if (type == 1) {
                //in sequence
                System.out.println("Sequence Received: " + message.num);
                if (acknowledgementNum != message.num) {
                    expected = false;
                    System.out.println("Recevied Sequence was wrong, resending acknowledgement");
                }
                if (expected) {
                    acknowledgementNum++;
                    byte[] contents = message.genBytesFromMsg();
                    if (fileOutput == null) {
                        fileOutput = new FileOutputStream("output");
                    }
                    fileOutput.write(contents);
                }
                Message acknowledgementMessage = new Message(destination, localIp, 2,
                        acknowledgementNum, 0, new byte[0]);
                window.remove(acknowledgementNum - 1);
                window.put(acknowledgementNum, acknowledgementMessage);
                send(acknowledgementMessage);
            } else if (type == 2) {
                //acknowledgement
                System.out.println("Acknowledgement Received: " + message.num);
                shutTimerofMsg(message.num - 1);
                addToSequence(message.sourceIp);
                Message forward = window.get(message.num);
                send(forward);
                beginTimerofMsg(forward.num);
            } else if (type == 3) {
                //final acknowledgement
                System.out.println("Final Acknowledgement Received: " + message.num);
                shutTimerofMsg(sequenceNum - 1);
                System.out.println("Data Transfer completed");
            } else {
                //final message
                System.out.println("Received Message: " + message.num);
                byte[] contents = message.genBytesFromMsg();
                fileOutput.write(contents);
                fileOutput.close();
                System.out.println("Written Data to file");
                acknowledgementNum++;
                Message finalMessage = new Message(destination, localIp, 3,
                        acknowledgementNum, 0, new byte[0]);
                System.out.println("Sending final acknowledgement: " + acknowledgementNum);
                window.put(acknowledgementNum, finalMessage);
                send(finalMessage);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Check for parameters of arguments from main function of pod class,
     * that is if there is a file to read from and the destination address,
     * if yes proceed accordingly
     */
    void beginToSendData() {
        try {
            if (destIpAddress != null) {
                File transferFile = new File(file);
                lengthSize = transferFile.length();
                dataInput = new DataInputStream(new FileInputStream(transferFile));
                for (int i = 0; i < 1; i++) {
                    addToSequence(destIpAddress);
                }
                for (int i = 0; i < 1; i++) {
                    Message newMessage = window.get(i);
                    send(newMessage);
                    beginTimerofMsg(newMessage.num);
                }
            }

        } catch (Exception e) {

        }
    }

    /**
     * Keep track of all timers of messages for given pod using timers hashmap
     *
     * @param ipAddress
     * @param id
     */
    void startTimer(String ipAddress, int id) {
        Timer timer;
        if (timers.containsKey(ipAddress)) {
            timer = timers.get(ipAddress);
            timer.cancel();
        }
        timer = new Timer();
        timers.put(ipAddress, timer);
        String localAddress = "10.0." + id + ".0";
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println("Time out for: " + localAddress);
                Table.Entry entry = table.retrieveEntryFromip(localAddress);
                entry.cost = 16;
                Table newTable = table.getEntriesForSameIp(ipAddress);
                newTable.setCostToInfinity();
                //display own table
                table.printDefaultTable();
                try {
                    //sendMessage
                    sendDefultMessage();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 10000);
    }

    /**
     * Create and transmit a message on the multicast network
     */
    void sendDefultMessage() {
        try {
            List<Byte> message = new ArrayList<>();
            //command
            message.add((byte) 1);
            //version
            message.add((byte) 2);
            //trailingZero
            message.add((byte) 0);
            //id
            message.add((byte) p_id);
            for (Table.Entry entry : table.podTable) {
                message.add((byte) 0);
                //address family
                message.add((byte) 2);
                //route tag
                for (int i = 0; i < 2; i++) {
                    message.add((byte) 1);
                }
                //IP
                for (byte bytes : InetAddress.getByName(entry.address).getAddress()) {
                    message.add(bytes);
                }
                //subnetmask
                for (int i = 0; i < 3; i++) {
                    message.add((byte) 0);
                }
                message.add(entry.mask);
                //hop
                for (byte bytes : InetAddress.getByName(entry.hop).getAddress()) {
                    message.add(bytes);
                }
                // cost metrics
                for (int i = 0; i < 3; i++) {
                    message.add((byte) 0);
                }
                message.add(entry.cost);
            }
            Byte[] transfer = message.toArray(new Byte[message.size()]);
            //convert Byte to byte
            byte[] messageInbyte = new byte[message.size()];
            for (int i = 0; i < transfer.length; i++) {
                messageInbyte[i] = transfer[i];
            }
            InetAddress group = InetAddress.getByName(multicastIp);
            DatagramPacket datagramPacket = new DatagramPacket(messageInbyte, messageInbyte.length, group, multicastPort);
            datagramSocket.send(datagramPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
