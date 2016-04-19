package edu.wisc.cs.sdn.apps.loadbalancer;
import edu.wisc.cs.sdn.apps.util.Host;
import edu.wisc.cs.sdn.apps.util.SwitchCommands;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.util.MACAddress;
import org.openflow.protocol.OFPacketIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by aliHitawala on 4/8/16.
 */
public class LoadBalancerHandlePacket {

    public static final String MODULE_NAME = LoadBalancerHandlePacket.class.getSimpleName();

    // Interface to the logging system
    private static Logger log = LoggerFactory.getLogger(MODULE_NAME);

    private Map<Integer,LoadBalancerInstance> instances;
    private LoadBalancer loadBalancer;
    private LoadBalancerRuleEngine loadBalancerRuleEngine;

    public LoadBalancerHandlePacket(Map<Integer, LoadBalancerInstance> instances, LoadBalancer loadBalancer, LoadBalancerRuleEngine loadBalancerRuleEngine) {
        this.loadBalancer = loadBalancer;
        this.loadBalancerRuleEngine = loadBalancerRuleEngine;
        this.instances = instances;
    }

    public void handleARPPacket(OFPacketIn pktIn, Ethernet eth, IOFSwitch sw) {
        if (eth.getEtherType() != Ethernet.TYPE_ARP) {
            return;
        }
        ARP arp = (ARP) eth.getPayload();
        int targetIP = IPv4.toIPv4Address(arp.getTargetProtocolAddress());
        log.info(String.format("Received ARP request for %s from %s",
                IPv4.fromIPv4Address(targetIP),
                MACAddress.valueOf(arp.getSenderHardwareAddress()).toString()));
        if (!this.instances.containsKey(targetIP))
            return;
        LoadBalancerInstance loadBalancerInstance = instances.get(targetIP);
        byte[] deviceMac = loadBalancerInstance.getVirtualMAC();
        arp.setOpCode(ARP.OP_REPLY);
        arp.setTargetHardwareAddress(arp.getSenderHardwareAddress());
        arp.setTargetProtocolAddress(arp.getSenderProtocolAddress());
        arp.setSenderHardwareAddress(deviceMac);
        arp.setSenderProtocolAddress(IPv4.toIPv4AddressBytes(targetIP));
        eth.setDestinationMACAddress(eth.getSourceMACAddress());
        eth.setSourceMACAddress(deviceMac);
        // Send the ARP reply
        log.info(String.format("Sending ARP reply %s->%s",
                IPv4.fromIPv4Address(targetIP),
                MACAddress.valueOf(deviceMac).toString()));
        SwitchCommands.sendPacket(sw, (short) pktIn.getInPort(), eth);
    }

    public void handleIPPacket(OFPacketIn pktIn, Ethernet eth, IOFSwitch sw, List<IOFSwitch> switches) {
        if (eth.getEtherType() != Ethernet.TYPE_IPv4)
            return;
        IPv4 ipv4 = (IPv4) eth.getPayload();
        if (ipv4.getProtocol() != IPv4.PROTOCOL_TCP)
            return;
        TCP tcpPacket = (TCP) ipv4.getPayload();
        short sourcePort = tcpPacket.getSourcePort();
        short destinationPort = tcpPacket.getDestinationPort();
        int sourceIp = ipv4.getSourceAddress();
        LoadBalancerInstance instance = this.instances.get(ipv4.getDestinationAddress());
        int hostIp = instance.getNextHostIP();
        System.out.println("\nNEXT HOP ID Obtained:: " + IPv4.fromIPv4Address(hostIp));
        byte[] hostMACAddress = this.loadBalancer.getHostMACAddress(hostIp);
        System.out.println("\nNEXT HOP MAC ID Obtained:: " + MACAddress.valueOf(hostMACAddress).toString());
        for (IOFSwitch s : switches)
            this.loadBalancerRuleEngine.addReRoutingRule(s, sourceIp, instance, hostIp, hostMACAddress, sourcePort, destinationPort);
    }

    public boolean isARPPacket(Ethernet eth) {
        if (eth.getEtherType() != Ethernet.TYPE_ARP) {
            return false;
        }
        ARP arp = (ARP) eth.getPayload();

        // We only care about ARP requests for IPv4 addresses
        if (arp.getOpCode() != ARP.OP_REQUEST
                || arp.getProtocolType() != ARP.PROTO_TYPE_IP) {
            return false;
        }
        return true;
    }

    public boolean isIPPacket(Ethernet eth) {
        return eth.getEtherType() == Ethernet.TYPE_IPv4;
    }
}