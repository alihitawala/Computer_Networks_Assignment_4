package edu.wisc.cs.sdn.apps.l3routing;

import edu.wisc.cs.sdn.apps.util.Host;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.routing.Link;

import java.util.*;

/**
 * Created by aliHitawala on 4/5/16.
 */
public class BellmanFord {
    private Collection<Host> hosts;
    private Map<Long, IOFSwitch> switches;
    private Collection<Link> _links;
    private List<Link> links;

    private Map<Long, List<Long>> switchToHosts;
    private Map<Long, Host> hostIdToHost;

    public BellmanFord(Collection<Host> hosts, Map<Long, IOFSwitch> switches, Collection<Link> links) {
        this.hosts = hosts;
        this.switches = switches;
        this._links = links;
        hostIdToHost = new HashMap<Long, Host>();
        switchToHosts = new HashMap<Long, List<Long>>();
    }

    //todo check
    private List<Link> getLinkSet(Collection<Link> links) {
        Set<Link> result = new HashSet<Link>();
        for (Link link: links) {
            result.add(new Link(link.getSrc(), link.getSrcPort(), link.getDst(), link.getDstPort()));
            result.add(new Link(link.getDst(), link.getDstPort(), link.getSrc(), link.getSrcPort()));
        }
        return new ArrayList<Link>(result);
    }

    private void constructInternalDS() {
        hostIdToHost.clear();
        switchToHosts.clear();
        this.links = getLinkSet(_links);
        for (Host host : hosts) {
            hostIdToHost.put(host.getID(), host);
            long switchId = host.getSwitch().getId();
            if (switchToHosts.containsKey(switchId)) {
                switchToHosts.get(switchId).add(host.getID());
            }
            else {
                List<Long> hostIds = new ArrayList<Long>();
                hostIds.add(host.getID());
                switchToHosts.put(switchId, hostIds);
            }
        }
    }

    public List<Path> start(IOFSwitch iofSwitch) {
        long src = (int) iofSwitch.getId();
        constructInternalDS();
        int V = this.switches.size();
        int E = this.links.size();
        List<Path> result = new ArrayList<Path>();
        Map<Long, Integer> distMap = new HashMap<Long, Integer>();
        Map<Long, Long> predMap = new HashMap<Long, Long>();
        Map<Long, Link> predLinkMap = new HashMap<Long, Link>();
        for (Long key : this.switches.keySet()) {
            distMap.put(key, Integer.MAX_VALUE);
            predMap.put(key, (long)-1);
        }
        distMap.put(src, 0);
        for (int i = 1; i <= V-1; i++)
        {
            for (int j = 0; j < E; j++)
            {
                Link currentLink = links.get(j);
                long u = currentLink.getSrc();
                long v = currentLink.getDst();
                int weight = 1;
                if (distMap.get(u) != Integer.MAX_VALUE && distMap.get(u) + weight < distMap.get(v)) {
                    distMap.put(v, distMap.get(u) + weight);
                    predMap.put(v, u);
                    predLinkMap.put(v, currentLink);
                }
            }
        }
        for (Long switchId : this.switches.keySet()) {
            if (switchId != src) {
                long destSwitchId = switchId;
                Path path = new Path();
                path.setDestSwitchId(destSwitchId);
                path.setSrcSwitchId(src);
                do {
                    long v = destSwitchId;
                    path.getLinks().add(0, predLinkMap.get(v));
                    destSwitchId = predMap.get(destSwitchId);
                } while (destSwitchId != src);
                result.add(path);
            }
        }
        return result;
    }
}

class Path {
    Long srcSwitchId;
    Long destSwitchId;
    List<Link> links;

    public Path() {
        links = new ArrayList<Link>();
    }

    public Long getSrcSwitchId() {
        return srcSwitchId;
    }

    public void setSrcSwitchId(Long srcSwitchId) {
        this.srcSwitchId = srcSwitchId;
    }

    public Long getDestSwitchId() {
        return destSwitchId;
    }

    public void setDestSwitchId(Long destSwitchId) {
        this.destSwitchId = destSwitchId;
    }

    public List<Link> getLinks() {
        return links;
    }

    public void addLinks(Link link) {
        this.links.add(link);
    }
}
