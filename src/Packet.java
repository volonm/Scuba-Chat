import client.Message;
import client.MessageType;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;

public class Packet implements Comparator<Packet> {


    @Override
    public int compare(Packet o1, Packet o2) {
        return Integer.compare(o1.seq, o2.seq);
    }

    public enum Flag {
        SYN, ACK, LSP, FIN
    }

    // Header fields

    // Bytes from 0 to 1
    String src;

    // Bytes from 2 to 3
    String dst;

    // Bytes from 4 to 5
    public int seq;

    // Byte 6
    Flag flag;
    boolean more;

    // Bytes from 7 to 8
    int offset;

    // Bytes from 9-10
    String nextHop;

    // Bytes from 11 to 31 |  Which gives 21 bytes of data in Total.
    String data;

    int[] txpkt;

    public static int HEADER_SIZE = 11;


    public Packet(String src, String dst) {
        this.src = src;
        this.dst = dst;

    }

    public Packet(String src, String dst, int seq, int offset, Flag flag, boolean more, String nextHop, String data) {
        this.src = src;
        this.dst = dst;
        this.seq = seq;
        this.offset = offset;
        this.flag = flag;
        this.more = more;
        this.data = data;
        this.nextHop = nextHop;
    }

    public Integer returnHeaderSize() {
        return HEADER_SIZE;
    }

    /**
     * Converts enum flag into binary representation
     *
     * @param flag that is being converted
     * @return the byte value of the flag
     */
    public int convertFlag(Flag flag) {
        int result = 0;
        switch (flag) {
            case SYN:
                result = 2;
                break;
            case ACK:
                result = 4;
                break;
            case LSP:
                result = 8;
                break;
            case FIN:
                result = 16;
                break;
        }
        return result;
    }


    /**
     * This method converts the received ByteBuffer into a Packet object which we can use
     *
     * @param buffer The packet as buffer that is received
     * @return The packet represented as an object of Packet class
     */
    public static Packet decodePacket(ByteBuffer buffer) {
        int[] message = new int[32];

        for (int i = 0; i < 32; i++) {
            message[i] = buffer.get(i) & 0xff;
        }
        String src = message[0] + "." + message[1];
        String dst = message[2] + "." + message[3];

        // Getting the Seq
        int receivedSequence = (message[4] << 8) + message[5];

        // Getting the Flag
        int byteFlag = message[6];

        boolean more = (byteFlag & 1) == 1;
        if (more) {
            byteFlag--;
        }
        // Getting the flag value
        Flag flag = Flag.SYN;


        switch (byteFlag) {
            case 2:
                break;
            case 4:
                flag = Flag.ACK;
                break;
            case 8:
                flag = Flag.LSP;
                break;
            case 16:
                flag = Flag.FIN;
                break;
        }


        // Getting the Offset
        int receivedOffset = (message[7] << 8) + message[8];

        // Getting the next hop
        String nextHop = message[9] + "." + message[10];

        byte[] filteredByteArray = Arrays.copyOfRange(buffer.array(), 11, 32);

        String s = StandardCharsets.ISO_8859_1.decode(ByteBuffer.wrap(filteredByteArray)).toString();

        // Returning the packet
        return new Packet(src, dst, receivedSequence, receivedOffset, flag, more, nextHop, s);
    }

    public Message composePacket() {
        return composePacket(data, seq, offset, flag, more, nextHop);
    }

    public Message composePacket(int seq, int offset, Flag flag, boolean more, String nextHop) {
        return composePacket(data, seq, offset, flag, more, nextHop);
    }

    public static String sequencesToDataOld(int[] sequences) {
        // Create a byte array of twice the length of the sequences array
        byte[] byteArray = new byte[sequences.length * 2];

        // Convert each integer to two bytes and store them in the byte array
        for (int i = 0; i < sequences.length; i++) {
            byteArray[i*2] = (byte) ((sequences[i]) >> 8);
            byteArray[i*2+1] = (byte) (sequences[i] & 0xFF);
        }

        // Convert the byte array to a string using UTF-8 encoding
        return new String(byteArray, StandardCharsets.UTF_8);
    }

    public static String sequencesToData(int[] seqs) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        for (int seq : seqs) {
            byteArrayOutputStream.write(seq >> 8);
            byteArrayOutputStream.write(seq & 0xFF);
        }
        return new String(byteArrayOutputStream.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    public static int[] dataToSequencesOld(String encodedString) {
        byte[] decodedBytes = encodedString.getBytes(StandardCharsets.ISO_8859_1);
        int[] intArray = new int[10];
        for (int i = 0; i < 10; i++) {
            intArray[i] = ((decodedBytes[i * 2] & 0xFF) << 8) | (decodedBytes[i * 2 + 1] & 0xFF);
        }
        return intArray;
    }

    public static int[] dataToSequences(String str) {
        byte[] bytes = str.getBytes();

        // Create an integer array of size 10
        int[] arr = new int[10];

        // Iterate through the byte array and convert each pair of bytes to an integer
        for (int i = 0; i < arr.length; i++) {
            int highByte = (int) bytes[i * 2] << 8;   // Shift the high byte to the left by 8 bits
            int lowByte = (int) bytes[i * 2 + 1] & 0xff; // Mask the low byte with 0xff to prevent sign extension
            arr[i] = highByte | lowByte;             // Combine the high and low bytes using bitwise OR
        }

        // Return the integer array
        return arr;
    }

    // Takes the given list of 10 integers and returns a 21-byte string


    public static int convertByteArrayToInt(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getInt();
    }


    /* This method checks the order of offset value */
    public boolean checkCompose(List<Packet> packetList) {
        int counter = 0;
        for (Packet p : packetList) {
            if (p.offset != counter) {
                return false;
            }
            counter++;
        }
        return true;
    }

    /* This method extracts the full text message from List of Received packets */
    public String composeMultiplePackets(List<Packet> packetList) {
        StringBuilder text = new StringBuilder();

        for (Packet p : packetList) {
            text.append(p.data.stripTrailing());
        }
        return text.toString();
    }


    public String getSource() {
        return src;
    }

    public String getDestination() {
        return dst;
    }


    /**
     * Composes the packet to be sent
     *
     * @param s      Message
     * @param seq    Sequence number
     * @param offset Offset value
     * @param flag   Flag value
     * @param more   Indicates if there are any more data that should be sent
     * @return The message packet that is supposed to be sent
     */
    public Message composePacket(String s, int seq, int offset, Flag flag, boolean more, String nextHop) {

        // text packet to be sent
        int[] txpkt = new int[32];

        // splitting addresses into parts
        String[] sourceParts = src.split("\\.");
        String[] destinationParts = dst.split("\\.");
        String[] nhParts = nextHop.split("\\.");

        // sequence parts
        Integer[] seqParts = new Integer[2];
        seqParts[0] = seq >> 8;
        seqParts[1] = seq & 255;

        // source address
        txpkt[0] = Integer.parseInt(sourceParts[0]);
        txpkt[1] = Integer.parseInt(sourceParts[1]);

        //destination address
        txpkt[2] = Integer.parseInt(destinationParts[0]);
        txpkt[3] = Integer.parseInt(destinationParts[1]);

        // sequence number
        txpkt[4] = seqParts[0];
        txpkt[5] = seqParts[1];

        // flag
        int flagByte = more ? convertFlag(flag) + 1 : convertFlag(flag);
        txpkt[6] = flagByte;

        // offset
        txpkt[7] = offset >> 8;
        txpkt[8] = offset & 255;

        // next hop
        txpkt[9] = Integer.parseInt(nhParts[0]);
        txpkt[10] = Integer.parseInt(nhParts[1]);


        byte[] header = new byte[11];
        for (int i = 0; i < 11; i++) {
            header[i] = (byte) txpkt[i];
        }


        ByteBuffer headerBuffer = ByteBuffer.wrap(header);
       int length = 0;

        try {
            length = s.getBytes("ISO-8859-1").length;


            String finalText = s + " ".repeat(21 - length);
            ByteBuffer buff = ByteBuffer.wrap(finalText.getBytes(StandardCharsets.ISO_8859_1));
            ByteBuffer toSend = ByteBuffer.allocate(32).put(headerBuffer).put(buff); // copy data without newline / returns
            return new Message(MessageType.DATA, toSend);
        } catch (UnsupportedEncodingException e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    /**
     * Checks the availability of the nodes both at the beginning and also periodically
     */
    public void checkAvailability() {

    }


    @Override
    public String toString() {
        return String.format(
                "\nsrc: %s\n" +
                        "dst: %s\n" +
                        "seq: %d\n" +
                        "offset: %d\n" +
                        "flag: %s\n" +
                        "more: %b\n" +
                        "next: %s\n" +
                        "data: %s\n",
                src, dst, seq, offset, flag.toString(), more, nextHop, data);
    }

    public static void main(String[] args) {

        int[] seqs = new int[]{1, 3, 46, 140, 0, 255, 5000, 0, 0, 0};
//        byte[] byteArr = byte
//        String res = String(byteArr, StandardCharsets.ISO_8859_1);

        ByteBuffer buff = ByteBuffer.wrap(sequencesToData(seqs).getBytes(StandardCharsets.ISO_8859_1));

//        String s = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(filteredByteArray)).toString();

        System.out.println("String we will send: " + sequencesToData(seqs));

        System.out.println("Seq's we will receive: " + Arrays.toString(dataToSequences(sequencesToData(seqs))));
    }


}
