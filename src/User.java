import java.io.Serializable;
import java.net.InetAddress;
import java.util.UUID;

public class User implements Serializable
{
    private UUID uuid;
    private String IPAddress;
    private String createdDate;
    private String realName;
    private byte[] password;
    private static long serialVerisonUID = -4694364532468189507L;

    public User(UUID uuid, InetAddress IPAddress, String createdDate, String realName, byte[] password){
        this.uuid = uuid;
        this.IPAddress = IPAddress.toString();
        this.createdDate = createdDate;
        this.realName = realName;
        this.password = password;
    }

    public User(UUID uuid, String IPAddress, String createdDate, String realName, byte[] password){
        this.uuid = uuid;
        this.IPAddress = IPAddress.toString();
        this.createdDate = createdDate;
        this.realName = realName;
        this.password = password;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public void setIPAddress(InetAddress IPAddress) {
        this.IPAddress = IPAddress.toString();
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

    public String getIPAddress() {
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

    public String getJSON()
    {
        return getUuid().toString()+"##"+getIPAddress()+"##"+getCreatedDate()+"##"+getRealName()+"##"+new String(getPassword());
    }
}
