import java.io.IOException;
import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class Listener extends Thread {
    public static final String ELECTION = "ELE";
    public static final String NEW_SERVER = "NS";
    public static final String UPDATE_SERVER_LIST = "USL";

    private MulticastSocket socket;

    public Listener(MulticastSocket socket){
        this.socket = socket;
    }

    @Override
    public void run() {

        while(true){
            try {
                byte[] buf = new byte[1024];
                DatagramPacket recv = new DatagramPacket(buf, buf.length);
                socket.receive(recv);

                System.out.println("Received Packet containing:");
                System.out.println(new String(buf));

                //parsing packet
                List<String> list = new ArrayList<>(Arrays.asList((new String(buf)).split(";")));
                String packetType = list.get(0);
                switch (packetType) {
                    case ELECTION :
                        processElectionPacket();
                        break;
                    case NEW_SERVER :
		    	processNewServerPacket(list);
                        break;
                    case UPDATE_SERVER_LIST :

                        break;
                }

            } catch (IOException e){
                System.out.println(e);
            }
        }

    }

    public void processElectionPacket(){

    }

    public void processNewServerPacket(List<String> list) {
	if (IdServer.isCoordinator()) {
		System.out.println("Received new server packet: PID=" + list.get(1) + ",IP=" + list.get(2));
		//IdServer.addServer(list.get(list.get(1), list.get(2)));
		//StringWriter str = new StringWriter();
		//str.write(Listener.NEW_SERVER + ";" + pid + ";" + ipaddress);
		//DatagramPacket electionPacket = new DatagramPacket(str.toString().getBytes(), str.toString().length(), group, MULTICAST_PORT);
		//s.send(electionPacket);
	}
    }

}
