import java.net.InetAddress;
import java.nio.ByteBuffer;

/**
 * Message class, handles generation of content in byte[] format from packages
 * which are transmitted between pods
 * message are contain transmission details in first 18 bytes, file contents 18 byte onwards
 *  @author omkar sarde
 *  @author sharwari salunkhe
 */

public class Message {
    String sourceIp, destIp;
    int num, len, type;
    byte[] contents;

    Message() {
    }

    Message(String destination, String source, int typeOfMsg, int number, int length, byte[] details) {
        //0-3 bytes
        destIp = destination;
        //4-7 bytes
        sourceIp = source;
        //8 th byte
        type = typeOfMsg;
        //9-12 bytes
        num = number;
        //13-16 bytes
        len = length;
        //17th byte onwards
        contents = details; //17
    }

    /**
     * Create a byte[] from the message containing data regarding the:
     * 1)destination 2) source 3) type of message 4) SequenceNumber 5) Length of contents 6) Contents
     *
     * @return
     */
    byte[] genBytesFromMsg() {
        try {
            byte[] sourceAddress = InetAddress.getByName(sourceIp).getAddress();
            byte[] destinationAddress = InetAddress.getByName(destIp).getAddress();
            byte[] typeOfMsg = new byte[1];
            if (type == 1) {
                // sequence
                typeOfMsg[0] = (byte) 1;
            } else if (type == 2) {
                //acknowledgement
                typeOfMsg[0] = (byte) 2;
            } else if (type == 3) {
                //final acknowledgement
                typeOfMsg[0] = (byte) 3;
            } else {
                //final
                typeOfMsg[0] = (byte) 0;
            }
            byte[] number = ByteBuffer.allocate(4).putInt(num).array();
            byte[] length = ByteBuffer.allocate(4).putInt(len).array();
            byte[] message = contents;
            byte[] msgDetails = new byte[17 + message.length];
            System.arraycopy(destinationAddress, 0, msgDetails, 0, 4);
            System.arraycopy(sourceAddress, 0, msgDetails, 4, 4);
            System.arraycopy(typeOfMsg, 0, msgDetails, 8, 1);
            System.arraycopy(number, 0, msgDetails, 9, 4);
            System.arraycopy(length, 0, msgDetails, 13, 4);
            System.arraycopy(message, 0, msgDetails, 17, message.length);
            return msgDetails;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Generate a message from byte[] contents
     *
     * @param message contents to send through message
     * @return
     */
    Message genMsgFromBytes(byte[] message) {
        String destinationAddress = genipfromByte(message, "destination");
        String sourceAddress = genipfromByte(message, "source");
        byte[] numberArr = new byte[4];
        byte[] lengthArr = new byte[4];
        System.arraycopy(message, 9, numberArr, 0, 4);
        System.arraycopy(message, 13, lengthArr, 0, 4);
        int typeOfMsg = message[8];
        int number = ByteBuffer.wrap(numberArr).getInt();
        int length = ByteBuffer.wrap(lengthArr).getInt();
        byte[] messageContents = new byte[length];
        System.arraycopy(message, 17, messageContents, 0, length);
        Message generatedMessage = new Message(destinationAddress, sourceAddress, typeOfMsg, number, length, messageContents);
        return generatedMessage;
    }

    /**
     * Generate Ip from the byte[] containing all message details, specifically using
     * first 4 bytes for destination ip and second 4 bytes for source ip
     *
     * @param msgDetails
     * @param ipType
     * @return
     */
    String genipfromByte(byte[] msgDetails, String ipType) {
        byte[] address = new byte[4];
        if (ipType.equals("destination")) {
            System.arraycopy(msgDetails, 0, address, 0, 4);
        } else {
            System.arraycopy(msgDetails, 4, address, 0, 4);
        }
        int pt1, pt2, pt3, pt4;
        pt1 = Byte.toUnsignedInt(address[0]);
        pt2 = Byte.toUnsignedInt(address[1]);
        pt3 = Byte.toUnsignedInt(address[2]);
        pt4 = Byte.toUnsignedInt(address[3]);
        return pt1 + "." + pt2 + "." + pt3 + "." + pt4;
    }

    @Override
    public String toString() {
        return "Message{" +
                "destIp='" + destIp + '\'' +
                ",sourceIp='" + sourceIp + '\'' +
                ", num=" + num +
                ", len=" + len +
                ", type=" + type +
                " (1=Sequence 2=Acknowledgement 3=FinalAcknowledgement 0=Final)" +
                '}';
    }
}
