import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Client
{
    public Client(String key, String nodeStringKey, boolean isGet)
    {
        try
        {
            Registry reg = LocateRegistry.getRegistry();
            IChordNode node = (IChordNode) reg.lookup(nodeStringKey);
            if (isGet)
            {
                String d = new String(node.get(key));
                System.out.println(d);
            }
            else
            {
                String d = "This is " + key + "'s data.";
                node.put(key, d.getBytes());
            }    
        }
        catch (RemoteException e)
        {
            System.err.println(e.getMessage());
        }
        catch (NotBoundException e)
        {
            System.err.println("Node \"" + nodeStringKey + "\" could not be found.");

        }
    }

    public static void main(String[] args)
    {
        if (args.length == 3)
        {
            if (args[0].equals("get"))
            {
                new Client(args[1], args[2], true);
            }
            else
            {
                new Client(args[1], args[2], false);
            }
        }
        else
        {
            System.err.println("Usage: java Client get|put key targetNode");
        }
    }
}