package io.subutai.core.strategy.impl;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.subutai.common.environment.Node;
import io.subutai.common.environment.Topology;
import io.subutai.common.peer.ContainerSize;
import io.subutai.common.quota.ContainerQuota;
import io.subutai.common.resource.PeerGroupResources;
import io.subutai.common.resource.PeerResources;
import io.subutai.core.strategy.api.ExampleStrategy;
import io.subutai.common.environment.NodeSchema;
import io.subutai.core.strategy.api.StrategyException;


/**
 * Master
 */
public class ExamplePlacementStrategy implements ExampleStrategy
{
    private static final Logger LOGGER = LoggerFactory.getLogger( ExamplePlacementStrategy.class );

    private List<NodeSchema> scheme = new ArrayList<>();
    private static ExamplePlacementStrategy instance;


    public static ExamplePlacementStrategy getInstance()
    {
        if ( instance == null )
        {
            instance = new ExamplePlacementStrategy();
        }
        return instance;
    }


    public ExamplePlacementStrategy()
    {
        scheme.add( new NodeSchema( "master", ContainerSize.TINY, "master", 0, 0 ) );
        scheme.add( new NodeSchema( "hadoop", ContainerSize.TINY, "hadoop", 0, 0 ) );
        scheme.add( new NodeSchema( "cassandra", ContainerSize.TINY, "cassandra", 0, 0 ) );
    }


    @Override
    public String getId()
    {
        return ID;
    }


    @Override
    public String getTitle()
    {
        return "Example container placement strategy.";
    }


    @Override
    public Topology distribute( final String environmentName, PeerGroupResources peerGroupResources,
                                Map<ContainerSize, ContainerQuota> quotas ) throws StrategyException
    {
        Topology result = new Topology( environmentName );

        Set<Node> nodes = distribute( getScheme(), peerGroupResources, quotas );
        for ( Node node : nodes )
        {
            result.addNodePlacement( node.getPeerId(), node );
        }

        return result;
    }


    @Override
    public Topology distribute( final String environmentName, final List<NodeSchema> nodeSchema,
                                final PeerGroupResources peerGroupResources,
                                final Map<ContainerSize, ContainerQuota> quotas ) throws StrategyException
    {
        Topology result = new Topology( environmentName/*, sshGroupId, hostGroupId*/ );

        Set<Node> ng = distribute( nodeSchema, peerGroupResources, quotas );
        for ( Node node : ng )
        {
            result.addNodePlacement( node.getPeerId(), node );
        }

        return result;
    }


    @Override
    public List<NodeSchema> getScheme()
    {
        return scheme;
    }


    protected Set<Node> distribute( List<NodeSchema> nodeSchemas, PeerGroupResources peerGroupResources,
                                    Map<ContainerSize, ContainerQuota> quotas ) throws StrategyException
    {

        // build list of allocators
        List<ResourceAllocator> allocators = new ArrayList<>();
        for ( PeerResources peerResources : peerGroupResources.getResources() )
        {
            if ( peerResources.getEnvironmentLimit() > 0 )
            {
                ResourceAllocator resourceAllocator = new ResourceAllocator( peerResources );
                allocators.add( resourceAllocator );
            }
        }


        // distribute node groups
        for ( NodeSchema nodeSchema : nodeSchemas )
        {
            String containerName = generateContainerName( nodeSchema );

            // select preferred peer
            List<ResourceAllocator> preferredAllocators = getPreferredAllocators( allocators );
            boolean allocated = false;
            for ( ResourceAllocator resourceAllocator : preferredAllocators )
            {
                allocated = resourceAllocator
                        .allocate( containerName, nodeSchema.getTemplateName(), nodeSchema.getSize(),
                                quotas.get( nodeSchema.getSize() ) );
                if ( allocated )
                {
                    break;
                }
            }

            if ( !allocated )
            {
                throw new StrategyException(
                        "Could not allocate containers. There is no space for container: '" + containerName + "'" );
            }
        }

        Set<Node> nodes = new HashSet<>();

        for ( ResourceAllocator resourceAllocator : allocators )
        {
            List<ResourceAllocator.AllocatedContainer> containers = resourceAllocator.getContainers();
            if ( !containers.isEmpty() )
            {
                for ( ResourceAllocator.AllocatedContainer container : containers )
                {
                    Node node =
                            new Node( UUID.randomUUID().toString(), container.getName(), container.getTemplateName(),
                                    container.getSize(), 0, 0, container.getPeerId(), container.getHostId() );
                    nodes.add( node );
                }
            }
        }


        return nodes;
    }


    private List<ResourceAllocator> getPreferredAllocators( final List<ResourceAllocator> allocators )
    {
        List<ResourceAllocator> result = new ArrayList<>( allocators );
        Collections.shuffle( result );
        return result;
    }


    private String generateContainerName( final NodeSchema nodeSchema )
    {
        return nodeSchema.getName().replaceAll( "\\s+", "_" );
    }
}
