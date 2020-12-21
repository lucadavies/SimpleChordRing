//NEXT: just debug the form parser...

import java.io.*;
import java.net.*;

/**
 * @author Barry Porter
 */
enum RequestType {
	GET,
	POST
}

class HTTPHeader {
	String key;
	String value;
}

public class RestKit {
	
	static Web web = new Web();
	
	//this function read and parses a HTTP request from its text format into a HTTPRequest object
	static HTTPRequest parseRequest(InputStream input) throws IOException
		{
		HTTPRequest req = new HTTPRequest();
		
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		
		int b;
		byte last4[] = new byte[4];
		
		while ((b = input.read()) != -1)
			{
			buf.write(b);
			
			last4[0] = last4[1];
			last4[1] = last4[2];
			last4[2] = last4[3];
			last4[3] = (byte) b;
			
			if (last4[0] == '\r' && last4[1] == '\n' && last4[2] == '\r' && last4[3] == '\n')
				{
				break;
				}
			}
		
		if (last4[0] == '\r' && last4[1] == '\n' && last4[2] == '\r' && last4[3] == '\n')
			{
			//format OK
			String data = buf.toString();
			
			String parts[] = data.split("\r\n");
			
			//parse the command line
			String cmd_parts[] = parts[0].split(" ");
			
			if (cmd_parts[0].toLowerCase().equals("get"))
				{
				req.type = RequestType.GET;
				}
				else if (cmd_parts[0].toLowerCase().equals("post"))
				{
				req.type = RequestType.POST;
				}
			
			req.resource = cmd_parts[1];
			
			req.headers = new HTTPHeader[parts.length-1];
			
			//parse the headers
			for (int i = 1; i < parts.length; i++)
				{
				int ndx = parts[i].indexOf(":");
				
				String k = parts[i].substring(0, ndx).trim();
				String v = parts[i].substring(ndx+1).trim();
				
				HTTPHeader nhdr = new HTTPHeader();
				nhdr.key = k.toLowerCase();
				nhdr.value = v;
				
				req.headers[i-1] = nhdr;
				}
			
			return req;
			}
			else
			{
			return null;
			}
		}
	
	static void processStream(Socket s)
		{
		try{
			//get low-level stream objects for input and output
			InputStream input = s.getInputStream();
			OutputStream output = s.getOutputStream();
			
			//wrap our low-level streams in character-based input/output stream objects
			InputStreamReader reader = new InputStreamReader(input);
			PrintWriter writer = new PrintWriter(output, true);
			
			HTTPRequest req = parseRequest(input);
			
			if (req != null)
				{
				if (req.type == RequestType.GET)
					{
					//TODO: just call some function, and use some policy to serve static content...
					System.out.println("received GET request for " + req.resource);
					output.write("HTTP/1.1 200 OK\r\n".getBytes());
					
					web.get(req, output);
					}
					else if (req.type == RequestType.POST)
					{
					//TODO: we need basic form parsing, for both form types
					int plen = Integer.parseInt(req.getHeaderValue("content-length"));
					byte payload[] = new byte[plen];
					input.read(payload, 0, plen);
					
					web.post(req, payload, output);
					}
				}
			}
			catch (IOException e)
			{
			System.out.println("I/O error on socket receive");
			}
		
		try{
			//close the socket
			s.close();
			}
			catch (IOException e)
			{
			//give up...
			}
		}
	
	public static void main(String args[]) throws IOException
		{
		//open master socket
		ServerSocket serverSocket = new ServerSocket(8080);
		
		while (true)
			{
			Socket socket = serverSocket.accept();
			processStream(socket);
			}
		}
	
	}