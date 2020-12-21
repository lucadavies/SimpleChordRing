import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class taskTest {

    static final String serverURL = "http://localhost:8080/uploadResults";

    private static String processData(byte[] data)
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
        r += "<result>";
        r += "\t<wordCount>" + wordCount + "</wordCount>";
        r += "\t<freqWord>";
        r += "\t\t<word>" + max.getKey() + "</word>";
        r += "\t\t<freq>" + max.getValue().val + "</freq>";
        r += "\t</freqWord>";
        r += "\t<avgWordLen>" + ((float)totLen / wordCount) + "</avgWordLen>";
        r += "</result>";
        return r;
    }

    static void postResult(String res)
    {
        String serverResponse = "";
        int attempts = 0;
        do
        {
            try
            {
                URL url = new URL(serverURL);
                HttpURLConnection con = (HttpURLConnection)url.openConnection();
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
                con.setRequestProperty("Accept", "text/plain; charset=UTF-8");
                con.setDoOutput(true);
                try (OutputStream os = con.getOutputStream())
                {
                    byte[] data = res.getBytes();
                    os.write(data, 0, data.length);
                }

                //wait for good response...

                try(BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), "utf-8")))
                {
                    StringBuilder response = new StringBuilder();
                    String responseLine = null;
                    while ((responseLine = br.readLine()) != null)
                    {
                        response.append(responseLine.trim());
                    }
                    serverResponse = response.toString();
                }
            }
            catch (MalformedURLException e)
            {
                e.printStackTrace();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            attempts++;
        } while (!serverResponse.equals("Results received.") && attempts <= 3);
    }

    public static void main(String[] args)
    {
        byte[] data = null;
        String r = null;
        try 
        {
            FileInputStream f = new FileInputStream("Cinderella.txt");
            data = f.readAllBytes();
            f.close();
            r = processData(data);
        }
        catch (IOException e)
        {
            System.out.println("File \"" + "\" cannot be found.");
        }

        System.out.println(r);
        //postResult(r);
    }
}
