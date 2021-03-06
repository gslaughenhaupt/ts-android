package com.door43.translationstudio.network;

import android.content.Context;
import android.os.Handler;

import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * This class operates as a client on the local network.
 * The client will listen for UDP broadcast messages from servers.
 * The client can connect to a server's TCP port as detailed in the UDP message.
 * Once the client and server are connected they may communicate by passing strings back and forth
 * using the writeTo() method.
 */
public class Client extends Service {
    private final int mClientUDPPort;
    private final OnClientEventListener mListener;
    private boolean mIsRunning = false;
    private final int mCleanupFrequency = 5000;
    private final long mServerTimeout = 5000; // servers will be considered as lost after this period of inactivity
    private Thread mBroadcastListenerThread;
    private Thread mCleanupThread;
    private Map<String, Connection> mServerConnections = new HashMap<String, Connection>();
    private Handler mHandler;

    public Client(Context context, int clientUDPPort, OnClientEventListener listener) {
        super(context);
        mClientUDPPort = clientUDPPort;
        mListener = listener;
    }

    /**
     * Begins listening for server broadcasts and sets up some house keeping utilties
     */
    @Override
    public void start(final String serviceName, Handler handle) {
        if(mIsRunning) return;
        mIsRunning = true;

        mHandler = handle;
        mBroadcastListenerThread = new Thread(new BroadcastListenerRunnable(mClientUDPPort, new BroadcastListenerRunnable.OnBroadcastListenerEventListener() {
            @Override
            public void onError(Exception e) {
                mListener.onError(mHandler, e);
            }

            @Override
            public void onMessageReceived(String message, String senderIP) {
                // we received a broadcast from a server. expecting app:version:port
                String[] pieces = message.split(":");
                if(pieces != null && pieces.length == 3) {
                    String service = pieces[0];
                    if(service.equals(serviceName)) {
                        int version = Integer.parseInt(pieces[1]);
                        int port = Integer.parseInt(pieces[2]);
                        Peer p = new Peer(senderIP, port, service, version);
                        if (addPeer(p)) {
                            mListener.onFoundServer(mHandler, p);
                        }
                    }
                } else {
                    mListener.onError(mHandler, new IndexOutOfBoundsException("The client expected three pieces of data from the server but received "+ message));
                }
            }
        }));
        mBroadcastListenerThread.start();

        // performs cleanup tasks such as checking for disconnected servers
        mCleanupThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        ArrayList<Peer> connectedPeers = getPeers();
                        for(Peer p:connectedPeers) {
                            if(System.currentTimeMillis() - p.getLastSeenAt() > mServerTimeout && mServerConnections.get(p.getIpAddress()) == null) {
                                removePeer(p);
                                mListener.onLostServer(mHandler, p);
                            }
                        }

                        try {
                            Thread.sleep(mCleanupFrequency);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    mListener.onError(mHandler, e);
                }
            }
        });
        mCleanupThread.start();
    }

    /**
     * Establishes a TCP connection with the server.
     * Once this connection has been made the cleanup thread won't identify the server as lost unless the tcp connection is also disconnected.
     * @param server the server we will connect to
     */
    public void connectToServer(Peer server) {
        if(!mServerConnections.containsKey(server.getIpAddress())) {
            ServerThread serverThread = new ServerThread(server);
            new Thread(serverThread).start();
        }
    }

    @Override
    public void stop() {
        mIsRunning = false;
        if(mCleanupThread != null) {
            mCleanupThread.interrupt();
        }
        // TODO: we may need to manually close the broadcast listener socket
        if(mBroadcastListenerThread != null) {
            mBroadcastListenerThread.interrupt();
        }
        Connection[] servers = mServerConnections.values().toArray(new Connection[mServerConnections.size()]);
        for(Connection c:servers) {
            c.close();
        }
        mServerConnections.clear();
    }

    @Override
    public boolean isRunning() {
        return mIsRunning;
    }

    @Override
    public void setHandler(Handler handler) {
        mHandler = handler;
    }

    @Override
    /**
     * Sends a message to the peer
     * @param server the server to which the message will be sent
     * @param message the message being sent to the server
     */
    public void writeTo(Peer server, String message) {
        message = mListener.onWriteMessage(mHandler, server, message);
        if(mServerConnections.containsKey(server.getIpAddress())) {
            mServerConnections.get(server.getIpAddress()).write(message);
        }
    }

    /**
     * Manages a single server connection on it's own thread
     */
    private class ServerThread implements Runnable {
        private Connection mConnection;
        private Peer mServer;

        public ServerThread(Peer server) {
            mServer = server;
        }

        @Override
        public void run() {
            mListener.onBeforeStart(mHandler);

            // set up sockets
            try {
                InetAddress serverAddr = InetAddress.getByName(mServer.getIpAddress());
                mConnection = new Connection(new Socket(serverAddr, mServer.getPort()));
                mConnection.setOnCloseListener(new Connection.OnCloseListener() {
                    @Override
                    public void onClose() {
                        Thread.currentThread().interrupt();
                    }
                });
                // we store a refference to all connections so we can access them later
                mServerConnections.put(mConnection.getIpAddress(), mConnection);
            } catch (Exception e) {
                mListener.onError(mHandler, e);
                Thread.currentThread().interrupt();
                return;
            }

            // begin listening to server
            while (!Thread.currentThread().isInterrupted()) {
                String message = mConnection.readLine();
                if(message == null) {
                    Thread.currentThread().interrupt();
                } else {
                    mListener.onMessageReceived(mHandler, mServer, message);
                }
            }
            // close the connection
            mConnection.close();
            // remove all instances of the peer
            if(mServerConnections.containsKey(mConnection.getIpAddress())) {
                mServerConnections.remove(mConnection.getIpAddress());
            }
            removePeer(mServer);
            mListener.onLostServer(mHandler, mServer);
        }
    }

    public interface OnClientEventListener {
        public void onBeforeStart(Handler handle);
        public void onError(Handler handle, Exception e);
        public void onFoundServer(Handler handle, Peer server);
        public void onLostServer(Handler handle, Peer server);
        public void onMessageReceived(Handler handle, Peer server, String message);

        /**
         * Allows you to perform global operations on a message(such as encryption) based on the peer
         * @param server
         * @param message
         * @return
         */
        public String onWriteMessage(Handler handle, Peer server, String message);
    }
}
