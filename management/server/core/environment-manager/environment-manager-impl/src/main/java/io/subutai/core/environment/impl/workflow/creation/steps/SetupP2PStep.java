package io.subutai.core.environment.impl.workflow.creation.steps;


import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.net.util.SubnetUtils;

import io.subutai.common.environment.Topology;
import io.subutai.common.peer.Peer;
import io.subutai.common.peer.PeerException;
import io.subutai.common.protocol.P2PConfig;
import io.subutai.common.protocol.P2pIps;
import io.subutai.common.settings.Common;
import io.subutai.common.tracker.TrackerOperation;
import io.subutai.common.util.P2PUtil;
import io.subutai.core.environment.api.exception.EnvironmentCreationException;
import io.subutai.core.environment.impl.entity.EnvironmentImpl;
import io.subutai.core.environment.impl.entity.RhP2PIpEntity;
import io.subutai.core.environment.impl.workflow.creation.steps.helpers.SetupP2PConnectionTask;
import io.subutai.core.environment.impl.workflow.creation.steps.helpers.SetupTunnelTask;
import io.subutai.common.util.PeerUtil;


/**
 * P2P setup step
 */
public class SetupP2PStep
{
    private final Topology topology;
    private final EnvironmentImpl environment;
    private final TrackerOperation trackerOperation;


    public SetupP2PStep( final Topology topology, final EnvironmentImpl environment,
                         final TrackerOperation trackerOperation )
    {
        this.topology = topology;
        this.environment = environment;
        this.trackerOperation = trackerOperation;
    }


    public void execute() throws EnvironmentCreationException, PeerException
    {

        //create p2p subnet util
        SubnetUtils.SubnetInfo p2pSubnetInfo =
                new SubnetUtils( environment.getP2pSubnet(), P2PUtil.P2P_SUBNET_MASK ).getInfo();

        //get all subnet ips
        final String[] p2pAddresses = p2pSubnetInfo.getAllAddresses();

        //obtain target RHs
        Map<String, Set<String>> peerRhIds = topology.getPeerRhIds();

        //count total requested
        int totalIps = 0;

        for ( Set<String> peerRhs : peerRhIds.values() )
        {
            totalIps += peerRhs.size();
        }

        if ( totalIps > 255 )
        {
            throw new EnvironmentCreationException(
                    String.format( "Requested IP count %d is more than available 255", totalIps ) );
        }

        //obtain participating peers
        Set<Peer> peers = environment.getPeers();

        //generate p2p secret key
        String sharedKey = DigestUtils.md5Hex( UUID.randomUUID().toString() );

        environment.setP2pKey( sharedKey );


        //p2p setup
        PeerUtil<P2PConfig> p2pUtil = new PeerUtil<>();

        int addressCounter = 0;

        for ( Peer peer : peers )
        {
            P2PConfig config = new P2PConfig( peer.getId(), environment.getId(), environment.getP2PHash(), sharedKey,
                    Common.DEFAULT_P2P_SECRET_KEY_TTL_SEC );

            Set<String> rhIds = peerRhIds.get( peer.getId() );

            for ( String rhId : rhIds )
            {
                config.addRhP2pIp( new RhP2PIpEntity( rhId, p2pAddresses[addressCounter++] ) );
            }

            p2pUtil.addPeerTask( new PeerUtil.PeerTask<>( peer, new SetupP2PConnectionTask( peer, config ) ) );
        }

        PeerUtil.PeerTaskResults<P2PConfig> p2pResults = p2pUtil.executeParallel();

        for ( PeerUtil.PeerTaskResult<P2PConfig> p2pResult : p2pResults.getPeerTaskResults() )
        {
            if ( p2pResult.hasSucceeded() )
            {
                environment.getPeerConf( p2pResult.getPeer().getId() )
                           .addRhP2pIps( p2pResult.getResult().getRhP2pIps() );

                trackerOperation
                        .addLog( String.format( "P2P setup succeeded on peer %s", p2pResult.getPeer().getName() ) );
            }
            else
            {
                trackerOperation.addLog(
                        String.format( "P2P setup failed on peer %s. Reason: %s", p2pResult.getPeer().getName(),
                                p2pResult.getFailureReason() ) );
            }
        }

        if ( p2pResults.hasFailures() )
        {
            throw new EnvironmentCreationException( "Failed to setup P2P connection across all peers" );
        }


        //tunnel setup
        P2pIps p2pIps = environment.getP2pIps();

        PeerUtil<Boolean> tunnelUtil = new PeerUtil<>();

        for ( Peer peer : peers )
        {
            tunnelUtil.addPeerTask(
                    new PeerUtil.PeerTask<>( peer, new SetupTunnelTask( peer, environment.getId(), p2pIps ) ) );
        }

        PeerUtil.PeerTaskResults<Boolean> tunnelResults = tunnelUtil.executeParallel();

        for ( PeerUtil.PeerTaskResult tunnelResult : tunnelResults.getPeerTaskResults() )
        {
            if ( tunnelResult.hasSucceeded() )
            {
                trackerOperation.addLog(
                        String.format( "Tunnel setup succeeded on peer %s", tunnelResult.getPeer().getName() ) );
            }
            else
            {
                trackerOperation.addLog(
                        String.format( "Tunnel setup failed on peer %s. Reason: %s", tunnelResult.getPeer().getName(),
                                tunnelResult.getFailureReason() ) );
            }
        }

        if ( tunnelResults.hasFailures() )
        {
            throw new EnvironmentCreationException( "Failed to setup tunnel across all peers" );
        }
    }
}
