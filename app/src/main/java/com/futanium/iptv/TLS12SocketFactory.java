package com.futanium.iptv;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class TLS12SocketFactory extends SSLSocketFactory {

    private final SSLSocketFactory delegate;

    public TLS12SocketFactory() {
        try {
            SSLContext sc = SSLContext.getInstance("TLSv1.2");
            sc.init(null, null, null);
            delegate = sc.getSocketFactory();
        } catch (Exception e) {
            throw new RuntimeException("Não foi possível inicializar TLS 1.2", e);
        }
    }

    private Socket enableTLS12(Socket s) {
        if (s instanceof SSLSocket) {
            try {
                ((SSLSocket) s).setEnabledProtocols(new String[]{"TLSv1.2","TLSv1.1","TLSv1"});
            } catch (Exception ignored) {}
        }
        return s;
    }

    @Override public String[] getDefaultCipherSuites()  { return delegate.getDefaultCipherSuites(); }
    @Override public String[] getSupportedCipherSuites(){ return delegate.getSupportedCipherSuites(); }

    @Override public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return enableTLS12(delegate.createSocket(s, host, port, autoClose));
    }
    @Override public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
        return enableTLS12(delegate.createSocket(host, port));
    }
    @Override public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
        return enableTLS12(delegate.createSocket(host, port, localHost, localPort));
    }
    @Override public Socket createSocket(InetAddress host, int port) throws IOException {
        return enableTLS12(delegate.createSocket(host, port));
    }
    @Override public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return enableTLS12(delegate.createSocket(address, port, localAddress, localPort));
    }
}