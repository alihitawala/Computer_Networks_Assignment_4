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

    public RuleEngine(Collection<Host> hosts, Map<Long, IOFSwitch> switches, Collection<Link> links) {
        this.hosts = hosts;
        this.switches = switches;
        this.links = links;
    }

    public void applyRuleToAllHosts() {
        Map<Pair, Path> aggregatedPath = new BellmanFord(this.hosts, this.switches, this.links).startOnAll();
        for (Host srcHost : this.hosts) {
            for (Host destHost : this.hosts) {
                if (!srcHost.equals(destHost)) {
                    Pair pair = new Pair(srcHost.getSwitch().getId(), destHost.getSwitch().getId());
                    if (!aggregatedPath.containsKey(pair)) {
                        //todo error handling
                        continue;
                    }
                    Path path = aggregatedPath.get(pair);
                    System.out.println("Path between host :: " + srcHost.getName() + "-->" + destHost.getName());
                    Long lastSwitchId = destHost.getSwitch().getId();
                    for (Link link : path.getLinks()) {
                        System.out.println("Path :: " + link.getSrc() + "-->" + link.getDst());
                        installRule(destHost, link.getSrc(), link.getSrcPort());
                        lastSwitchId = link.getDst();
                    }
                    installRule(destHost, lastSwitchId, destHost.getPort());
                    System.out.println("END");
                }
            }
        }
    }

    private void installRule(Host destHost, Long switchId, int switchOutputPort) {
        OFMatch match = new OFMatch();
        OFMatchField field1 = new OFMatchField(OFOXMFieldType.ETH_TYPE, Ethernet.TYPE_IPv4);
        OFMatchField field2 = new OFMatchField(OFOXMFieldType.IPV4_DST, destHost.getIPv4Address());
        List<OFMatchField> matches = new ArrayList<OFMatchField>();
        matches.add(field1);
        matches.add(field2);
        match.setMatchFields(matches);
        OFActionOutput actionOutput = new OFActionOutput(switchOutputPort);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(actionOutput);
        OFInstructionApplyActions applyActions = new OFInstructionApplyActions(actions);
        List<OFInstruction> instructions = new ArrayList<OFInstruction>();
        instructions.add(applyActions);
        SwitchCommands.installRule(this.switches.get(switchId), L3Routing.table, SwitchCommands.DEFAULT_PRIORITY, match, instructions);
    }
}
