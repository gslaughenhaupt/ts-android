package com.door43.translationstudio.network;

import com.door43.util.KeyValueStore;

/**
 * A peer is any device that is connected to another device.
 * Clients can have peers in the form of servers and servers can have peers in the form of clients.
 */
public class Peer {
    private final String mIpAddress;
    private final int mPort;
    private String mServiceName = "";
    private int mVersion = 0;
    private long mLastSeenAt = 0;
    private boolean mIsConnected = false;
    public final KeyValueStore keyStore = new KeyValueStore();
    private boolean mIsAuthorized = false;

    /**
     * Specifies a new peer (likely a client)
     * @param ipAddress
     * @param port
     */
    public Peer(String ipAddress, int port) {
        mIpAddress = ipAddress;
        mPort = port;
        touch();
    }

    /**
     * Specifies a new peer (likely a server)
     * @param ipAddress
     * @param port
     * @param serviceName
     * @param version
     */
    public Peer(String ipAddress, int port, String serviceName, int version) {
        mIpAddress = ipAddress;
        mPort = port;
        mServiceName = serviceName;
        mVersion = version;
        touch();
    }

    /**
     * Returns the ip address of the peer
     * @return
     */
    public String getIpAddress() {
        return mIpAddress;
    }

    /**
     * Returns the port number of the peer
     * @return
     */
    public int getPort() {
        return mPort;
    }

    /**
     * Returns the name of the service that was advertized by the peer.
     * This may be blank in the case of clients
     * @return
     */
    public String getServiceName() {
        return mServiceName;
    }

    /**
     * Returns the version of the service being offered (app version).
     * This may be 0 in the case of clients
     * @return
     */
    public int getVersion() {
        return mVersion;
    }

    /**
     * Returns the time when we last saw this peer
     * @return
     */
    public long getLastSeenAt() {
        return mLastSeenAt;
    }

    /**
     * Changes the timestamp on the peer so we know when we last saw it.
     */
    public void touch() {
        mLastSeenAt = System.currentTimeMillis();
    }

    /**
     * Checks if the peer is connected.
     * @return
     */
    public boolean isConnected() {
        return mIsConnected;
    }

    /**
     * Checks if the peer has permission to connect
     * @return
     */
    public boolean isAuthorized() {
        return mIsAuthorized;
    }

    /**
     * Sets the authorization status of the peer
     * @param isAuthorized
     */
    public void setIsAuthorized(boolean isAuthorized) {
        mIsAuthorized = isAuthorized;
    }

    /**
     * Sets the connection status of the peer
     * @param isConnected
     */
    public void setIsConnected(Boolean isConnected) {
        mIsConnected = isConnected;
    }
}
