package io.subutai.hub.share.dto.environment;


import java.util.ArrayList;
import java.util.List;


public class EnvironmentDto
{
    public enum BuildState
    {
        CREATED,
        EXCHANGE_INFO,
        SETUP_P2P,
        BUILD_CONTAINER,
        CONFIGURE_CONTAINER,
        DESTROYING,
        DESTROYED,
        DISABLED,
        READY
    }


    public enum State
    {
        PENDING, EMPTY, UNDER_MODIFICATION, HEALTHY, IMPORTING, UNHEALTHY
    }


    private String id;

    private String name;

    private BuildState buildState;

    private State state;

    private ArrayList<EnvironmentPeerDto> peers = new ArrayList<>();

    private List<EnvironmentNodesDto> nodes = new ArrayList<>();


    public String getId()
    {
        return id;
    }


    public EnvironmentDto setId( String id )
    {
        this.id = id;

        return this;
    }


    public String getName()
    {
        return name;
    }


    public EnvironmentDto setName( String name )
    {
        this.name = name;

        return this;
    }


    public State getState()
    {
        return state;
    }


    public EnvironmentDto setState( State state )
    {
        this.state = state;

        return this;
    }


    public BuildState getBuildState()
    {
        return buildState;
    }


    public void setBuildState( final BuildState buildState )
    {
        this.buildState = buildState;
    }


    public List<EnvironmentPeerDto> getPeers()
    {
        return peers;
    }


    public void setPeers( final ArrayList<EnvironmentPeerDto> peers )
    {
        this.peers = peers;
    }


    public void addPeer( EnvironmentPeerDto peer )
    {
        this.peers.add( peer );
    }


    public void removePeer( EnvironmentPeerDto peer )
    {
        this.peers.remove( peer );
    }


    public EnvironmentPeerDto findPeer( String peerId )
    {
        for ( EnvironmentPeerDto envPeerDto : this.peers )
        {
            if ( envPeerDto.getPeerId().equals( peerId ) )
            {
                return envPeerDto;
            }
        }
        return null;
    }


    public List<EnvironmentNodesDto> getNodes()
    {
        return nodes;
    }


    public void setNodes( final List<EnvironmentNodesDto> nodes )
    {
        this.nodes = nodes;
    }


    public void addNode( EnvironmentNodesDto node )
    {
        this.nodes.add( node );
    }


    public void removeNode( EnvironmentNodesDto node )
    {
        this.nodes.remove( node );
    }
}
