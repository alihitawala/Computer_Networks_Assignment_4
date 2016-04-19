package edu.wisc.cs.sdn.apps.l3routing;

import edu.wisc.cs.sdn.apps.util.Host;
import edu.wisc.cs.sdn.apps.util.SwitchCommands;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.routing.Link;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMatchField;
import org.openflow.protocol.OFOXMFieldType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.instruction.OFInstruction;
import org.openflow.protocol.instruction.OFInstructionApplyActions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created by aliHitawala on 4/6/16.
 */
public class RuleEngine {
    private Collection<Host> hosts;
    private Map<Long, IOFSwitch> switches;
    private Collection<Link> links;
    private Map<Pair, Path> pathDiscoveries;

    private enum RULE_TYPE {
        ADD, REMOVE
    }

    public RuleEngine(Collection<Host> hosts, Map<Long, IOFSwitch> switches, Collection<Link> links) {
        this.hosts = hosts;
        this.switches = switches;
        this.links = links;
    }

    public synchronized void applyRuleToAddAllHosts(Collection<Host> h, Map<Long, IOFSwitch> s, Collection<Link> l) {
        this.hosts = h;
        this.switches = s;
        this.links = l;
        pathDiscoveries = new BellmanFord(this.hosts, this.switches, this.links).startOnAll();
        for (Host destHost : this.hosts) {
            this.removeRuleForHost(destHost);
        }
        for (Host srcHost : this.hosts) {
            this.addPathFromSrcToAllHost(srcHost);
        }
    }

    public void removeRuleForHost(Host destHost) {
        int destinationIp = destHost.getIPv4Address();
        OFMatch match = this.getMatchCriteriaObject(destinationIp);
        for (Long switchId : this.switches.keySet()) {
            SwitchCommands.removeRules(this.switches.get(switchId),L3Routing.table, match);
        }
    }

    private synchronized void addPathFromSrcToAllHost(Host srcHost) {
        for (Host destHost : this.hosts) {
            if (!srcHost.equals(destHost))
                updatePathBetweenHosts(srcHost, destHost, RULE_TYPE.ADD);
        }
    }

    private synchronized void updatePathBetweenHosts(Host srcHost, Host destHost, RULE_TYPE ruleType) {
        if (srcHost.getSwitch() == null || destHost.getSwitch() == null)
            return;
        Pair pair = new Pair(srcHost.getSwitch().getId(), destHost.getSwitch().getId());
        if (!this.pathDiscoveries.containsKey(pair)) {
            //todo error handling
            return;
        }
        Path path = this.pathDiscoveries.get(pair);
        System.out.println("Path between host :: " + srcHost.getName() + "-->" + destHost.getName());
        Long lastSwitchId = destHost.getSwitch().getId();
        for (Link link : path.getLinks()) {
            System.out.println("Path :: " + link.getSrc() + "-->" + link.getDst());
            applyRuleToDevice(destHost, link.getSrc(), link.getSrcPort(), ruleType);
            lastSwitchId = link.getDst();
        }
        applyRuleToDevice(destHost, lastSwitchId, destHost.getPort(), ruleType);
        System.out.println("END");
    }

    private synchronized void applyRuleToDevice(Host destHost, Long switchId, int switchOutputPort, RULE_TYPE ruleType) {
        OFMatch match = this.getMatchCriteriaObject(destHost.getIPv4Address());
        OFActionOutput actionOutput = new OFActionOutput(switchOutputPort);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(actionOutput);
        OFInstructionApplyActions applyActions = new OFInstructionApplyActions(actions);
        List<OFInstruction> instructions = new ArrayList<OFInstruction>();
        instructions.add(applyActions);
        if (ruleType == RULE_TYPE.ADD)
            SwitchCommands.installRule(this.switches.get(switchId), L3Routing.table, SwitchCommands.DEFAULT_PRIORITY, match, instructions);
        else
            SwitchCommands.removeRules(this.switches.get(switchId), L3Routing.table, match);
    }

    private OFMatch getMatchCriteriaObject(int destinationIp) {
        OFMatch match = new OFMatch();
        OFMatchField field1 = new OFMatchField(OFOXMFieldType.ETH_TYPE, Ethernet.TYPE_IPv4);
        OFMatchField field2 = new OFMatchField(OFOXMFieldType.IPV4_DST, destinationIp);
        List<OFMatchField> matches = new ArrayList<OFMatchField>();
        matches.add(field1);
        matches.add(field2);
        match.setMatchFields(matches);
        return match;
    }
}
