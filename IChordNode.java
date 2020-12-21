import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Vector;

public interface IChordNode extends Remote
{
    void put(String key, byte[] value) throws RemoteException;
    byte[] get(String key) throws RemoteException;
    boolean getStatus(String key) throws RemoteException;
    IChordNode findSuccessor(int key) throws RemoteException;
    int getKey() throws RemoteException;
    IChordNode getPredecessor() throws RemoteException;
    void notifyNode(IChordNode potentialPredecessor) throws RemoteException;
    Vector<Store> getBackup() throws RemoteException;
    void remBackup(String key) throws RemoteException;
    void putBackup(String key, byte[] value) throws RemoteException;
    void updateBackup(String key, byte[] value, boolean complete) throws RemoteException;
}
