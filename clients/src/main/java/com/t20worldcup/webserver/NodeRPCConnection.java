package com.t20worldcup.webserver;

import net.corda.client.rpc.CordaRPCClient;
import net.corda.client.rpc.CordaRPCConnection;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.utilities.NetworkHostAndPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * Wraps an RPC connection to a Corda node.
 *
 * The RPC connection is configured using command line arguments.
 */
@Component
public class NodeRPCConnection implements AutoCloseable {
    // The host of the node we are connecting to.
    @Value("${bank.config.rpc.host}")
    private String bankHost;
    // The RPC port of the node we are connecting to.
    @Value("${bank.config.rpc.username}")
    private String bankUsername;
    // The username for logging into the RPC client.
    @Value("${bank.config.rpc.password}")
    private String bankPassword;
    // The password for logging into the RPC client.
    @Value("${bank.config.rpc.port}")
    private int bankRpcPort;

    @Value("${school.config.rpc.host}")
    private String schoolHost;
    // The RPC port of the node we are connecting to.
    @Value("${school.config.rpc.username}")
    private String schoolUsername;
    // The username for logging into the RPC client.
    @Value("${school.config.rpc.password}")
    private String schoolPassword;
    // The password for logging into the RPC client.
    @Value("${school.config.rpc.port}")
    private int schoolRpcPort;


    @Value("${student.config.rpc.host}")
    private String studentHost;
    // The RPC port of the node we are connecting to.
    @Value("${student.config.rpc.username}")
    private String studentUsername;
    // The username for logging into the RPC client.
    @Value("${student.config.rpc.password}")
    private String studentPassword;
    // The password for logging into the RPC client.
    @Value("${student.config.rpc.port}")
    private int studentRpcPort;

    private CordaRPCConnection bankRpcConnection;
    private CordaRPCConnection schoolRpcConnection;

    private CordaRPCConnection studentRpcConnection;
    CordaRPCOps bankProxy;
    CordaRPCOps schoolProxy;

    CordaRPCOps studentProxy;

    @PostConstruct
    public void initialiseNodeRPCConnection() {
        NetworkHostAndPort bankRpcAddress = new NetworkHostAndPort(bankHost, bankRpcPort);
        CordaRPCClient bankRpcClient = new CordaRPCClient(bankRpcAddress);
        bankRpcConnection = bankRpcClient.start(bankUsername, bankPassword);
        bankProxy = bankRpcConnection.getProxy();


        NetworkHostAndPort schoolRpcAddress = new NetworkHostAndPort(schoolHost, schoolRpcPort);
        CordaRPCClient schoolRpcClient = new CordaRPCClient(schoolRpcAddress);
        schoolRpcConnection = schoolRpcClient.start(schoolUsername, schoolPassword);
        schoolProxy = schoolRpcConnection.getProxy();

        NetworkHostAndPort studentRpcAddress = new NetworkHostAndPort(studentHost, studentRpcPort);
        CordaRPCClient studentRpcClient = new CordaRPCClient(studentRpcAddress);
        studentRpcConnection = studentRpcClient.start(studentUsername, studentPassword);
        studentProxy = studentRpcConnection.getProxy();
    }

    @PreDestroy
    public void close() {
        bankRpcConnection.notifyServerAndClose();
        schoolRpcConnection.notifyServerAndClose();
        studentRpcConnection.notifyServerAndClose();
    }
}
