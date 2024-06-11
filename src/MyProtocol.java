import client.*;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This is just some example code to show you how to interact
 * with the server using the provided client and two queues.
 * Feel free to modify this code in any way you like!
 */

public class MyProtocol {


    public static int TIMEOUT = 10_000;

    private String source = "111.111";
    private String destination = "0.0";
    private int seq = 1;
    private int ack = 0;
    private int offset = 0;
    private Packet.Flag flag = Packet.Flag.SYN;
    private Boolean more = false;
    private String nextHop = "2.2";
    // The host to connect to. Set this to localhost when using the audio interface tool.
    private static String SERVER_IP = "netsys.ewi.utwente.nl"; //"127.0.0.1";
    // The port to connect to. 8954 for the simulation server.
    private static int SERVER_PORT = 8954;
    public static int W_SIZE = 9;
    // The frequency to use.
    private static int frequency = 3650;
    // View the simulator at https://netsys.ewi.utwente.nl/integrationproject/
    private Scanner scanner = new Scanner(System.in);

    // Checking if a node is able to send
    private final Object lock = new Object();
    private volatile boolean isBusy = false;

    private BlockingQueue<Message> receivedQueue;
    private BlockingQueue<Message> sendingQueue;


    public MyProtocol(String server_ip, int server_port, int frequency) {
        receivedQueue = new LinkedBlockingQueue<Message>();
        sendingQueue = new LinkedBlockingQueue<Message>();


        new Client(SERVER_IP, SERVER_PORT, frequency, receivedQueue, sendingQueue); // Give the client the Queues to use

        new receiveThread(receivedQueue).start(); // Start thread to handle received messages!

        // handle sending from stdin from this thread.
        try {
            while (true) {
                // Resume only if there is data to send
                synchronized (packetsToSend) {
                    while (packetsToSend.isEmpty()) {
                        packetsToSend.wait();
                    }
                }

                // Resume if the medium is free
                synchronized (lock) {
                    while (isBusy) {
                        lock.wait();
                    }

                }

                while (!packetsToSend.isEmpty()) {

                    int nextBurstSize;
                    int retransmissionCount = 0;

                    while (!packetsToSend.isEmpty() || !unackedPackets.isEmpty()) {

                        synchronized (packetsToSend) {
                            if (packetsToSend.size() > 0) {
                                var headPacket = packetsToSend.peek();
                                if (headPacket.flag.equals(Packet.Flag.ACK)) {
//                                    Thread.sleep((long)(Math.random() * TIMEOUT));
                                    sendingQueue.put(packetsToSend.poll().composePacket());
                                    packetsToSend.notifyAll();
                                    break;
                                }
                            }
                        }

                        synchronized (unackedPackets) {


                            // Determine the burst size
                            nextBurstSize = Math.min(SWS - unackedPackets.size(), packetsToSend.size());

                            // Append the next packets in packet queue to unacked packets
                            while (nextBurstSize > 0) {
                                var packetToSend = packetsToSend.poll();
                                unackedPackets.add(packetToSend);
                                nextBurstSize--;
                            }

                            // Send the burst
                            for (Packet unackedPacket : unackedPackets) {
//                                Thread.sleep((long)(Math.random() * TIMEOUT));
                                sendingQueue.put(unackedPacket.composePacket());
                            }

                            // Wait for a response or a timeout
                            try {
                                unackedPackets.wait(TIMEOUT * unackedPackets.size());
                            } catch (InterruptedException e) {
                                // Interrupted, stop waiting and exit the loop
                                break;
                            }
                        }


                        // Check if we timed out
//                        if (unackedPackets.size() < nextBurstSize) {
//                            retransmissionCount = 0;
//                        } else {
//                            retransmissionCount++;
//
//                            if (retransmissionCount >= 3) {
//                                packetsToSend.clear();
//                                unackedPackets.clear();
//                                // Reached maximum number of retransmissions, break out of the loop
//                                break;
//                            }
//                        }


                    }


                }


            }

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            System.out.println("Done");
        }
    }

    public static void main(String args[]) {

        if (args.length > 0) {
            frequency = Integer.parseInt(args[0]);
        }
        new MyProtocol(SERVER_IP, SERVER_PORT, frequency);
    }


    /*
     * This thread will push the user's input to the sending thread.
     * This is done in order to circumvent waiting for user's input each time.
     */
    private class TuiThread extends Thread {

        @Override
        public void run() {
            while (true) {
                System.out.print("Enter text:\n");
                String input = scanner.nextLine();
                synchronized (packetsToSend) {
                    var packets = composeMultiplePackets(splitIntoChunks(input), flag, nextHop);
                    for (var packet : packets) {
                        try {
                            packetsToSend.put(packet);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    packetsToSend.notifyAll();

                }
            }
        }


    }

    private class receiveThread extends Thread {
        private BlockingQueue<Message> receivedQueue;

        public receiveThread(BlockingQueue<Message> receivedQueue) {
            super();
            this.receivedQueue = receivedQueue;
        }

        public void printByteBuffer(ByteBuffer bytes, int bytesLength) {
            for (int i = 0; i < bytesLength; i++) {
                System.out.print(Byte.toString(bytes.get(i)) + " ");
            }
            System.out.println();
        }


        public void run() {




            while (true) {
                try {
                    Message m = receivedQueue.take();
                    if (m.getType() == MessageType.BUSY) {
                        synchronized (lock) {
                            isBusy = true;
                            System.out.println("BUSY");
                        }
                    } else if (m.getType() == MessageType.FREE) {
                        synchronized (lock) {
                            isBusy = false;
                            lock.notifyAll();
                            System.out.println("FREE");
                        }
                    } else if (m.getType() == MessageType.DATA) {
                        System.out.print("DATA: ");
                        // Prints actual data

//                        byte[] bytes = new byte[m.getData().remaining()];
//                        String s = StandardCharsets.UTF_8.decode(m.getData()).toString();
//                        System.out.println(s);

                        Packet packet = Packet.decodePacket(m.getData());

                        if (packet.flag.equals(Packet.Flag.ACK)) {
                            System.out.println("ACK DATA: " + Arrays.toString(Packet.dataToSequences(packet.data)));

                            // If packet is an ack
                            // Extract sequence numbers from the packet
                            int[] ackedSequences = Packet.dataToSequences(packet.data);


                            // Remove all unacked packets containing extracted sequence numbers
                            for (int ackedSeq : ackedSequences) {
                                unackedPackets.removeIf(p -> p.seq == ackedSeq);
                            }

                            // Call notifyAll so that the next burst is sent
                            synchronized (unackedPackets) {
                                unackedPackets.notifyAll();
                            }

                        } else if (packet.flag.equals(Packet.Flag.SYN)) {


                            if (task != null) {
                                task.cancel();
                            }
                            // Add packet to temporary list
                            synchronized (temporaryPackets) {
                                temporaryPackets.add(packet);
                                temporaryPackets.notifyAll();
                            }
                            sequenceNumbers.add(packet.seq);

                            // Check if received packet is a retransmission
                            // (in other words, if it's seq already exists in final list)
                            boolean shouldAdd = true;
                            for (var finalPacket : allPacketsReceived) {
                                if (packet.seq == finalPacket.seq) {
                                    shouldAdd = false;
                                    break;
                                }
                            }
                            // If packet isn't retransmission, add it to final list
                            if (shouldAdd) {
                                allPacketsReceived.add(packet);
                            }

                            int counter = 0;
                            // This is last packet



                            if (!packet.more || canCompose) {
                                canCompose = true;
                                if (packet.checkCompose(allPacketsReceived)) {
                                    String receivedText = packet.composeMultiplePackets(allPacketsReceived);
                                    sendBackAck(packet);

                                    System.out.println("Message: " + receivedText);
                                    canCompose = false;
                                    allPacketsReceived.clear();
                                    temporaryPackets.clear();
                                    sequenceNumbers.clear();
                                    continue;
                                }
                            }

                            if (task != null) {
                                task.cancel();
                            }

                            task = new TimerTask() {
                                @Override
                                public void run() {
                                    synchronized (temporaryPackets) {
                                        if (!temporaryPackets.isEmpty()) {
                                            try {
                                                sendBackAck(packet);
                                            } catch (InterruptedException e) {
                                                throw new RuntimeException(e);
                                            }
                                        }
                                    }
                                }
                            };
                            timer.schedule(task, 3000); // 3 seconds timeout

                            if (temporaryPackets.size() == SWS) {
                                task.cancel();
                                sendBackAck(packet);
                            }




                            /*
                             * Initialize a temporary packet list and received packet list
                             * Check the order of the incoming packets sequence numbers
                             * When packet is received,
                             * Reset timeout
                             * Add its seq number to list of seqs
                             * Check whether the last packet contains ??more packet?? flag
                             * If it does:
                             *      run composition algorithm
                             *      If offset values are not in order,
                             *         Order them
                             *      print the message
                             * Else:
                             * If seq does not exist in received packets:
                             *      add it to received packets
                             * If temporary packet size reaches window size, or timeout occurs after last packet:
                             *      send back an ack of all of their seq's
                             *   Continue waiting for packets
                             * <p>
                             *
                             * If it does, wait for a timeout and run an aggregation algorithm to compose the full message
                             * END
                             *
                             * DONE
                             * Other case
                             * If there is timeout, send the acks that you have received
                             * If packets are missing, continue
                             * When a packet arrives, set a timer
                             * If temporary p
                             *
                             */
                            /*
                             * Start a timeout and reset it each time you receive a packet
                             * If you receive window size amount of data || the last packet's flag is "NO MORE"
                             *      Send acks for the packets that you have received
                             * Else if you receive "more data" flag but the window size hasn't been reached
                             *      Send acks for the packets that you have received
                             * Else
                             *      Send acks for the packets that you have received
                             */

                            /*
                             *
                             * Store each Seq value
                             * If the received packet's seq value is equal to a packet that has already been received,
                             * Discard it
                             * Otherwise, store it until the flag is turned to NO MORE DATA TO SEND.
                             * Put the packet to the packetToSend Queue
                             * This
                             */

                        }


//                        printByteBuffer(m.getData(), m.getData().capacity()); //Just print the data

                    } else if (m.getType() == MessageType.DATA_SHORT) {
                        System.out.print("DATA_SHORT: ");
                        printByteBuffer(m.getData(), m.getData().capacity()); //Just print the data
                    } else if (m.getType() == MessageType.DONE_SENDING) {
                        System.out.println("DONE_SENDING");
                    } else if (m.getType() == MessageType.HELLO) {
                        System.out.println("HELLO");
                    } else if (m.getType() == MessageType.SENDING) {
                        System.out.println("SENDING");
                    } else if (m.getType() == MessageType.END) {
                        System.out.println("END");
                        System.exit(0);
                    }
                } catch (InterruptedException e) {
                    System.err.println("Failed to take from queue: " + e);
                }
            }
        }
    }

}
//                if (read > 0) {
//                    if (temp.get(read - 1) == '\n' || temp.get(read - 1) == '\r')
//                        new_line_offset = 1; //Check if last char is a return or newline so we can strip it
//                    if (read > 1 && (temp.get(read - 2) == '\n' || temp.get(read - 2) == '\r'))
//                        new_line_offset = 2; //Check if second to last char is a return or newline so we can strip it
//                    ByteBuffer toSend = ByteBuffer.allocate(read - new_line_offset); // copy data without newline / returns
//                    toSend.put(temp.array(), 0, read - new_line_offset); // enter data without newline / returns
//                    Message msg;
//                    if ((read - new_line_offset) > 2) {
//                        msg = new Message(MessageType.DATA, toSend);
//                    } else {
//                        msg = new Message(MessageType.DATA_SHORT, toSend);
//                    }
//                    sendingQueue.put(msg);
//                }
//    public class Node extends Thread {
//
//        int[] txpkt;
//
//        String sourceAddress = "234.234";
//        String destinationAddress = "122.32";
//        Integer sequenceNumber = 123;
//        Integer ackNumber = 1;
//        Integer offset = 40;
//
//        Integer flag = 1;
//        String nextHop = "132.225";
//
//        ByteBuffer buff = ByteBuffer.allocate(1024);
//        private Random random;
//
//        public String generateRandomIP() {
//            random = new Random();
//            int firstNumber = random.nextInt(256); // Generates a random number between 0 (inclusive) and 256 (exclusive)
//            int secondNumber = random.nextInt(256);
//            String ip = String.format("%d-%d", firstNumber,".", secondNumber);
//            return ip;
//        }
//
//
//
//
//
//
//        private byte[] ip;
//        //        private byte[] nextHop;
//        private byte[] counter;
//
//        int[] lastSeq;
//
//
//
////        public void routingPacket(byte[] ip, byte[] nextHop, byte[] counter) {
////            if (ip.length != 2 || nextHop.length != 2 || counter.length != 2) {
////                throw new IllegalArgumentException("Invalid IP, next hop, or counter size");
////            }
////            this.ip = ip;
////            this.nextHop = nextHop;
////            this.counter = counter;
////        }
//
//        public void messagePacket() {
//
//        }
//
//        public void finalizingPacket() {
//
//        }
//
//        public void ackPacket() {
//
//        }
//
//
//
////        public void inspectPacket() {
////            txpkt = new int[32];
////            try {
////                txpkt[0] = receivedQueue.take();
////            } catch (InterruptedException e) {
////                throw new RuntimeException(e);
////            }
////        }
////
////    }
//    }
//}

