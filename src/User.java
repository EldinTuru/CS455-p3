import java.io.Serializable;
import java.net.InetAddress;
import java.util.UUID;

public class User implements Serializable
{
    private UUID uuid;
    private InetAddress IPAddress;
    private String createdDate;
    private String realName;
    private byte[] password;
    private static long serialVerisonUID = -4694364532468189507L;

    public User(UUID uuid, InetAddress IPAddress, String createdDate, String realName, byte[] password){
        this.uuid = uuid;
        this.IPAddress = IPAddress;
        this.createdDate = createdDate;
        this.realName = realName;
        this.password = password;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public void setIPAddress(InetAddress IPAddress) {
        this.IPAddress = IPAddress;
    }

    public void setCreatedDate(String createdDate) {
        this.createdDate = createdDate;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public void setPassword(byte[] password) {
        this.password = password;
    }

    public UUID getUuid() {
        return uuid;
    }

    public InetAddress getIPAddress() {
        return IPAddress;
    }

    public String getCreatedDate() {
        return createdDate;
    }

    public String getRealName() {
        return realName;
    }

    public byte[] getPassword() {
        return password;
    }

    public String toString()
    {
        return "UUID: " + getUuid() + "\nIP: " + getIPAddress() + "\ncreated date: " + getCreatedDate() + "\nrealname " + getRealName();
    }
}
