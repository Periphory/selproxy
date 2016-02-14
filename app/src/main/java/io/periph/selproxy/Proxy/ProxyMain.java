package io.periph.selproxy.Proxy;

import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by Periph on 18/08/2015.
 *
 * A basic HTTP/HTTPS transparent proxy.
 *
 * TODO - Add functionality to test for an internet connection.
 * TODO - HTTP Proxy does not deal with 304 If-modified-since properly.
 *
 *
 */
public class ProxyMain implements Runnable {

    public static String TAG = "SELPROXY";

    private String listenAddress = "127.0.0.1";
    private int listenPort = 8080;
    private int listenSocketBacklog = 10;
    private boolean proxyOn = true;

    private ServerSocket listenerSocket;

    public void run() {
        //Entry point into proxy
        //Spawn a server socket to listen for incoming connections from the client.
        try {
                listenerSocket = new ServerSocket(listenPort, listenSocketBacklog,
                        InetAddress.getByName(listenAddress));
                Log.i(TAG, "Proxy listening on " + listenAddress + ":" + listenPort);
                while(proxyOn){
                    Socket clientSock = listenerSocket.accept();
                    new Thread(new ConnectionHandler(clientSock)).start();
                }

        } catch (IOException e) {
            Log.e(TAG, "Error setting up server socket " + e.getMessage());
            e.printStackTrace();
        }
        Log.i(TAG, "ProxyMain run End.");
    }
}
