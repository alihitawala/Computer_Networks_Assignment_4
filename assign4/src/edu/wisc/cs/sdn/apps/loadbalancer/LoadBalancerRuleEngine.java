package edu.wisc.cs.sdn.apps.loadbalancer;

import edu.wisc.cs.sdn.apps.l3routing.L3Routing;
import edu.wisc.cs.sdn.apps.util.SwitchCommands;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMatchField;
import org.openflow.protocol.OFOXMFieldType;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionSetField;
import org.openflow.protocol.instruction.OFInstruction;
import org.openflow.protocol.instruction.OFInstructionApplyActions;
import org.openflow.protocol.instruction.OFInstructionGotoTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by aliHitawala on 4/8/16.
 */
public class LoadBalancerRuleEngine {

    public static final String MODULE_NAME = LoadBalancerHandlePacket.class.getSimpleName();
    private static final short MIN_PRIORITY = 1;
    private static final short DEFAULT_PRIORITY = 5;
    private static final short MAX_PRIORITY = 10;
    private static final short HARD_TIMEOUT_RULES = 0;
    private static final short IDLE_TIMEOUT_RULES = 20;

    // Interface to the logging system
    private static Logger log = LoggerFactory.getLogger(MODULE_NAME);

    private Map<Integer,LoadBalancerInstance> instances;

    // Switch table in which rules should be installed
    private byte table;

    public LoadBalancerRuleEngine(Map<Integer, LoadBalancerInstance> instances, byte table) {
        this.instances = instances;
        this.table = table;
    }

    public void addARPRule(IOFSwitch sw) {
        for (int ip: this.instances.keySet()) {
            OFMatch match = new OFMatch();
            List<OFMatchField> matches = new ArrayList<OFMatchField>();
            OFMatchField field1 = new OFMatchField(OFOXMFieldType.ETH_TYPE, Ethernet.TYPE_ARP);
            matches.add(field1);
            OFMatchField field2 = new OFMatchField(OFOXMFieldType.ARP_TPA, ip);
            matches.add(field2);
            match.setMatchFields(matches);
            OFActionOutput actionOutput = new OFActionOutput(OFPort.OFPP_CONTROLLER);
            List<OFAction> actions = new ArrayList<OFAction>();
            actions.add(actionOutput);
            OFInstructionApplyActions applyActions = new OFInstructionApplyActions(actions);
            List<OFInstruction> instructions = new ArrayList<OFInstruction>();
            instructions.add(applyActions);
            SwitchCommands.installRule(sw, this.table, DEFAULT_PRIORITY, match, instructions);
        }
    }

    public void addVirtualIPRule(IOFSwitch sw)
    {
        for (int ip: this.instances.keySet())
        {
            OFMatch match = new OFMatch();
            List<OFMatchField> matches = new ArrayList<OFMatchField>();
            OFMatchField field1 = new OFMatchField(OFOXMFieldType.ETH_TYPE, Ethernet.TYPE_IPv4);
            matches.add(field1);
            OFMatchField field2 = new OFMatchField(OFOXMFieldType.IPV4_DST, ip);
            matches.add(field2);
            match.setMatchFields(matches);
            OFActionOutput actionOutput = new OFActionOutput(OFPort.OFPP_CONTROLLER);
            List<OFAction> actions = new ArrayList<OFAction>();
            actions.add(actionOutput);
            OFInstructionApplyActions applyActions = new OFInstructionApplyActions(actions);
            List<OFInstruction> instructions = new ArrayList<OFInstruction>();
            instructions.add(applyActions);
            SwitchCommands.installRule(sw, this.table, DEFAULT_PRIORITY, match, instructions);
        }

    }

    public void addReRoutingRule(IOFSwitch sw, int sourceIp, LoadBalancerInstance instance, int hostIp, byte[] hostMACAddress, short sourcePort, short destinationPort) {
        this.addOutgoingReRoutingRule(sw, sourceIp, instance, hostIp, hostMACAddress, sourcePort, destinationPort);
        this.addIncomingReRoutingRule(sw, sourceIp, instance, hostIp, hostMACAddress, sourcePort, destinationPort);
    }

    private void addIncomingReRoutingRule(IOFSwitch sw, int sourceIp, LoadBalancerInstance instance, int hostIp, byte[] hostMACAddress, short sourcePort, short destinationPort) {
        OFMatch match = new OFMatch();
        List<OFMatchField> matches = new ArrayList<OFMatchField>();
        OFMatchField ethernetType = new OFMatchField(OFOXMFieldType.ETH_TYPE, Ethernet.TYPE_IPv4);
        matches.add(ethernetType);
        OFMatchField srcIp = new OFMatchField(OFOXMFieldType.IPV4_SRC, hostIp);
        matches.add(srcIp);
        OFMatchField destinationIp = new OFMatchField(OFOXMFieldType.IPV4_DST, sourceIp);
        matches.add(destinationIp);
        OFMatchField protocol = new OFMatchField(OFOXMFieldType.IP_PROTO, IPv4.PROTOCOL_TCP);
        matches.add(protocol);
        OFMatchField destPort = new OFMatchField(OFOXMFieldType.TCP_SRC, destinationPort);
        matches.add(destPort);
        OFMatchField srcPort = new OFMatchField(OFOXMFieldType.TCP_DST, sourcePort);
        matches.add(srcPort);
        match.setMatchFields(matches);

        OFActionSetField setFieldMac = new OFActionSetField(OFOXMFieldType.ETH_SRC, instance.getVirtualMAC());
        OFActionSetField setFieldIp = new OFActionSetField(OFOXMFieldType.IPV4_SRC, instance.getVirtualIP());
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(setFieldMac);
        actions.add(setFieldIp);
        OFInstructionApplyActions ofInstructionApplyActions = new OFInstructionApplyActions(actions);
        OFInstructionGotoTable ofInstructionGotoTable = new OFInstructionGotoTable(L3Routing.table);
        List<OFInstruction> instructions = new ArrayList<OFInstruction>();
        instructions.add(ofInstructionApplyActions);
        instructions.add(ofInstructionGotoTable);
        SwitchCommands.installRule(sw, this.table, MAX_PRIORITY, match, instructions, HARD_TIMEOUT_RULES, IDLE_TIMEOUT_RULES);
    }

    private void addOutgoingReRoutingRule(IOFSwitch sw, int sourceIp, LoadBalancerInstance instance, int hostIp, byte[] hostMACAddress, short sourcePort, short destinationPort) {
        OFMatch match = new OFMatch();
        List<OFMatchField> matches = new ArrayList<OFMatchField>();
        OFMatchField field1 = new OFMatchField(OFOXMFieldType.ETH_TYPE, Ethernet.TYPE_IPv4);
        matches.add(field1);
        OFMatchField srcIp = new OFMatchField(OFOXMFieldType.IPV4_SRC, sourceIp);
        matches.add(srcIp);
        OFMatchField field2 = new OFMatchField(OFOXMFieldType.IPV4_DST, instance.getVirtualIP());
        matches.add(field2);
        OFMatchField protocol = new OFMatchField(OFOXMFieldType.IP_PROTO, IPv4.PROTOCOL_TCP);
        matches.add(protocol);
        OFMatchField field3 = new OFMatchField(OFOXMFieldType.TCP_SRC, sourcePort);
        matches.add(field3);
        OFMatchField field4 = new OFMatchField(OFOXMFieldType.TCP_DST, destinationPort);
        matches.add(field4);
        match.setMatchFields(matches);

        OFActionSetField setFieldMac = new OFActionSetField(OFOXMFieldType.ETH_DST, hostMACAddress);
        OFActionSetField setFieldIp = new OFActionSetField(OFOXMFieldType.IPV4_DST, hostIp);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(setFieldMac);
        actions.add(setFieldIp);
        OFInstructionApplyActions ofInstructionApplyActions = new OFInstructionApplyActions(actions);
        OFInstructionGotoTable ofInstructionGotoTable = new OFInstructionGotoTable(L3Routing.table);
        List<OFInstruction> instructions = new ArrayList<OFInstruction>();
        instructions.add(ofInstructionApplyActions);
        instructions.add(ofInstructionGotoTable);
        SwitchCommands.installRule(sw, this.table, MAX_PRIORITY, match, instructions, HARD_TIMEOUT_RULES, IDLE_TIMEOUT_RULES);
    }

    public void addDefaultRule(IOFSwitch sw) {
        OFMatch match = new OFMatch();
        OFInstructionGotoTable ofInstructionGotoTable = new OFInstructionGotoTable(L3Routing.table);
        List<OFInstruction> instructions = new ArrayList<OFInstruction>();
        instructions.add(ofInstructionGotoTable);
        SwitchCommands.installRule(sw, this.table, MIN_PRIORITY, match, instructions);
    }
}