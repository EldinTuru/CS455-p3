import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import org.apache.commons.cli.*;

/**
 * @author edin, eldin
 *
 */
public class IdClient {
	private final static String CLIENT_STUB_INTERFACE = "Service";
	private final static String HOST = "localhost";

	public static void main(String args[]) {
		if (System.getSecurityManager() == null) {
			System.setProperty("javax.net.ssl.trustStore", "../resources/Client_Truststore");
			System.setProperty("java.security.policy", "file:./security.policy");
			System.setSecurityManager(new SecurityManager());
		}
		try {

			// create the command line parser
			CommandLineParser parser = new DefaultParser();

			// create the Options
			Options options = new Options();
			options.addRequiredOption("s", "server", true, "specify server host");
			options.addOption("n", "numport", true, "specify port number");

			OptionGroup query = new OptionGroup();
			Option o = new Option("c", "create", true, "create a login");
			o.setOptionalArg(true);
			o.setArgs(4);
			query.setRequired(true);
			query.addOption(o);
			query.addOption(new Option("l", "lookup", true, "lookup account information with loginname"));
			query.addOption(new Option("r", "reverse-lookup", true, "lookup account information with uuid"));
			o = new Option("m", "modify", true, "modify loginname");
			o.setOptionalArg(true);
			o.setArgs(4);
			query.addOption(o);
			o = new Option("d", "delete", true, "delete loginname");
			o.setOptionalArg(true);
			o.setArgs(3);
			query.addOption(o);
			query.addOption(new Option("g", "get", true, "get list of login names, uuids, or users"));
			options.addOptionGroup(query);


			if (args.length == 0) {
				printUsage(options);
			}

			try {
				// parse the command line arguments
				CommandLine line = parser.parse(options, args);

				String[] hosts = line.getOptionValues("s");
				String host = hosts[0];
				String port = "1099";
				if(line.hasOption("n")){
				    port = line.getOptionValue("n");
				}

				Service serve = null;
				for (String currHost: hosts) {
					try {
						String name = "//" + currHost + ":" + port + "/Service";
						Registry registry = LocateRegistry.getRegistry(Integer.parseInt(port));
						serve = (Service) registry.lookup(name);
						break;
					} catch (Exception e) {
						continue;
					}
				}


				if(line.hasOption("c")){
					String[] values = line.getOptionValues("c");

					if(values == null){
						printUsage(options);
						System.exit(1);
					}

					String loginname = values[0];
					String realname = System.getProperty("user.name");
					String password = "";

					if(values.length == 2){
						realname = values[1];
					}

					if(values.length == 3){
						if(values[1].equals("-p") || values[1].equals("--password")){
							password = values[2];
						} else {
							printUsage(options);
							System.exit(1);
						}
					}

					if(values.length == 4){
						realname = values[1];
						if(values[2].equals("-p") || values[2].equals("--password")){
							password = values[3];
						} else {
							printUsage(options);
							System.exit(1);
						}
					}

					byte[] encryptedPass = getSHA(password);
					String response = serve.createLogin(loginname, realname, encryptedPass, InetAddress.getLocalHost());
					System.out.println(response);
				}

				if(line.hasOption("l")){
					String loginname = line.getOptionValue("l");
					String response = serve.lookup(loginname);
					System.out.println(response);
				}

				if(line.hasOption("r")){
					String uuid = line.getOptionValue("r");
					String response = serve.reverseLookup(UUID.fromString(uuid));
					System.out.println(response);
				}

				if(line.hasOption("m")){
					String[] values = line.getOptionValues("m");

					if(values == null || values.length == 1 || values.length == 3){
						printUsage(options);
						System.exit(1);
					}

					String oldloginname = values[0];
					String newloginname = values[1];
					String password = "";
					if(values.length == 4 && (values[2].equals("-p") || values[2].equals("--password"))){
						password = values[3];
					}

					byte[] encryptedPass = getSHA(password);
					String response = serve.modify(oldloginname, newloginname, encryptedPass);
					System.out.println(response);
				}

				if(line.hasOption("d")){
					String[] values = line.getOptionValues("d");

					if(values == null || values.length == 2){
						printUsage(options);
						System.exit(1);
					}

					String loginname = values[0];
					String password = "";
					if(values.length == 3 && (values[1].equals("-p") || values[1].equals("--password"))){
						password = values[2];
					}

					byte[] encryptedPass = getSHA(password);
					String response = serve.delete(loginname,encryptedPass);
					System.out.println(response);
				}

				if(line.hasOption("g")){
					String item = line.getOptionValue("g");
					String response = serve.getInfo(item);
					System.out.println(response);
				}

			} catch (ParseException e) {
				System.out.println(e);
				printUsage(options);
			}

		} catch (Exception e) {
			System.err.println("Sadface");
			e.printStackTrace();
		}
	}

        /**
         * Prints the usage of the main method in this file.
         *
         * @param options - specifies what option you want to return to the user
         */
	public static void printUsage(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp( "ParseTest ", options );
	}

	/**
	 * Encoding with SHA 512
	 *
	 * @param input - text to be encrypted
	 */
	private static byte[] getSHA(String input) throws NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance("SHA-512");
		byte[] bytes = input.getBytes();
		md.reset();
		return md.digest(bytes);
	}
}
