import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

/**
 * @author Barry Porter (base framework)
 * @author Luca Davies (significant modification for application specific usage)
 */
class HTTPRequest
{
    RequestType type;
    String resource;
    HTTPHeader headers[];

    String getHeaderValue(String key)
    {
        for (int i = 0; i < headers.length; i++)
        {
            if (headers[i].key.equals(key))
                return headers[i].value;
        }

        return null;
    }
}

enum JobType
{
    WORD,
    ZIP,
    IMGCONV,
    IMGCOMP
}

public class Web
{

    static int RESPONSE_OK = 200;
    static int BAD_REQUEST = 400;
    static int RESPONSE_NOT_FOUND = 404;
    static int RESPONSE_SERVER_ERROR = 501;

    FormMultipart formParser = new FormMultipart();

    HashSet<String> jobs = new HashSet<String>();
    boolean waitingOnResult = false;
    String refreshFunction;
    int lastDispatchIndex = 0;

    Web()
    {
        try
        {
            FileInputStream f = new FileInputStream("refresh.js");
            refreshFunction = new String(f.readAllBytes());
            f.close();
        }
        catch (IOException e)
        {
            System.out.println("File \"" + "refresh.js" + "\" cannot be found.");
        }
        
    }

    private void sendResponse(OutputStream output, int responseCode, String contentType, byte content[])
    {
        try
        {
            output.write(new String("HTTP/1.1 " + responseCode + "\r\n").getBytes());
            output.write("Server: Kitten Server\r\n".getBytes());
            if (content != null)
                output.write(new String("Content-length: " + content.length + "\r\n").getBytes());
            if (contentType != null)
                output.write(new String("Content-type: " + contentType + "\r\n").getBytes());
            output.write(new String("Connection: close\r\n").getBytes());
            output.write(new String("\r\n").getBytes());

            if (content != null)
                output.write(content);
        }
        catch (SocketException e)
        {
            System.err.println("Socket exception. Likely your browser aborting connection for some unknown reason.");
        }
        catch (IOException e)
        {
            System.err.println("IO error sending response.");
            e.printStackTrace();
        }
    }

    // this function maps GET requests onto functions / code which return HTML pages
    void get(HTTPRequest request, OutputStream output)
    {
        System.out.println("----------------------");
        if (request.resource.equals("/"))
            page_root(output);
        else if (request.resource.equals("/upload"))
        {
            page_upload(output);
        }
        else if (request.resource.contains("/results"))
        {
            if (request.resource.equals("/results"))                        //matches /results
            {
                page_results(output);
            }
            else if (jobs.contains(request.resource.substring(9))) //matches /results/<job filename>
            {
                String key = request.resource.substring(9);
                byte[] response = null;
                JobType jType = JobType.valueOf(key.split("_")[0]);
                try
                {
                    IChordNode node = getChordNode();
                    response = node.get(key);
                }
                catch (RemoteException e)
                {
                    System.err.println("Error conacting Chord ring for key " + key);
                }
                
                if (response != null)
                {
                    if (jType == JobType.WORD)
                    {
                        sendResponse(output, RESPONSE_OK, "text/xml", response);
                    }
                    if (jType == JobType.ZIP)
                    {
                        sendResponse(output, RESPONSE_OK, "attachment", response);
                    }
                    else if (jType == JobType.IMGCOMP || jType == JobType.IMGCONV)
                    {
                        sendResponse(output, RESPONSE_OK, "image/jpeg", response);
                    }
                    else
                    {
                        sendResponse(output, RESPONSE_OK, "text/plain", response);
                    }
                }
                else
                {
                    sendResponse(output, RESPONSE_OK, "text/plain", "Error retrieving results. Try again later.".getBytes());
                }
                
                
            }
            else if (request.resource.substring(9).equals("update"))        //matches /results/update
            {
                String response = getResultsTableBody();
                sendResponse(output, RESPONSE_OK, "text/plain", response.getBytes());
            }
        }
        else if (request.resource.substring(1).equals("tick.png") || request.resource.substring(1).equals("loading.gif"))
        {
            getFile(output, request.resource.substring(1));
        }
        else
        {
            page_404(output);
        }
    }

    // this function maps POST requests onto functions / code which return HTML
    // pages
    void post(HTTPRequest request, byte payload[], OutputStream output)
    {
        if (request.resource.equals("/upload_do"))
        {
            page_upload_do(request, payload, output);
        }
        else 
        {
            page_404(output);
        }
    }

    // example of a simple HTML page
    private void page_root(OutputStream output)
    {
        String response = "";
        response += "<html>";
        response += "<body>";
        response += "<a href=\"upload\">Upload</a>";
        response += "</body>";
        response += "</html>";

        sendResponse(output, RESPONSE_OK, "text/html", response.getBytes());
    }

    // example of a form to fill in, which triggers a POST request when the user
    // clicks submit on the form
    private void page_upload(OutputStream output)
    {
        String response = "";
        response += "<html>";
        response += "<body>";
        response += "<h3>Upload text file to be processed</h3>";
        response += "<form action=\"/upload_do\" method=\"POST\" enctype=\"multipart/form-data\">";
        response += "<input type=\"file\" name=\"content\" required/><br><br>";
        response += "<input type=\"radio\" id=\"word\" name=\"jobType\" value=\"word\" checked>";
        response += "<label for=\"word\">Word Analysis</label><br>";
        response += "<input type=\"radio\" id=\"zip\" name=\"jobType\" value=\"zip\">";
        response += "<label for=\"zip\">Zip File</label><br>";
        response += "<input type=\"radio\" id=\"imgcomp\" name=\"jobType\" value=\"imgcomp\">";
        response += "<label for=\"imgcomp\">Compress JPG</label><br>";
        response += "<input type=\"radio\" id=\"imgconv\" name=\"jobType\" value=\"imgconv\">";
        response += "<label for=\"imgconv\">Convert PNG to JPG</label><br><br>";
        response += "<input type=\"submit\" name=\"submit\"/>";
        response += "</form>";
        response += "</body>";
        response += "</html>";

        sendResponse(output, RESPONSE_OK, "text/html", response.getBytes());
    }

    private void page_upload_do(HTTPRequest request, byte[] payload, OutputStream output)
    {
        // FormMultipart
        if (request.getHeaderValue("content-type") != null && request.getHeaderValue("content-type").startsWith("multipart/form-data"))
        {
            FormData data = formParser.getFormData(request.getHeaderValue("content-type"), payload);
            String filename = null;
            JobType jType = null;
            byte[] content = null;

            for (int i = 0; i < data.fields.length; i++)
            {
                System.out.println("field: " + data.fields[i].name);
                if (data.fields[i].name.equals("content"))
                {
                    filename = ((FileFormField) data.fields[i]).filename;
                    System.out.println(" -- filename: " + filename);
                    content = ((FileFormField) data.fields[i]).content;
                }
                else if (data.fields[i].name.equals("jobtype"))
                {
                    String recvJType = new String(data.fields[i].content);
                    if (recvJType.equals("word"))
                    {
                        jType = JobType.WORD;
                    }
                    else if (recvJType.equals("zip"))
                    {
                        jType = JobType.ZIP;
                    }
                    else if (recvJType.equals("imgcomp"))
                    {
                        jType = JobType.IMGCOMP;
                    }
                    else if (recvJType.equals("imgconv"))
                    {
                        jType = JobType.IMGCONV;
                    }
                    else
                    {
                        jType = JobType.WORD;
                    }
                }                
            }
            dispatchJob(filename, jType, content);

            String html = "";
            html += "<html>";
            html += "<body>";
            html += "<script>";
            html += "setTimeout(function(){ location.replace(\"results\"); }, 500);";
            html += "</script>";
            html += "</body>";
            html += "</html>";
            sendResponse(output, RESPONSE_OK, "text/html", html.getBytes());
        }
        else
        {
            sendResponse(output, RESPONSE_SERVER_ERROR, null, null);
        }
    }

    private void page_results(OutputStream output)
    {
        String html = "";
        html += "<html>";
        html += "<head>";
        html += "<script>";
        html += refreshFunction;
        html += "</script>";
        html += "<style>";
        html += "table {border-collapse: collapse;}";
        html += "td, th {border: 1px solid #dddddd;text-align: left;padding: 8px;}";
        html += "tr:nth-child(even) {background-color: #dddddd;}";
        html += "</style>";
        html += "</head>";
        html += "<body>";
        html += "<h3>Table of results:</h3>";
        html += "<table id=\"tabResults\">";
        html += getResultsTableBody();
        html += "</table>";
        html += "</body>";
        html += "</html>";
        sendResponse(output, RESPONSE_OK, "text/html", html.getBytes());
    }

    private void page_listFiles(OutputStream output)
    {
        String html = "<html>";
        html += "<table><tbody><tr><th>Name</th><th></th></tr>";
        File f = new File(System.getProperty("user.dir"));
        for (String fi : f.list())
        {
            html += "<tr>";
            html += "<td><div><a href=\"" + fi + "\" download=\"" + fi + "\">" + fi + "</a></div></td>";
            html += "<td><div><a href=\"" + fi + ".zip\" download=\"" + fi + ".zip\"> |  Zip</a></div></td>";
            html += "</tr>";
        }
        html += "</tbody></table></html>";
        sendResponse(output, RESPONSE_OK, "text/html", html.getBytes());
    }

    private void page_listFilesXML(OutputStream output)
    {
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
		xml += "<directory>";
		File f = new File(System.getProperty("user.dir"));
        for (String fi : f.list())
        {
			xml += "<file>\n";
			xml += "\t<name>" + fi + "</name>\n";
			xml += "\t<size>" + fi.length() + "</size>\n";
			xml += "</file>\n";
		}
		xml += "</directory>";
        sendResponse(output, RESPONSE_OK, "text/xml", xml.getBytes());
    }

    private void page_404(OutputStream output)
    {
        String html = "";
        html += "<html>";
        html += "<body>";
        html += "<h1>404: Page not found</h3>";
        html += "<a href=\"/\">Return to root</a>";
        html += "</body>";
        html += "</html>";
        
        sendResponse(output, RESPONSE_NOT_FOUND, "text/html", html.getBytes());
    }

    private void dispatchJob(String filename, JobType jType, byte[] data)
    {
        String cleanedFilename = filename.replace("_", "-").replace(" ", "-");
        waitingOnResult = true;
        jobs.add(jType.toString() + "_" + cleanedFilename);
        try
        {
            IChordNode node = getChordNode();
            node.put(jType.toString() + "_" + cleanedFilename, data);
        }
        catch (RemoteException e)
        {
            System.err.println("Error dispatching job to Chord ring.");
        }
        catch (NullPointerException e)
        {
            System.err.println("Error dispatching job: could not contact Chord ring.");
        }
    }

    private IChordNode getChordNode() throws RemoteException
    {
        try
        {
            Registry reg = LocateRegistry.getRegistry();
            if (lastDispatchIndex >= reg.list().length)
            {
                lastDispatchIndex = 0;
            }
            return (IChordNode) reg.lookup(reg.list()[lastDispatchIndex++]);
        }
        catch (NotBoundException e)
        {
            System.err.println("Selected node could not be found in registry.");
        }
        return null;
    }

    private String getResultsTableBody()
    {
        String html = "";
        html += "<tbody><tr><th>Name</th><th>Type</th><th>Status</th><th>Results</th></tr>";
        for (String s : jobs)
        {
            JobType jType = JobType.valueOf(s.split("_")[0]);
            String name = s.split("_")[1];
            boolean isReady = false;

            try
            {
                isReady = getChordNode().getStatus(s);
            }
            catch (RemoteException e)
            {
                System.err.println("Could not contact Chord ring to check task status.");
            }

            html += "<tr>";
            html += "<td>" + name + "</td>";
            html += "<td>" + jType.toString() + "</td>";
            if (!isReady)
            {
                html += "<td style=\"text-align:center\"><img src=\"loading.gif\" alt=\"Loading\" height=\"20\"></td>";
                html += "<td style=\"text-align:center\">...</td>";
            }
            else
            {
                html += "<td style=\"text-align:center\"><img src=\"tick.png\" alt=\"Complete\" height=\"20\"></td>";
                html += "<td style=\"text-align:center\">";
                if (jType == JobType.WORD)
                {
                    html += "<a href=\"results/" + s + "\">View</a>&nbsp";
                    html += "<a href=\"results/" + s + "\" download=\"" + s + ".xml\">Download</a>";
                }
                else if (jType == JobType.ZIP)
                {
                    html += "<a href=\"results/" + s + "\" download=\"" + s + ".zip\">Download</a>";
                }
                else if (jType == JobType.IMGCOMP)
                {
                    html += "<a href=\"results/" + s + "\">View</a>&nbsp";
                    html += "<a href=\"results/" + s + "\" download=\"" + s + "\">Download</a>";
                }
                else if (jType == JobType.IMGCONV)
                {
                    html += "<a href=\"results/" + s + "\">View</a>&nbsp";
                    html += "<a href=\"results/" + s + "\" download=\"" + s + ".jpg\">Download</a>";
                }
                else
                {
                    html += "<a href=\"results/" + s + "\" download=\"" + s + ".txt\">Download</a>";
                }
                html += "</td>";
            }
            html += "</tr>";
        }
        html += "</tbody>";
        return html;
    }

    private void getFile(OutputStream output, String filename)
    {
        byte[] data = null;
        try 
        {
            FileInputStream f = new FileInputStream(filename);
            data = f.readAllBytes();
            f.close();
            sendResponse(output, RESPONSE_OK, "attachment", data);
        }
        catch (IOException e)
        {
            System.out.println("File \"" + filename + "\" cannot be found.");
        }
    }

    private void getFileZip(OutputStream output, String file)
    {
        System.out.println(file);
        try
        {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ZipOutputStream zo = new ZipOutputStream(bo);
            File plain = new File(file);
            FileInputStream fi = new FileInputStream(plain);
            ZipEntry ze = new ZipEntry(plain.getName());
            zo.putNextEntry(ze);
            byte[] bytes = new byte[1024];
            int length;
            while ((length = fi.read(bytes)) >= 0)
            {
                zo.write(bytes, 0, length);
            }
            zo.close();
            fi.close();
            bo.close();
            sendResponse(output, RESPONSE_OK, "attachment", bo.toByteArray());
        }
        
        catch (FileNotFoundException e)
        {
            System.out.println("File \"" + file + "\" cannot be found.");
        }
        catch (ZipException e)
        {
            System.out.println("Error zipping file \"" + file + "\".");
        }
        catch (IOException e)
        {
            System.out.println("File \"" + file + "\" cannot be found.");
        }
    }

}