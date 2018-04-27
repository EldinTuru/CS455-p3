import java.io.IOException;
import java.io.StringWriter;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class Listener extends Thread {
    public static final String ELECTION = "ELE";
    public static final String NEW_SERVER = "NWS";
    public static final String UPDATE_SERVER_LIST = "USL";
    public static final String IM_THE_COORDINATOR = "IAM";
    public static final String UPDATE_DB = "UDB";
    public static final String PING = "PNG";
    public static final String PONG = "ONG";
    public static final String BULLY = "BLY";
    private IdServer server;

    private MulticastSocket socket;

    public Listener(MulticastSocket socket, IdServer server){
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        while(true){
            try {
                byte[] buf = new byte[1024];
                DatagramPacket recv = new DatagramPacket(buf, buf.length);
                socket.receive(recv);

                System.out.println("Received Packet containing: " + new String(buf));

                //parsing packet
                List<String> parsedPacket = new ArrayList<>(Arrays.asList((new String(buf)).split(";")));
                String packetType = parsedPacket.get(0);
                switch (packetType.trim()) {
                    case ELECTION :
                        processElectionPacket();
                        break;
                    case NEW_SERVER :
                        processNewServerPacket(parsedPacket);
                        break;
                    case UPDATE_SERVER_LIST :
                        processUpdateServerListPacket(parsedPacket);
                        break;
                    case IM_THE_COORDINATOR :
                        processImTheCoordinator(parsedPacket);
                        break;
                    case UPDATE_DB:
                        processUpdateDBPacket(parsedPacket);
                        break;
                    case PING:
                        processPingPacket(); // coordinator only
                        break;
                    case PONG:
                        processPongPacket();
                        break;
                    case BULLY:
                        processBullyPacket();
                        break;
                }

            } catch (IOException e){
                System.out.println(e.getMessage());
            }
        }

    }

    private void processElectionPacket(){

    }

    private void processBullyPacket(){

    }

    private void processPingPacket(){
        System.out.println("Processing ping packet while coordinator is: " + server.isCoordinator());
        if (server.isCoordinator()) {
            String packet = Listener.PONG;
            StringWriter str = new StringWriter(packet.length());
            str.write(packet, 0, packet.length());
            try {
                DatagramPacket electionPacket = new DatagramPacket(
                        str.toString().getBytes(),
                        str.toString().length(),
                        InetAddress.getByName(IdServer.MULTICAST_ADDRESS),
                        IdServer.MULTICAST_PORT);
                System.out.println("Sent PONG");
                socket.send(electionPacket);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
    }

    private void processPongPacket(){
        if (!server.isCoordinator()) {
            server.setTimeElapsed(0);
            server.setWaitingOnReply(false);
        }
    }

    private void processNewServerPacket(List<String> parsedPacket) {
        if (server.isCoordinator()) {
            System.out.println("Received new server packet: PID=" + parsedPacket.get(1) + ",IP=" + parsedPacket.get(2));
            server.addServer(parsedPacket.get(1), parsedPacket.get(2));
            // Loop through the server list and create a respective string of pids and ip addresses
            String pids = "";
            String ips = "";
            for (String currPid: server.getServerList().keySet()) {
                pids = pids.concat("," + currPid);
                ips = ips.concat("," + server.getServerList().get(currPid));
            }
            if (!pids.equals("")) {
                pids = pids.substring(1);
                ips = ips.substring(1);
            }

            // Send the packet with the server list of pids and ips
            String packet = Listener.UPDATE_SERVER_LIST + ";" + pids + ";" + ips;
            StringWriter str = new StringWriter(packet.length());
            str.write(packet, 0, packet.length());
            try {
                DatagramPacket electionPacket = new DatagramPacket(
                        str.toString().getBytes(),
                        str.toString().length(),
                        InetAddress.getByName(IdServer.MULTICAST_ADDRESS),
                        IdServer.MULTICAST_PORT);
                socket.send(electionPacket);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }

            // Send the database to the new server
            server.sendUpdateDBPacket();
        }
    }

    private void processUpdateServerListPacket(List<String> parsedPacket){
        List<String> pidList = new ArrayList<>(Arrays.asList((parsedPacket.get(1)).split(",")));
        List<String> ipList = new ArrayList<>(Arrays.asList((parsedPacket.get(2)).split(",")));
        server.updateServerList(pidList, ipList);
    }

    private void processImTheCoordinator(List<String> parsedPacket){
        if (server.comparePids(parsedPacket.get(1)) != 0) {
            server.setCoordinator(false);
            server.setWaitingOnReply(false);
            server.setTimeElapsed(0);
        } else {
            server.setCoordinator(true);
            server.startSaveStateThread();
        }
    }

    private void processUpdateDBPacket(List<String> parsedPacket){
        //System.out.println(parsedPacket.get(1));
    }
}
