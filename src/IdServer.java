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
 *
 */
public class IdServer implements Service, Runnable {
    private HashMap<String, User> users;
    private boolean verbose;
    private static boolean isCoordinator = false;
    private static final String MULTICAST_ADDRESS = "230.230.250.230";
    private static final int MULTICAST_PORT = 5199;

	@SuppressWarnings("unchecked")
   	public IdServer(boolean verbose) {
            this.verbose = verbose;
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
		new Thread(this).start();//starting thread to save info every 10 seconds
	}

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
		if (System.getSecurityManager() == null){
        	try {
				String ipaddress = Inet4Address.getLocalHost().getHostAddress();
				System.setProperty("java.rmi.server.hostname", ipaddress);
				System.out.println("Server's IP address: " + ipaddress);
			} catch (UnknownHostException e) {
				System.out.println("Can't set java.rmi.server.hostname.");
			}
			System.out.println("Setting System Properties....");
			System.setProperty("javax.net.ssl.keyStore", "../resources/Server_Keystore");
			System.setProperty("javax.net.ssl.keyStorePassword", "timmyterdal123!");
			System.setProperty("java.security.policy", "security.policy");
			System.setSecurityManager(new SecurityManager());
		}
		// Spin up server
        Service server = new IdServer(verboseArg);

        // Join multicast group
		MulticastSocket s = null;
        InetAddress group = null;
		try {
            group = InetAddress.getByName(MULTICAST_ADDRESS);
            s = new MulticastSocket(MULTICAST_PORT);
			Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
			while (networkInterfaces.hasMoreElements())
			{
				NetworkInterface networkInterface = (NetworkInterface) networkInterfaces.nextElement();
				if(networkInterface.getName().startsWith("wlan") || networkInterface.getName().startsWith("eth")) {
					System.out.println("Attempting to connect to: " + networkInterface.getName());
                    try {
                        NetworkInterface net = NetworkInterface.getByName(networkInterface.getName());
                        s.setNetworkInterface(net);
                        s.joinGroup(group);
                        break;
                    } catch (IOException e) {
                        System.err.println("Failed joining multicast group: " + e.getMessage());
                    }
				}
			}
        } catch (IOException e) {
            System.err.println("Failed joining multicast group: " + e.getMessage());
        }

        // Connect to multicast group
        try {
		    // Get the PID of this process
            String pid = ManagementFactory.getRuntimeMXBean().getName();
            pid = pid.substring(0, pid.indexOf('@'));
            System.out.println("This servers pid is " + pid);
            StringWriter str = new StringWriter();
            str.write(pid);
            DatagramPacket electionPacket = new DatagramPacket(str.toString().getBytes(), str.toString().length(), group,
                    MULTICAST_PORT);
            // Send PID to everyone in the group
            s.send(electionPacket);

            // Receive the PID list back from coordinator
            byte[] buf = new byte[1024];
            DatagramPacket recv = new DatagramPacket(buf, buf.length);
			s.receive(recv);

            // We must parse twice to forget about our coordinator
			int timeout = s.getSoTimeout();
			boolean coordinatorResponded = true;
			buf = new byte[1024];
			recv = new DatagramPacket(buf, buf.length);
			s.setSoTimeout(4000);
			try {
				s.receive(recv);
			} catch (SocketTimeoutException e) {
				coordinatorResponded = false;
			}
			s.setSoTimeout(timeout);
            System.out.println("Received list of PIDs/IPs from coordinator(lone server if empty): " + new String(buf));
            List<String> list = new ArrayList<>(Arrays.asList((new String(buf)).split(",")));

            // Check if this servers PID is biggest and hold an election if it is
            boolean isBiggest = true;
            for (String currPid: list) {
            	if (!coordinatorResponded) {
            		break;
				}
                if (Integer.parseInt(currPid.trim()) > Integer.parseInt(pid)) {
                    isBiggest = false;
                    break;
                }
            }
            // If this server is the biggest send a multicast packet to everyone in the group
            if (isBiggest) {
                str = new StringWriter();
                str.write("OK");
                electionPacket = new DatagramPacket(str.toString().getBytes(), str.toString().length(), group,
                        MULTICAST_PORT);
                s.send(electionPacket);
                IdServer.setCoordinator(true);
                System.out.println("I AM THE COORDINATOR");
            }
        } catch (IOException | NullPointerException e) {
		    System.err.println("Failed while attempting to send/receive multicast packet: " + e.getMessage());
        }

		try {
		    if (IdServer.isCoordinator()) {
                // name to register in RMIRegistry
                String name = "//localhost:" + port + "/Service";
                RMIClientSocketFactory rmiClientSocketFactory = new SslRMIClientSocketFactory();
                RMIServerSocketFactory rmiServerSocketFactory = new SslRMIServerSocketFactory();
                Service stub = (Service) UnicastRemoteObject.exportObject(server, 0, rmiClientSocketFactory, rmiServerSocketFactory);
                Registry registry = LocateRegistry.createRegistry(Integer.parseInt(port));
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
				try {
					FileOutputStream fos = new FileOutputStream("users_table.ser");
					ObjectOutputStream oos = new ObjectOutputStream(fos);
					oos.writeObject(users);
					oos.close();
					fos.close();
					printVerbose("Serialized HashMap data is saved in users_table.ser");
				} catch (IOException e){
					System.out.println(e);
				}
			}
		});

   		while(true){
   		    try {
   		    	FileOutputStream fos = new FileOutputStream("users_table.ser");
				ObjectOutputStream oos = new ObjectOutputStream(fos);
				oos.writeObject(users);
				oos.close();
				fos.close();
				printVerbose("Serialized HashMap data is saved in users_table.ser");
				Thread.sleep(10000);
			} catch (IOException e){
   		    	System.out.println(e);
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

    public static boolean isCoordinator() {
        return isCoordinator;
    }

    public static void setCoordinator(boolean coordinator) {
        isCoordinator = coordinator;
    }

}
