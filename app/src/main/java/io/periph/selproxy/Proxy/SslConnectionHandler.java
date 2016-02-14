package io.periph.selproxy.Proxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by Periph on 31/08/2015.
 *
 * This class handles HTTP transactions over TLS.
 *
 * TODO - SSL2 has a different ClientHello Message structure than SSL3/TLS 1.0.
 * TODO - Just test if it is possible to simply forward traffic between sockets and enable SSL to work without any interception.
 * TODO - Doing this but only sending one record at a time.
 *
 *
 * TODO - Can probably move all of this into the original ConnectionHandler class...because it doesn't really need anything extra.
 *
 *
 *
 * First Test - Simply forward TLS encrypted traffic between a client and server (Single TLSRecords at a time).
 *
 *
 */
public class SslConnectionHandler extends Thread {

    ConnectionHandler ch = null;

    InputStream fromClient = null;
    InputStream fromServer = null;

    OutputStream toClient = null;
    OutputStream toServer = null;

    SSLStreamHandler streamToServer;
    SSLStreamHandler streamToClient;

    private boolean directionToServer = false;

    public SslConnectionHandler(InputStream fc, InputStream fs, OutputStream tc, OutputStream ts, ConnectionHandler ch) {
        this.ch = ch;
        this.fromClient = fc;
        this.fromServer = fs;
        this.toClient = tc;
        this.toServer = ts;
    }
    public void start(){
        //Send a 200 OK back to the client.
        connectionEstablished();
        //Client ---> Server.
        streamToServer = new SSLStreamHandler(this.fromClient, this.toServer, ch, true);
        //Server ---> Client
        streamToClient = new SSLStreamHandler(this.fromServer, this.toClient, ch, false);
        //separate thread dealing with each directional stream....
        Thread t1 = new Thread(streamToServer);
        Thread t2 = new Thread(streamToClient);
        t1.start();
        t2.start();
        //Don't close streams early before both sides of the connection have finished.
        while(t1.isAlive() || t2.isAlive()) {
            try {
                sleep(5000);
            } catch(InterruptedException ie) {
                ie.printStackTrace();
            }
        }
        closeStreams();
    }



    private void connectionEstablished(){
        try {
            toClient.write("HTTP/1.1 OK Connection Established".getBytes());
            toClient.write("\r\n".getBytes());
            toClient.write("Proxy-agent: SelProxyv0.1".getBytes());
            toClient.write("\r\n".getBytes());
            toClient.write("\r\n".getBytes());
            toClient.flush();
        } catch(IOException ioe) {
            return;
        }
    }

    private void closeStreams() {
        try {
            this.toClient.close();
            this.toServer.close();
            this.fromClient.close();
            this.fromServer.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
