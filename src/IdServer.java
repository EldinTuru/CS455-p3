import jdk.nashorn.internal.runtime.regexp.joni.exception.ValueException;

import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.net.*;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author edin, eldin
 * Calculation for passing grade: (10 * 82 / 120) + (70 * (277 + (Enter score for p3 here)) / 440) + (5) + (10)
 */
public class IdServer implements Service, Runnable {
    private HashMap<String, User> users;
    private boolean verbose;
    private boolean isCoordinator;
    private String myPID;
	private String myIP;
    public static final String MULTICAST_ADDRESS = "230.230.250.230";
    public static final int MULTICAST_PORT = 5199;
    private HashMap<String, String> serverList = new HashMap<>();
    private MulticastSocket socket;

	@SuppressWarnings("unchecked")
   	public IdServer(boolean verbose) {
		this.verbose = verbose;
		this.socket = null;
	    File f = new File("users_table.ser");
	    if(f.exists()) {
			try {
				FileInputStream fis = new FileInputStream("users_table.ser");
				ObjectInputStream ois = new ObjectInputStream(fis);
				users = (HashMap<String, User>) ois.readObject();
				ois.close();
				fis.close();
			} catch (IOException e) {
				System.out.println(e);
			} catch (ClassNotFoundException e) {
				System.out.println(e);
			}
		} else {
	    	users = new HashMap<>();
		}
		//startSaveStateThread();
	}

	@SuppressWarnings("unchecked")
	public static void main(String[] args) {
		String port = "1099";
        boolean verboseArg = false;
        // Check the optional command line arguments
        if (args.length >= 2 && (args[0].equals("--numport") || args[0].equals("-n"))) {
        	port = args[1];
        	if (args.length == 3 && (args[2].equals("--verbose") || args[2].equals("-v"))) {
        		verboseArg = true;
        	}
        } else if (args.length == 1 && (args[0].equals("--verbose") || args[0].equals("-v"))) {
        	verboseArg = true;
        }
		// Spin up server
		IdServer server = new IdServer(verboseArg);

		if (System.getSecurityManager() == null){
        	try {
				server.setMyIP(Inet4Address.getLocalHost().getHostAddress());
				System.setProperty("java.rmi.server.hostname", server.getMyIP());
				System.out.println("Server's IP address: " + server.getMyIP());
			} catch (UnknownHostException e) {
				System.out.println("Can't set java.rmi.server.hostname.");
			}
			System.out.println("Setting System Properties....");
			System.setProperty("javax.net.ssl.keyStore", "../resources/Server_Keystore");
			System.setProperty("javax.net.ssl.keyStorePassword", "timmyterdal123!");
			System.setProperty("java.security.policy", "security.policy");
			System.setSecurityManager(new SecurityManager());
		}

        // Join multicast group
        InetAddress group = null;
		try {
            group = InetAddress.getByName(MULTICAST_ADDRESS);
            server.setSocket(new MulticastSocket(MULTICAST_PORT));
			Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
			while (networkInterfaces.hasMoreElements())
			{
				NetworkInterface networkInterface = (NetworkInterface) networkInterfaces.nextElement();
				if(networkInterface.getName().startsWith("wlan") || networkInterface.getName().startsWith("eth")) {
					System.out.println("Attempting to connect to: " + networkInterface.getName());
                    try {
                        NetworkInterface net = NetworkInterface.getByName(networkInterface.getName());
                        server.getSocket().setNetworkInterface(net);
                        server.getSocket().joinGroup(group);
                        break;
                    } catch (IOException e) {
                        System.err.println("Failed joining multicast group: " + e.getMessage());
                    }
				}
			}
        } catch (IOException e) {
            System.err.println("Failed joining multicast group, shutting down: " + e.getMessage());
            System.exit(1);
        }

        // Connect to multicast group
        try {
		    // Get the PID of this process
            String tempPid = ManagementFactory.getRuntimeMXBean().getName();
			server.setMyPID(tempPid.substring(0, tempPid.indexOf('@')));
            System.out.println("This servers pid is " + server.getMyPID());
            StringWriter str = new StringWriter();
            str.write(Listener.NEW_SERVER + ";" + server.getMyPID() + ";" + server.getMyIP());
            DatagramPacket electionPacket = new DatagramPacket(str.toString().getBytes(), str.toString().length(), group,
                    MULTICAST_PORT);
            // Send new server information to everyone in the group
            server.getSocket().send(electionPacket);

            // Receive the PID list back from coordinator, if no coordinator, hold election.
			// Ignore all other packets besides update server list packet
			int timeout = server.getSocket().getSoTimeout();
			boolean coordinatorResponded = true;
			byte[] buf;
			DatagramPacket recv;
			List<String> list = new ArrayList<>();
			server.getSocket().setSoTimeout(4000);
			boolean isBiggest = true;
			while (true) {
				buf = new byte[1024];
				recv = new DatagramPacket(buf, buf.length);
				try {
					server.getSocket().receive(recv);
				} catch (SocketTimeoutException e) {
					System.out.println("Coordinator response timed out!");
					coordinatorResponded = false;
					break;
				}
				list = new ArrayList<>(Arrays.asList((new String(buf)).split(";")));
				if (list.get(0).equals(Listener.UPDATE_SERVER_LIST)) {
					// Finally got update server list packet, break out of listening loop
					System.out.println("Received list of PIDs/IPs from coordinator(lone server if empty): " + new String(buf));
					// Now we have know there is a coordinator, get an updated Database

					buf = new byte[65535];
					recv = new DatagramPacket(buf, buf.length);
					try {
						server.getSocket().receive(recv);
						List<String> json = new ArrayList<>(Arrays.asList((new String(buf)).split(";")));
						server.parseJson(json.get(1));
						server.saveDB();
					} catch (SocketTimeoutException e) {
						System.out.println("Coordinator response timed out!");
						coordinatorResponded = false;
						break;
					}

					break;
				}
			}
			server.getSocket().setSoTimeout(timeout);
			// Add the servers we received to our server list
			if (coordinatorResponded) {
				List<String> pidList = new ArrayList<>(Arrays.asList((list.get(1)).split(",")));
				List<String> ipList = new ArrayList<>(Arrays.asList((list.get(2)).split(",")));
				server.updateServerList(pidList, ipList);
				// Check if this servers PID is biggest
				for (String currPid: pidList) {
					System.out.println("Comparing to: " + currPid);
					if (Integer.parseInt(currPid.trim()) > Integer.parseInt(server.getMyPID())) {
						isBiggest = false;
						break;
					}
				}
			}
			server.addServer(server.getMyPID(), server.getMyIP());

            // If this server is the biggest send a multicast packet to everyone in the group saying we are coordinator
            if (isBiggest) {
                str = new StringWriter();
                str.write(Listener.IM_THE_COORDINATOR + ";" + server.getMyPID());
                electionPacket = new DatagramPacket(str.toString().getBytes(), str.toString().length(), group,
                        MULTICAST_PORT);
                server.getSocket().send(electionPacket);
                server.setCoordinator(true);
                System.out.println("I AM THE COORDINATOR");
            }
        } catch (IOException | NullPointerException e) {
		    System.err.println("Failed while attempting to send/receive multicast packet: " + e.getMessage());
        }

        Listener listener = new Listener(server.getSocket(), server);
		listener.start();

		try {
		    if (server.isCoordinator()) {
                // name to register in RMIRegistry
                String name = "//localhost:" + port + "/Service";
                RMIClientSocketFactory rmiClientSocketFactory = new SslRMIClientSocketFactory();
                RMIServerSocketFactory rmiServerSocketFactory = new SslRMIServerSocketFactory();
                Service stub = (Service) UnicastRemoteObject.exportObject((Service) server, 0, rmiClientSocketFactory, rmiServerSocketFactory);
                Registry registry = LocateRegistry.getRegistry(Integer.parseInt(port));
                registry.rebind(name, stub);
                System.out.println("IdServer is bound!");
            }
		} catch (Exception e) {
			System.err.println("Sadface");
			e.printStackTrace();
		}
	}

	/**
	 * Thread to save the state of our hash table every 10 seconds.
	 * Also so we can catch when the server is shutdown and save the state.
	 */
	public void run() {

		Runtime.getRuntime().addShutdownHook(new Thread()
		{
			@Override
			public void run()
			{
				saveDB();
				printVerbose("Serialized HashMap data is saved in users_table.ser");
			}
		});

   		while(isCoordinator()){
   		    try {
				saveDB();
				Thread.sleep(10000);
			} catch (InterruptedException e){
				System.out.println(e);
			}
		}
	}

	// TODO make a listener for the multicast port to see if an election is being held

    // TODO make a thread for pinging coordinator every 10 seconds

        /**
         * Create a user and save them in the database
         *
         * @param loginname - login name of the created account
         * @param realname - real name the user wants to have
         * @param password - password of the users created account
         * @param ip - ip address that this user was at when creating account
         */
	public synchronized String createLogin(String loginname, String realname, byte[] password, InetAddress ip) throws RemoteException {
		printVerbose("Checking if login name " + loginname + " is already taken...");
		if(users.containsKey(loginname)){
			return "This loginname is already taken";
		}

		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		Date date = new Date();

		printVerbose("Creating the new user...");
		User u = new User(UUID.randomUUID(), ip, dateFormat.format(date), realname, password);
		users.put(loginname, u);
		return "You have created a login. UUID for the account is: " + u.getUuid().toString();
	}

        /**
         * Looks up a user with the specified loginname and displays their account information.
         *
         * @param loginname - name of the user to get information for
         */
	public synchronized String lookup(String loginname) throws RemoteException {
		printVerbose("Checking if user " + loginname + " exists...");
		if(users.containsKey(loginname)){
			return "Information found for account: " + loginname + "\n" + users.get(loginname).toString();
		} else {
			return loginname + " can not be found";
		}
	}

        /**
         * Looks up a user with the specified UUID and displays their account information.
         *
         * @param uuid - uuid of the user to get information for
         */
	public synchronized String reverseLookup(UUID uuid) throws RemoteException {
		printVerbose("Looking for user with UUID: " + uuid + "...");
		for (Map.Entry<String, User> entry : users.entrySet()) {
			String loginname = entry.getKey();
			User user = entry.getValue();
			if(user.getUuid().equals(uuid)){
				return "Information found for account: " + loginname + "\n" + user.toString();
			}
		}
		return uuid.toString() + " can not be found";
	}

        /**
         * Modify the name of an existing user in the database.
         *
         * @param oldname - the name of the user we want to modify
         * @param newname - the new name we want to store in the database for the specified user
         * @param password - password for the user so we can verify them in the database
         */ 
	public synchronized String modify(String oldname, String newname, byte[] password) throws RemoteException
	{
		printVerbose("Checking if " + oldname + " is a valid user...");
		if(!users.containsKey(oldname)){
			return oldname + " can not be found";
		}

		printVerbose("Checking if " + newname + " is taken...");
		if(users.containsKey(newname)){
			return newname + " is already taken";
		}

		User u = users.get(oldname);
		printVerbose("Checking password...");
		if(Arrays.equals(password, u.getPassword())){
			printVerbose("Changing names...");
			users.remove(oldname);
			users.put(newname, u);
			return "Successfully changed name " + oldname + " to " + newname;
		} else {
			return "Incorrect password for " + oldname;
		}
	}

        /**
         * Delete a user from the database
         *
         * @param loginname - the name of the user that we want to delete
         * @param password - the password of the user we want to delete
         */
	public synchronized String delete(String loginname, byte[] password) throws RemoteException
	{
	    if(users.containsKey(loginname)){
	    	if(Arrays.equals(password, users.get(loginname).getPassword())){
				printVerbose("Removing user " + loginname +  "...");
	    		users.remove(loginname);
	    		return "Successfully deleted " + loginname;
			} else {
	    		return "Incorrect password for " + loginname;
			}
		}
		return "Could not find " + loginname;
	}

        /**
         * Displays all users, uuids, or all information, depending on user input
         *
         * @param item - ("users" | "uuids" | "all") string defining what information you want
         */
	public synchronized String getInfo(String item) throws RemoteException
	{
	    if(item.equals("users")){
	    	String list = "";
            printVerbose("Getting users...");
			for (Map.Entry<String, User> entry : users.entrySet()) {
				list += entry.getKey() + "\n";
			}
			return list;
		}

		if(item.equals("uuids")){
			String list = "";
			printVerbose("Getting UUIDs...");
			for (Map.Entry<String, User> entry : users.entrySet()) {
				list += entry.getValue().getUuid().toString() + "\n";
			}
			return list;
		}

		if(item.equals("all")){
			String list = "";
			printVerbose("Getting all information about users...");
			for (Map.Entry<String, User> entry : users.entrySet()) {
				list += "User: " + entry.getKey() + "\n" + entry.getValue().toString() + "\n";
			}
			return list;
		}

		return "No accounts have been created";
	}

	/**
	 * Prints a message if the server was run in verbose mode
	 *
	 * @param message - the string to print to the server's console
	 */
	public void printVerbose(String message) {
            if (verbose) {
                System.out.println(message);
            }
	}

	public void sendUpdateDBPacket() {
		String packet = Listener.UPDATE_DB + ";" + getJSON();
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
	}

	private void saveDB() {
		try {
			FileOutputStream fos = new FileOutputStream("users_table.ser");
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(users);
			oos.close();
			fos.close();
			printVerbose("Serialized HashMap data is saved in users_table.ser");
		} catch (IOException e){
			System.out.println(e.getMessage());
		}
	}

    public void updateServerList(List<String> pids, List<String> ips) {
		serverList = new HashMap<>();
        for (int i = 0; i < pids.size(); i++) {
        	serverList.put(pids.get(i), ips.get(i));
		}
    }

    public HashMap<String, String> getServerList() {
        return serverList;
    }

    public void addServer(String pid, String ipaddress) {
        serverList.put(pid.trim(), ipaddress.trim());
    }

    public void removeServer(String pid) {
        serverList.remove(pid);
    }

    public void setMyPID(String pid) {
		myPID = pid;
	}

    public String getMyPID() {
		return myPID.trim();
	}

	/**
	 * If currPid < myPid, return -1
	 * If currPid = myPid, return 0
	 * If currPid > myPid, return 1
	 *
	 * @param currPid The id to compare to our ID
	 * @return 1, 0, -1
	 */
    public int comparePids(String currPid) {
		return Integer.compare(Integer.parseInt(currPid.trim()), Integer.parseInt(getMyPID()));
	}

	public String getMyIP() {
		return myIP;
	}

	public void setMyIP(String myIP) {
		this.myIP = myIP;
	}


	public boolean isCoordinator() {
        return isCoordinator;
    }

    public void setCoordinator(boolean coordinator) {
        isCoordinator = coordinator;
    }

    public void startSaveStateThread() {
		new Thread(this).start();//starting thread to save info every 10 seconds
	}
	public HashMap<String, User> getUsers() {
		return this.users;
	}

	public void setUsers(HashMap<String, User> users) {
		this.users = users;
	}

	public MulticastSocket getSocket() {
		return socket;
	}

	public void setSocket(MulticastSocket socket) {
		this.socket = socket;
	}

	public String getJSON(){
    	String json = "";
    	System.out.println(getUsers().get("eldin"));
    	for(String loginName : getUsers().keySet()){
    		json += ",," + loginName + "##" + getUsers().get(loginName).getJSON();
		}
		json = json.substring(2);
		return json;
	}

	public void parseJson(String json){
		List<String> users = new ArrayList<>(Arrays.asList(json.split(",,")));
		System.out.println("users size" + users.size());
		//users.remove(users.size());
		HashMap<String, User> tempUsers = new HashMap<>();
		for (String user : users){
			List<String> userInfo = new ArrayList<>(Arrays.asList(user.split("##")));
			System.out.println("size of list" + userInfo.size());
			User u = new User(UUID.fromString(userInfo.get(1)),
					userInfo.get(2),
					userInfo.get(3),
					userInfo.get(4),
					userInfo.get(5).getBytes());
			tempUsers.put(userInfo.get(0), u);
		}
	}
}
