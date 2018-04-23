import java.net.InetAddress;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

public interface Service extends Remote {
	String createLogin(String loginname, String realname, byte[] password, InetAddress ip) throws RemoteException;
	String lookup(String loginname) throws RemoteException;
	String reverseLookup(UUID uuid) throws RemoteException;
	String modify(String oldname, String newname, byte[] password) throws RemoteException;
	String delete(String loginname, byte[] password) throws RemoteException;
	String getInfo(String item) throws RemoteException;
}
