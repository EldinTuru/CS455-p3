import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;
import java.io.IOException;
import java.io.StringWriter;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Class that handles receiving and processing packets
 */
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
    private boolean isElectionInProcess;

    private MulticastSocket socket;

    public Listener(MulticastSocket socket, IdServer server){
        this.socket = socket;
        this.server = server;
        this.isElectionInProcess = false;
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
                        processBullyPacket(parsedPacket);
                        break;
                }

            } catch (IOException e){
                System.out.println(e.getMessage());
            }
        }

    }

    /**
     * Process election packet. Sends out BULLY packet
     */
    private void processElectionPacket(){
        isElectionInProcess = true;
        try {
            StringWriter str = new StringWriter();
            str.write(Listener.BULLY + ";" + server.getMyPID() + ";" + server.getMyIP());
            DatagramPacket bullyPacket = new DatagramPacket(
                    str.toString().getBytes(),
                    str.toString().length(),
                    InetAddress.getByName(IdServer.MULTICAST_ADDRESS),
                    IdServer.MULTICAST_PORT);
            System.out.println("Sending Bully packet");
            server.getSocket().send(bullyPacket);
        } catch (IOException e) {
            System.out.println("Error sending bully packet.");
        }
    }

    /**
     * This will proccess the bully packet. Will receive all bully packets
     * then loop through the bully packet pids' and determine if its the
     * coordinator
     * @param parsedPacket
     */
    private void processBullyPacket(List<String> parsedPacket){
        ArrayList<String> pidList = new ArrayList<>();
        ArrayList<String> ipList = new ArrayList<>();
        pidList.add(parsedPacket.get(1));
        ipList.add(parsedPacket.get(2));

        // Receive bully packets until we timeout or get a non bully packet
        try {
            int timeout = server.getSocket().getSoTimeout();
            server.getSocket().setSoTimeout(4000);
            while (true) {
                byte[] buf = new byte[1024];
                DatagramPacket recv = new DatagramPacket(buf, buf.length);
                socket.receive(recv);
                System.out.println("Received Packet containing in bully: " + new String(buf));
                List<String> bullyPacket = new ArrayList<>(Arrays.asList((new String(buf)).split(";")));
                if (bullyPacket.get(0).equals(BULLY)) {
                    pidList.add(bullyPacket.get(1));
                    ipList.add(bullyPacket.get(2));
                }
                else
                    break;
            }
            server.getSocket().setSoTimeout(timeout);
        }catch (Exception e) {
            System.out.println("Stopped bully algorithm.");
        }
        boolean isBiggest = true;
        for (String currPid: pidList) {
            System.out.print("Comparing PID: " + currPid.trim());
            if (server.comparePids(currPid.trim()) > 0) {
                System.out.println(", it was greater than our PID of " + server.getMyPID());
                isBiggest = false;
                break;
            }
        }
        try {
            if (isBiggest) {
                StringWriter str = new StringWriter();
                str.write(Listener.IM_THE_COORDINATOR + ";" + server.getMyPID());
                DatagramPacket coordPacket = new DatagramPacket(
                        str.toString().getBytes(), str.toString().length(),
                        InetAddress.getByName(IdServer.MULTICAST_ADDRESS),
                        IdServer.MULTICAST_PORT);
                server.getSocket().send(coordPacket);
                server.setCoordinator(true);
                System.out.println("I AM THE COORDINATOR");
                String name = "//localhost:" + server.getPort() + "/Service";
                RMIClientSocketFactory rmiClientSocketFactory = new SslRMIClientSocketFactory();
                RMIServerSocketFactory rmiServerSocketFactory = new SslRMIServerSocketFactory();
                Service stub;
                if (server.getStub() != null)
                    stub = server.getStub();
                else
                    stub = (Service) UnicastRemoteObject.exportObject((Service) server, 0, rmiClientSocketFactory, rmiServerSocketFactory);
                Registry registry = LocateRegistry.getRegistry(server.getPort());
                registry.rebind(name, stub);
                System.out.println("IdServer is bound!");
            }
        } catch (RemoteException e) {
            System.out.println("Error setting up local registry.");
            e.printStackTrace();
        }catch (IOException e) {
            System.out.println("Error sending i am coordinator.");
            e.printStackTrace();
        }
        isElectionInProcess = false;
    }

    /**
     * This will process the ping packet and then send out
     * a pong packet
     */
    private void processPingPacket(){
        System.out.println("Processing Ping Packet");
        if (isElectionInProcess)
            return;
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

    /**
     * This will process the pong packet and then reset the ping for itself
     */
    private void processPongPacket(){
        if (!server.isCoordinator()) {
            server.setTimeElapsed(0);
            server.setWaitingOnReply(false);
        }
    }

    /**
     * The coordinator will process this packet and then send out an update server list
     * to all other servers
     * @param parsedPacket
     */
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

    /**
     * Updates the server list - no need for it right now,
     * currently not being used
     * @param parsedPacket
     */
    private void processUpdateServerListPacket(List<String> parsedPacket){
        List<String> pidList = new ArrayList<>(Arrays.asList((parsedPacket.get(1)).split(",")));
        List<String> ipList = new ArrayList<>(Arrays.asList((parsedPacket.get(2)).split(",")));
//        server.updateServerList(pidList, ipList);
    }

    /**
     * Process the IM_THE_COORDINATOR packet. Will set the coordinator to false
     * for the server
     * @param parsedPacket
     */
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

    /**
     * Process the update db packet. Calls parse json on the packet
     * so that the new db is saved
     * @param parsedPacket
     */
    private void processUpdateDBPacket(List<String> parsedPacket){
        if (!server.isCoordinator()) {
                server.parseJson(parsedPacket.get(1));
                System.out.println("Database Updated!");
        }
    }
}
