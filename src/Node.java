//
//import client.Message;
//
//import java.nio.charset.StandardCharsets;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.List;
//import java.util.concurrent.Executors;
//import java.util.concurrent.ScheduledExecutorService;
//import java.util.concurrent.TimeUnit;
//import java.util.function.Function;
//
//public class Node {
//    private String address;
//    private int retryCount;
//    private long timeout;
//    private ScheduledExecutorService scheduler;
//    String src_address;
//    int timestamp;
//    private MyProtocol myProtocol;
//
//
//    public Node(String address, Packet packet, int retryCount, long timeout, MyProtocol myProtocol) {
//        this.address = address;
//        this.retryCount = retryCount;
//        this.timeout = timeout;
//        this.scheduler = Executors.newScheduledThreadPool(1);
//        this.myProtocol = myProtocol;
//    }

//    public void RetransmitPacket() {
//        sendPacketWithRetransmission(packet, 0);
//    }
//
//    private void sendPacketWithRetransmission(Message , int currentRetry) {
//        if (currentRetry >= retryCount) {
//            System.out.println("Max retries reached. Giving up on retransmission.");
//            return;
//        }
//
//        // Simulate sending the packet here
//        System.out.println("Sending packet: " + packet.toString());
//
//        // Schedule a task to check for an ACK after a timeout
//        scheduler.schedule(() -> {
//            // Simulate receiving an ACK here (in real-life scenario, you would receive an ACK from another node)
//            Packet receivedPacket = myProtocol.sendingQueue.put();
//
//            if (receivedPacket == null || receivedPacket.ack == packet.seq) {
//                System.out.println("ACK not received or has the same ACK number. Retransmitting...");
//                sendPacketWithRetransmission(packet, currentRetry + 1);
//            } else {
//                System.out.println("ACK received: " + receivedPacket.ack);
//            }
//        }, timeout, TimeUnit.MILLISECONDS);
//    }

//
//    public static List<String> splitIntoStringChunks(String s, int chunkSize) {
//        chunkSize = 19;
//        List<String> chunks = new ArrayList<>();
//
//        for (int i = 0; i < s.length(); i += chunkSize) {
//            int end = Math.min(i + chunkSize, s.length());
//            String chunk = s.substring(i, end);
//            chunks.add(chunk);
//        }
//        return chunks;
//    }
//
//
//}





