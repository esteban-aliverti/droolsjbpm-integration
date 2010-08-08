package org.drools.grid.task;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.mina.core.future.CloseFuture;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.IoFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.serialization.ObjectSerializationCodecFactory;
import org.apache.mina.transport.socket.SocketConnector;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.drools.SystemEventListener;
import org.drools.SystemEventListenerFactory;
import org.drools.grid.ConnectorException;
import org.drools.grid.ConnectorType;
import org.drools.grid.GenericConnection;
import org.drools.grid.GenericHumanTaskConnector;
import org.drools.grid.HumanTaskNodeService;

import org.drools.grid.internal.Message;
import org.drools.grid.internal.MessageResponseHandler;
import org.drools.grid.internal.responsehandlers.BlockingMessageResponseHandler;
import org.drools.grid.GridConnection;
import org.drools.grid.remote.mina.MinaIoHandler;

public class RemoteMinaHumanTaskConnector
        implements
        GenericHumanTaskConnector {

    protected IoSession session;
    protected final String name;
    protected AtomicInteger counter;
    protected SocketConnector connector;
    protected SocketAddress address;
    protected SystemEventListener eventListener;
    protected GridConnection connection;

    public RemoteMinaHumanTaskConnector(String name,
            String providerAddress, Integer providerPort,
            SystemEventListener eventListener) {

        SocketConnector minaconnector = new NioSocketConnector();
        minaconnector.setHandler(new MinaIoHandler(SystemEventListenerFactory.getSystemEventListener()));
        if (name == null) {
            throw new IllegalArgumentException("Name can not be null");
        }
        this.name = name;
        this.counter = new AtomicInteger();
        this.address = new InetSocketAddress(providerAddress, providerPort);
        this.connector = minaconnector;
        this.eventListener = eventListener;
        this.connection = new GridConnection();
    }

    public void connect() throws ConnectorException {
        if (session != null && session.isConnected()) {
            throw new IllegalStateException("Already connected. Disconnect first.");
            
        }

        try {
            this.connector.getFilterChain().addLast("codec",
                    new ProtocolCodecFilter(new ObjectSerializationCodecFactory()));

            ConnectFuture future1 = this.connector.connect(this.address);
            future1.await(2000);
            if (!future1.isConnected()) {
                eventListener.info("unable to connect : " + address + " : " + future1.getException());
                Logger.getLogger(RemoteMinaHumanTaskConnector.class.getName()).log(Level.SEVERE, null, "The Node Connection Failed!");
                throw new ConnectorException("unable to connect : " + address + " : " + future1.getException());
            }
            eventListener.info("connected : " + address);
            this.session = future1.getSession();
        } catch (Exception e) {
            throw new ConnectorException(e);
        }
    }

    public void disconnect() throws ConnectorException {

        if (session != null && session.isConnected()) {

            CloseFuture future = session.getCloseFuture();

            future.addListener((IoFutureListener<?>) new IoFutureListener<IoFuture>() {

                public void operationComplete(IoFuture future) {
                    System.out.println("The human task connector session is now closed");
                }
            });

            session.close(false);
            future.awaitUninterruptibly();

            connector.dispose();
        }

    }

    private void addResponseHandler(int id,
            MessageResponseHandler responseHandler) {
        ((MinaIoHandler) this.connector.getHandler()).addResponseHandler(id,
                responseHandler);
    }

    public void write(Message msg,
            MessageResponseHandler responseHandler) {
        if (responseHandler != null) {
            addResponseHandler(msg.getResponseId(),
                    responseHandler);
        }
        this.session.write(msg);
    }

    public Message write(Message msg) throws ConnectorException {
        BlockingMessageResponseHandler responseHandler = new BlockingMessageResponseHandler();

        if (responseHandler != null) {
            addResponseHandler(msg.getResponseId(),
                    responseHandler);
        }
        this.session.write(msg);

        Message returnMessage = responseHandler.getMessage();
        if (responseHandler.getError() != null) {
            throw responseHandler.getError();
        }

        return returnMessage;
    }

    public String getId() {
        String hostName = ((InetSocketAddress) this.address).getHostName();
        int hostPort = ((InetSocketAddress) this.address).getPort();
        return "Mina:" + this.name + ":" + hostName + ":" + hostPort;
    }

    public void setSession(Object object) {
        this.session = (IoSession) object;
    }

    public GenericConnection getConnection() {
        return this.connection;
    }

    public HumanTaskNodeService getHumanTaskNodeService() throws ConnectorException {
        return new HumanTaskServiceImpl(this, (int) this.session.getId());
    }

    public ConnectorType getConnectorType() {
        return ConnectorType.REMOTE;
    }
}