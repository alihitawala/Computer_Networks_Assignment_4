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
    private Map<Long, IOFSwitch> _switches;
    private Collection<Link> _links;
    private Collection<Long> switchIds;
    private List<Link> links;

    public BellmanFord(Collection<Host> hosts, Map<Long, IOFSwitch> switches, Collection<Link> links) {
        this.hosts = hosts;
        this._switches = switches;
        this._links = links;
    }

    private List<Link> getLinkSet(Collection<Link> links) {
        Set<Link> result = new HashSet<Link>();
        for (Link link: links) {
            result.add(new Link(link.getSrc(), link.getSrcPort(), link.getDst(), link.getDstPort()));
            result.add(new Link(link.getDst(), link.getDstPort(), link.getSrc(), link.getSrcPort()));
        }
        return new ArrayList<Link>(result);
    }

    private void constructInternalDS() {
        this.links = getLinkSet(_links);;
        this.switchIds = this._switches.keySet();
    }

    public Map<Pair, Path> startOnAll() {
        Map<Pair, Path> aggregatedPath = new HashMap<Pair, Path>();
        constructInternalDS();
        for (Long l : this.switchIds) {
            IOFSwitch iofSwitch = this._switches.get(l);
            Map<Pair, Path> pairPathMap = start(iofSwitch);
            aggregatedPath.putAll(pairPathMap);
        }
        return aggregatedPath;
    }

    public Map<Pair, Path> start(IOFSwitch iofSwitch) {
        long src = (int) iofSwitch.getId();
        int V = this.switchIds.size();
        int E = this.links.size();
        Map<Pair, Path> result = new HashMap<Pair, Path>();
        Map<Long, Integer> distMap = new HashMap<Long, Integer>();
        Map<Long, Long> predMap = new HashMap<Long, Long>();
        Map<Long, Link> predLinkMap = new HashMap<Long, Link>();
        Map<Long, Boolean> reachable = new HashMap<Long, Boolean>();
        for (Long key : this.switchIds) {
            distMap.put(key, Integer.MAX_VALUE);
            predMap.put(key, (long)-1);
            reachable.put(key, false);
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
                    reachable.put(u, true);
                    reachable.put(v, true);
                }
            }
        }
        for (Long switchId : this.switchIds) {
            if (switchId != src && reachable.get(switchId)) {
                long destSwitchId = switchId;
                Pair pair = new Pair(src, destSwitchId);
                Path path = new Path();
                path.setSrcSwitchId(src);
                path.setDestSwitchId(destSwitchId);
                do {
                    long v = destSwitchId;
                    path.getLinks().add(0, predLinkMap.get(v));
                    destSwitchId = predMap.get(destSwitchId);
                } while (destSwitchId != src);
                result.put(pair, path);
            }
        }
        //dummy entry src to src
        Pair pair = new Pair(src, src);
        Path path = new Path();
        path.setDestSwitchId(src);
        path.setSrcSwitchId(src);
        result.put(pair, path);
        return result;
    }
}

class Pair {
    private final Long srcSwitchId;
    private final Long destSwitchId;

    public Pair(Long srcSwitchId, Long destSwitchId) {
        this.srcSwitchId = srcSwitchId;
        this.destSwitchId = destSwitchId;
    }

    public Long getSrcSwitchId() {
        return srcSwitchId;
    }

    public Long getDestSwitchId() {
        return destSwitchId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Pair pair = (Pair) o;

        if (!srcSwitchId.equals(pair.srcSwitchId)) return false;
        return destSwitchId.equals(pair.destSwitchId);

    }

    @Override
    public int hashCode() {
        int result = srcSwitchId.hashCode();
        result = 31 * result + destSwitchId.hashCode();
        return result;
    }
}

class Path {
    Long srcSwitchId;
    Long destSwitchId;
    List<Link> links;

    public Path() {
        links = new LinkedList<Link>();
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
