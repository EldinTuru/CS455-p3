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
		    	if (IdServer.isCoordinator()) {
				System.out.println("Received new server Packet: PID=" + list.get(1) +",IP=" + list.get(2));
				// TODO send update packet to all servers (multicast)
			}
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

}
