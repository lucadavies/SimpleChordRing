import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

class Finger
{
    public int key;
    public IChordNode node;
}

class Store implements Serializable
{
    String key;
    byte[] value;
    boolean complete;
}

class Counter
{
    int val = 0;

    void increment(){ val++; }
}

public class ChordNode implements Runnable, IChordNode
{

    private static final int KEY_BITS = 8;

    private IChordNode successor;
    private IChordNode predecessor;

    // my finger table; note that all "node" entries will initially be "null"; your
    // code should handle this
    private int fingerTableLength;
    private Finger finger[];
    private int nextFingerFix;

    private Vector<Store> dataStore = new Vector<Store>();
    private Vector<Store> predecessorBackup = new Vector<Store>();

    // note: you should always use getKey() to get a node's key; this will make the
    // transition to RMI easier
    private int myKey;

    ChordNode(String myKeyString, String joinString)
    {
        myKey = hash(myKeyString);
        System.out.println("Starting node \"" + myKeyString + "\" with id " + myKey);
        successor = this;  //init

        // initialise finger table with entries to this node. Will be fixed as ring grows
        finger = new Finger[KEY_BITS];
        for (int i = 0; i < KEY_BITS; i++)
        {
            Finger f = new Finger();
            f.key = getKey();
            f.node = this;
            finger[i] = f;
        }
        fingerTableLength = KEY_BITS;

        //register with RMI
        Registry reg = null;
        try
        {
            IChordNode stub = (IChordNode) UnicastRemoteObject.exportObject(this, 0);
            reg = LocateRegistry.getRegistry();
            reg.rebind(myKeyString, stub);
            
            if (joinString != null) //join if atNode provided
            {
                IChordNode joinNodeI = (IChordNode) reg.lookup(joinString);
                join(joinNodeI);
            }
            else
            {
                System.out.println("No join node specified. Starting new Chord ring.");
            }

            // start up the periodic maintenance thread
            new Thread(this).start();
        }
        catch (RemoteException e)
        {
            System.err.println("Error acquiring RMI Registry.");
            e.printStackTrace();
        }
        catch (NotBoundException e)
        {
            System.err.println("RMI binding \"" + joinString + "\" not found.");
            try
            {
                reg.unbind(myKeyString);
                System.err.println("Failed node unbound from registry.");
            }
            catch (Exception ex) //Not good.
            {
                System.err.println("Error unbinding node.");
                e.printStackTrace();
            }
            
        }
        
    }

    // -- topology management functions --
    private void join(IChordNode atNode) throws RemoteException
    {
        predecessor = null;  //init
        successor = atNode.findSuccessor(getKey()); //init
    }

//#region -- API Functions --
    public void put(String key, byte[] value) throws RemoteException
    {
        // find the node that should hold this key and add the key and value to that
        // node's local store

        int keyHash = hash(key);
        Store s = new Store();
        s.key = key;
        s.value = value;
        s.complete = false;

        if (isInHalfOpenRangeR(keyHash, predecessor.getKey(), getKey())) //> predecssor key, <= myKey
        { //therefore, store here
            dataStore.add(s);
            successor.putBackup(key, value); //tell my successor that I (it's predecessor) have this key/value
        }
        else
        {
            IChordNode n = closestPrecedingNode(keyHash).findSuccessor(keyHash);
            if (n != this) //prevents a recursive call when the key doesn't belong here but there's no other nodes
            {
                n.put(key, value);
            }
        }
    }

    public void putBackup(String key, byte[] value) throws RemoteException
    {
        Store s = new Store();
        s.key = key;
        s.value = value;
        s.complete = false;
        predecessorBackup.add(s);
    }

    public byte[] get(String key) throws RemoteException
    {
        // find the node that should hold this key, request the corresponding value from
        // that node's local store, and return it
        if (isInHalfOpenRangeR(hash(key), predecessor.getKey(), getKey())) //>= myKey, less than succesorKey
        { //therefore is stored here
            for (Store s : dataStore)
            {
                if (s.key.equals(key))
                {
                    return s.value;
                }
            }
        }
        return closestPrecedingNode(hash(key)).findSuccessor(hash(key)).get(key);
    }

    public boolean getStatus(String key) throws RemoteException
    {
        // find the node that should hold this key, request the corresponding value from
        // that node's local store, and return it
        if (isInHalfOpenRangeR(hash(key), predecessor.getKey(), getKey())) //>= myKey, less than succesorKey
        { //therefore is stored here
            for (Store s : dataStore)
            {
                if (s.key.equals(key))
                {
                    return s.complete;
                }
            }
        }
        return closestPrecedingNode(hash(key)).findSuccessor(hash(key)).getStatus(key);
    }
//#endregion

//#region -- State Utilities --
    public int getKey()
    {
        return myKey;
    }

    public IChordNode getPredecessor()
    {
        return predecessor;
    }

    public Vector<Store> getBackup() throws RemoteException
    {
        return dataStore;
    }

    public void remBackup(String key) throws RemoteException
    {
        predecessorBackup.removeIf(s -> (s.key.equals(key)));
    }

    public void updateBackup(String key, byte[] value, boolean complete) throws RemoteException
    {
        for (Store s : predecessorBackup)
        {
            if (s.key.equals(key))
            {
                s.value = value;
                s.complete = complete;
                break;
            }
        }
    }

//#endregion

//#region -- Utility unctions --
    public IChordNode findSuccessor(int key) throws RemoteException
    {
        if (successor == this || isInHalfOpenRangeR(key, getKey(), successor.getKey())) //ring not setup fully: successor == this base case
        {
            return successor;
        }
        else
        {
            IChordNode n0 = closestPrecedingNode(key);
            return (n0 == this ? this : n0.findSuccessor(key)); //ring not setup fully: predecessor == this base case
        }
    }

    private IChordNode closestPrecedingNode(int key)
    {
        for (int i = fingerTableLength - 1; i >= 0 && i < fingerTableLength; i--)
        {
            if (isInClosedRange(finger[i].key, getKey(), key))
            {
                return finger[i].node;
            }
        }
        return this;
    }

    // this function converts a string "s" to a key that can be used with the DHT's
    // API functions
    private int hash(String s)
    {
        int hash = 0;

        for (int i = 0; i < s.length(); i++)
            hash = hash * 31 + (int) s.charAt(i);

        if (hash < 0)
            hash = hash * -1;

        return hash % ((int) Math.pow(2, KEY_BITS));
    }

    //#region Range functions
    // -- range check functions; they deal with the added complexity of range wraps
    // --
    // x is in [a,b] ?
    private boolean isInOpenRange(int key, int a, int b)
    {
        if (b > a)
            return key >= a && key <= b;
        else
            return key >= a || key <= b;
    }

    // x is in (a,b) ?
    private boolean isInClosedRange(int key, int a, int b)
    {
        if (b > a)
            return key > a && key < b;
        else
            return key > a || key < b;
    }

    // x is in [a,b) ? - a <= x < b
    private boolean isInHalfOpenRangeL(int key, int a, int b)
    {
        if (b > a)
            return key >= a && key < b;
        else
            return key >= a || key < b;
    }

    // x is in (a,b] ? - a < x <= b
    private boolean isInHalfOpenRangeR(int key, int a, int b)
    {
        if (b > a)
            return key > a && key <= b;
        else
            return key > a || key <= b;
    }
    //#endregion
 //#endregion
 
//#region -- Maintenance --
    // -- maintenance --
    public void notifyNode(IChordNode potentialPredecessor) throws RemoteException
    {
        if (predecessor == null || isInClosedRange(potentialPredecessor.getKey(), predecessor.getKey(), getKey()))
        {
            predecessor = potentialPredecessor; //stabalise
            predecessorBackup = predecessor.getBackup(); //passed via RMI -> passed by val -> effective deep copy
        }
    }

    private void stabilise()
    {
        try
        {
            IChordNode x = successor.getPredecessor();
            if (x != null && isInClosedRange(x.getKey(), this.getKey(), successor.getKey()))
            {
                //if successor's predecessor is between my key and successor key, make successor's predecessor my successor.
                // (this -> new node -> successor) becomes (this -> successor -> old successor)
                successor = x;  //stabilise
            }
            successor.notifyNode(this);
        }
        catch (RemoteException e)
        {
            System.err.println("Successor missing.");
            successor = this;   //successor dead
        }
    }

    private void fixFingers()
    {
        if (nextFingerFix >= KEY_BITS)
        {
            nextFingerFix = 0;
        }
        try
        {
            IChordNode n = findSuccessor(getKey() + (int)Math.pow(2, nextFingerFix));
            Finger f = new Finger();
            f.key = n.getKey();
            f.node = n;
            finger[nextFingerFix] = f;
            nextFingerFix += 1;
        }
        catch (RemoteException e)
        {
            System.err.println("Finger node missing.");
            Finger f = new Finger();
            f.key = getKey();
            f.node = this;
            finger[nextFingerFix - 1] = f;
        }
    }

    private void checkPredecessor()
    {
        try
        {
            predecessor.getKey();
        }
        catch (RemoteException e)
        {
            System.out.println("Predecessor missing.");
            predecessor = null; //predeccesor dead
            for (Store s : predecessorBackup) //take on dead node's keys
            {
                dataStore.add(s);
                try
                {
                    successor.putBackup(s.key, s.value); //tell my successor that I (it's predecessor) have this key/value
                }
                catch (RemoteException ex)
                {
                    System.err.println("Unable to back up value to successor.");
                }
                
                //don't put in predecessor backup as it is currently null, on reacquiring a predecessor, it will request a backup
            }
            predecessorBackup.clear();
        }
        catch (NullPointerException e)
        {
            System.out.println("Reacquiring predecessor...");
        }
    }

    private void checkDataMoveDown() throws RemoteException
    {
        // if I'm storing data that my current predecessor should be holding, move it
        if (predecessor != null)
        {
            Store s;
            for (int i = 0; i < dataStore.size(); i++)
            {
                s = dataStore.get(i);
                if (!isInHalfOpenRangeR(hash(s.key), predecessor.getKey(), getKey())) // If key is in wrong place
                {
                    IChordNode n = closestPrecedingNode(hash(s.key));
                    n.put(s.key, s.value);
                    successor.remBackup(s.key);
                    dataStore.remove(i);
                }
            }
        }
    }
    
//#endregion

//#region -- Data Processing --

    private void doWork() throws RemoteException
    {
        Store s;
        byte[] result;
        JobType jType;
        for (int i = 0; i < dataStore.size(); i++)
        {
            s = dataStore.get(i);
            if (!s.complete)
            {
                try
                {
                    jType = JobType.valueOf(s.key.split("_")[0]);
                }
                catch (IllegalArgumentException e)
                {
                    System.err.println("Unknown job typed requested. Performing word analysis instead.");
                    jType = JobType.WORD;
                }
    
                if (jType == JobType.ZIP)
                {
                    result = processZipFile(s.value, s.key.split("_")[1]);
                }
                else if (jType == JobType.IMGCOMP)
                {
                    result = processImageCompress(s.value, s.key.split("_")[1]);
                }
                else if (jType == JobType.IMGCONV)
                {
                    result = processImageConvert(s.value, s.key.split("_")[1]);
                }
                else
                {
                    result = processWordAnalysis(s.value);
                }

                s.value = result;
                s.complete = true;
                successor.updateBackup(s.key, result, true);
            }
        }
    }

    private byte[] processWordAnalysis(byte[] data)
    {
        String text = new String(data);
        HashMap<String, Counter> words = new HashMap<String, Counter>();
        int wordCount = 0;
        int totLen = 0;
        
        Pattern pat = Pattern.compile("([a-zA-Z_0-9]|-|')+");
        Matcher mat = pat.matcher(text);
        String w = "";
        while (mat.find())
        {
            w = mat.group();
            if (!words.containsKey(w))
            {
                words.put(w, new Counter());
            }
            words.get(w).increment();
            totLen += w.length();
            wordCount++;
        }

        Map.Entry<String, Counter> max = null;
        for (Map.Entry<String, Counter> e : words.entrySet())
        {
            if (max == null || e.getValue().val > max.getValue().val)
            {
                max = e;
            }
        }

        String r = "";
        r += "<result>\n";
        r += "\t<wordCount>" + wordCount + "</wordCount>\n";
        r += "\t<freqWord>\n";
        r += "\t\t<word>" + max.getKey() + "</word>\n";
        r += "\t\t<freq>" + max.getValue().val + "</freq>\n";
        r += "\t</freqWord>\n";
        r += "\t<avgWordLen>" + ((float)totLen / wordCount) + "</avgWordLen>\n";
        r += "</result>\n";
        return r.getBytes();
    }

    private byte[] processZipFile(byte[] data, String filename)
    {
        try
        {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ZipOutputStream zo = new ZipOutputStream(bo);
            ZipEntry ze = new ZipEntry(filename);
            ze.setSize(data.length);
            zo.putNextEntry(ze);
            zo.write(data);
            zo.closeEntry();
            zo.close();
            bo.close();
            return bo.toByteArray();
        }
        catch (ZipException e)
        {
            System.out.println("Error zipping file \"" + filename + "\".");
            return null;
        }
        catch (IOException e)
        {
            System.err.println("IO error writing zip file: \"" + filename + "\"");
            return null;
        }
    }

    private byte[] processImageCompress(byte[] data, String filename)
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try
        {
            ByteArrayInputStream in = new ByteArrayInputStream(data);
            ImageOutputStream iOut = ImageIO.createImageOutputStream(out);
            BufferedImage iIn = ImageIO.read(in);
            
            ImageWriter jpg = ImageIO.getImageWritersByFormatName("jpg").next();
            ImageWriteParam jpgParams = jpg.getDefaultWriteParam();
            jpgParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            jpgParams.setCompressionQuality(0.25f); //compress to 25% of original quality

            jpg.setOutput(iOut);
            jpg.write(null, new IIOImage(iIn, null, null), jpgParams);
            jpg.dispose();
            System.out.println("img data output len: " + out.toByteArray().length);
        }
        catch (IllegalArgumentException e)
        {
            System.err.println("Could not compress image, \"" + filename + "\" it not a valid image.");
            return null;
        }
        catch (IOException e)
        {
            System.err.println("Could not compress image \"" + filename + "\".");
            return null;
        }
        return out.toByteArray();
    }

    private byte[] processImageConvert(byte[] data, String filename)
    {
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try
        {
            BufferedImage iIn = ImageIO.read(in);
            BufferedImage iOut = new BufferedImage(iIn.getWidth(), iIn.getHeight(), BufferedImage.TYPE_INT_RGB);
            iOut.createGraphics().drawImage(iIn, 0, 0, Color.WHITE, null);
            if (ImageIO.write(iOut, "jpg", out))
            {
                System.out.println("Image \"" + filename + "\" converted successfully.");
                return out.toByteArray();
            }
            else
            {
                System.err.println("Error converting image \"" + filename + "\": failed.");
                return null;
            }
        }
        catch (IllegalArgumentException e)
        {
            System.err.println("Could not compress image, \"" + filename + "\" it not a valid image.");
            return null;
        }
        catch (NullPointerException e)
        {
            System.err.println("Could not read image, \"" + filename + "\" it not a valid image.");
            return null;
        }
        catch (IOException e)
        {
            System.err.println("Could not convert image \"" + filename + "\".");
            return null;
        }
    }

//#endregion

//#region -- Debug Printing

    private void printBackup()
    {
        String pbu = "";
        for (Store v : predecessorBackup)
        {
            pbu += "[" + v.key + "(" + hash(v.key) + ") : " + v.value + ", " + v.complete + "]";
            if (!(predecessorBackup.indexOf(v) == predecessorBackup.size() - 1))
                pbu += ",\n";
        }
        System.out.println("Predecessor backup:\n[" + pbu + "]");
    }

    private void printNodeStatus()
    {
        System.out.print("P:");
        try
        {
            System.out.print(predecessor == null ? "null" : predecessor.getKey());
        } catch (RemoteException e) { System.out.print("xxx"); }
        System.out.print(" -> " + getKey() + " -> S:");
        try
        {
            System.out.print(successor == null ? "null" : successor.getKey());
        } catch (RemoteException e) { System.out.print("xxx"); }
        System.out.println();
    }

    private void printDataStore()
    {
        String ds = "";
        for (Store v : dataStore)
        {
            ds += "[" + v.key + "(" + hash(v.key) + ") : " + v.value + ", " + v.complete + "]";
            if (!(dataStore.indexOf(v) == dataStore.size() - 1))
                 ds += ",\n";
        }
        System.out.println("Data store:\n[" + ds + "]");
    }

    private void printFingerTable()
    {
        ArrayList<Integer> ft = new ArrayList<Integer>();
        for (Finger f : finger)
        {
            ft.add(f.key);
        }
        System.out.println("Finger table:\n" + ft.toString());
    }

    private void printInfo()
    {
        printNodeStatus();
        printDataStore();
        printBackup();
        printFingerTable();
    }

    //#endregion

    /**
     * Chord ring maintenance and execution loop. The only exceptions caught in this method are those that are
     * unexpected. Those caught in the methods called <i>from</i>> here are necesasry for maintenance of the ring and
     * are handled appropriately
     */
    public void run()
    {
        while (true)
        {
            try
            {
                printInfo();
                System.out.println("---------------waiting...");
                Thread.sleep(1000);
            }
            catch (InterruptedException e)
            {
                System.err.println("Interrupted");
            }

            try
            {
                stabilise();
            } catch (Exception e)
            {
                System.err.println("Unexpected error in stabilise().");
            }

            try
            {
                fixFingers();
            }
            catch (Exception e)
            {
                System.err.println("Unexpected error in fixFingers().");
            }

            try
            {
                checkPredecessor();
            }
            catch (Exception e)
            {
                System.err.println("Unexpected error in checkPredecessor().");
            }

            try
            {
                checkDataMoveDown();
            }
            catch (Exception e)
            {
                System.err.println("Unexpected error in checkDataMoveDown().");
            }

            try
            {
                doWork();
            }
            catch (RemoteException e)
            {
                System.err.println("Unexpected error in doWork(). Could not update backups.");
            }
        }
    }

    /**
     * Usage: "java ChordNode keyString [joinNodeKey]"
     * 
     * Starts a new ChordNode with key <i>keyString</i>. If <i>joinNodeKey</i> is specified, attempts to join existing
     * Chord ring at the node with the key <i>joinNodeKey</i>. If no <i>joinNodeKey</i> is specified, a new Chord ring
     * is started.
     * @param args
     */
    public static void main(String args[])
    {
        if  (args.length == 1)
        {
            new ChordNode(args[0], null);
        }
        else if (args.length == 2)
        {
            new ChordNode(args[0], args[1]);
        }
        else
        {
            System.err.println("Usage: java ChordNode key [joinNodeKey]");
        
        }
    }

}