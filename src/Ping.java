import java.io.IOException;
import java.io.StringWriter;
import java.net.*;


public class Ping extends Thread {
    private IdServer server;

    private MulticastSocket socket;

    public Ping(MulticastSocket socket, IdServer server){
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            while (true) {
                if (!server.isCoordinator() && server.getTimeElapsed() > 10 && !server.isWaitingOnReply()) {
                    // Send the ping packet to the server
                    StringWriter str = new StringWriter();
                    str.write(Listener.PING + ";");
                    DatagramPacket pingPacket = new DatagramPacket(
                            str.toString().getBytes(),
                            str.toString().length(),
                            InetAddress.getByName(IdServer.MULTICAST_ADDRESS),
                            IdServer.MULTICAST_PORT);
                    System.out.println("Sending PING");
                    server.getSocket().send(pingPacket);
                    server.setTimeElapsed(0);
                    server.setWaitingOnReply(true);
                } else if (!server.isCoordinator() && server.getTimeElapsed() > 10 && server.isWaitingOnReply()) {
                    // Hold Election
                    System.out.println("COORDINATOR IS DOWN!");
                    System.out.println("!server.isCoordinator() && server.getTimeElapsed() > 10 && !server.isWaitingOnReply()");
                    try {
                        StringWriter str = new StringWriter();
                        str.write(Listener.ELECTION + ";");
                        DatagramPacket pingPacket = new DatagramPacket(
                                str.toString().getBytes(),
                                str.toString().length(),
                                InetAddress.getByName(IdServer.MULTICAST_ADDRESS),
                                IdServer.MULTICAST_PORT);
                        System.out.println("Starting new election!");
                        server.getSocket().send(pingPacket);
                        Thread.sleep(10000);
                    } catch (IOException a) {
                        System.out.println("Error");
                    }
                } else {
                    server.setTimeElapsed(server.getTimeElapsed() + 1);
                    Thread.sleep(1000);
                }
            }
        } catch (SocketTimeoutException e) {
            System.out.println("Coordinator response timed out!");
            // HOLD ELECTION
            try {
                StringWriter str = new StringWriter();
                str.write(Listener.ELECTION + ";");
                DatagramPacket pingPacket = new DatagramPacket(
                        str.toString().getBytes(),
                        str.toString().length(),
                        InetAddress.getByName(IdServer.MULTICAST_ADDRESS),
                        IdServer.MULTICAST_PORT);
                System.out.println("Starting new election.");
                server.getSocket().send(pingPacket);
            } catch (IOException a) {
                System.out.println("Error");
            }
        } catch(IOException | InterruptedException v){
            System.out.println(v);
        }

    }

    private void processElectionPacket(){

    }
}
