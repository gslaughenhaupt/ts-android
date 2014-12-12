package com.door43.translationstudio.network;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

/**
 * This class send broadcast messages to the network
 */
public class BroadcastRunnable implements Runnable {
    private final int mPort;
    private final String mData;
    private final OnBroadcastEventListener mListener;
    private final InetAddress mIpAddress;
    private final int mBroadcastFrequency;

    public BroadcastRunnable(String data, InetAddress address, int port, int broadcastFrequency, OnBroadcastEventListener listener) {
        mPort = port;
        mData = data;
        mListener = listener;
        mIpAddress = address;
        mBroadcastFrequency = broadcastFrequency;
    }

    @Override
    public void run() {
        DatagramSocket socket;
        try {
            socket = new DatagramSocket();
            socket.setBroadcast(true);
        } catch (SocketException e) {
            mListener.onError(e);
            return;
        }

        DatagramPacket sendPacket;
        sendPacket = new DatagramPacket(mData.getBytes(), mData.length(), mIpAddress, mPort);

        try {
            while (!Thread.currentThread().isInterrupted()) {
               socket.send(sendPacket);
               try {
                   Thread.sleep(mBroadcastFrequency);
               } catch (InterruptedException e) {
                   break;
               }
            }
        } catch (Exception e) {
            mListener.onError(e);
        } finally {
            socket.close();
        }
    }

    public interface OnBroadcastEventListener {
        public void onError(Exception e);
    }
}