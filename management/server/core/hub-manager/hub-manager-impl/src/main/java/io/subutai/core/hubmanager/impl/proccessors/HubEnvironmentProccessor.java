package io.subutai.core.hubmanager.impl.proccessors;


import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.core.Response;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.http.HttpStatus;

import com.google.common.base.Strings;

import io.subutai.common.network.DomainLoadBalanceStrategy;
import io.subutai.common.network.NetworkResource;
import io.subutai.common.network.ReservedNetworkResources;
import io.subutai.common.peer.ContainerId;
import io.subutai.common.peer.EnvironmentId;
import io.subutai.common.peer.LocalPeer;
import io.subutai.common.peer.PeerException;
import io.subutai.common.peer.PeerId;
import io.subutai.common.security.crypto.pgp.PGPKeyUtil;
import io.subutai.core.environment.api.exception.EnvironmentCreationException;
import io.subutai.core.executor.api.CommandExecutor;
import io.subutai.core.hubmanager.api.HubPluginException;
import io.subutai.core.hubmanager.api.StateLinkProccessor;
import io.subutai.core.hubmanager.impl.ConfigManager;
import io.subutai.core.hubmanager.impl.HubEnvironmentManager;
import io.subutai.core.peer.api.PeerManager;
import io.subutai.hub.share.dto.environment.ContainerStateDto;
import io.subutai.hub.share.dto.environment.EnvironmentDto;
import io.subutai.hub.share.dto.environment.EnvironmentInfoDto;
import io.subutai.hub.share.dto.environment.EnvironmentNodeDto;
import io.subutai.hub.share.dto.environment.EnvironmentNodesDto;
import io.subutai.hub.share.dto.environment.EnvironmentPeerDto;
import io.subutai.hub.share.dto.environment.EnvironmentPeerLogDto.LogEvent;
import io.subutai.hub.share.dto.environment.EnvironmentPeerLogDto.LogType;
import io.subutai.hub.share.json.JsonUtil;


public class HubEnvironmentProccessor implements StateLinkProccessor
{
    private static final Logger LOG = LoggerFactory.getLogger( HubEnvironmentProccessor.class.getName() );

    private static final Pattern ENVIRONMENT_PEER_DATA_PATTERN = Pattern.compile(
            "/rest/v1/environments/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/peers/"
                    + "[a-zA-z0-9]{1,100}" );

    private final ConfigManager configManager;

    private final PeerManager peerManager;

    private final HubEnvironmentManager hubEnvironmentManager;

    private final CommandExecutor commandExecutor;

    private final EnvironmentUserHelper environmentUserHelper;


    public HubEnvironmentProccessor( final HubEnvironmentManager hubEnvironmentManager,
                                     final ConfigManager hConfigManager, final PeerManager peerManager,
                                     CommandExecutor commandExecutor, EnvironmentUserHelper environmentUserHelper )
    {
        this.configManager = hConfigManager;

        this.peerManager = peerManager;

        this.hubEnvironmentManager = hubEnvironmentManager;

        this.commandExecutor = commandExecutor;

        this.environmentUserHelper = environmentUserHelper;
    }


    @Override
    public void processStateLinks( final Set<String> stateLinks ) throws HubPluginException
    {
        for ( String link : stateLinks )
        {
            // Environment Data     GET /rest/v1/environments/{environment-id}/peers/{peer-id}
            Matcher environmentDataMatcher = ENVIRONMENT_PEER_DATA_PATTERN.matcher( link );
            if ( environmentDataMatcher.matches() )
            {
                EnvironmentPeerDto envPeerDto = getEnvPeerDto( link );
                environmentBuildProcess( envPeerDto );
            }
        }
    }


    private EnvironmentPeerDto getEnvPeerDto( String link ) throws HubPluginException
    {
        try
        {
            WebClient client = configManager.getTrustedWebClientWithAuth( link, configManager.getHubIp() );

            LOG.debug( "Getting Environment peer data from Hub..." );

            Response r = client.get();
            byte[] encryptedContent = configManager.readContent( r );
            byte[] plainContent = configManager.getMessenger().consume( encryptedContent );
            EnvironmentPeerDto result = JsonUtil.fromCbor( plainContent, EnvironmentPeerDto.class );

            LOG.debug( "EnvironmentPeerDto: " + result.toString() );
            return result;
        }
        catch ( UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException | PGPException | IOException
                e )
        {
            throw new HubPluginException( "Could not retrieve environment peer data", e );
        }
    }


    private void environmentBuildProcess( final EnvironmentPeerDto peerDto )
    {
        try
        {
            switch ( peerDto.getState() )
            {
                case EXCHANGE_INFO:
                    infoExchange( peerDto );
                    break;
                case RESERVE_NETWORK:
                    reserveNetwork( peerDto );
                    break;
                case SETUP_TUNNEL:
                    setupTunnel( peerDto );
                    break;
                case BUILD_CONTAINER:
                    buildContainers( peerDto );
                    break;
                case CONFIGURE_CONTAINER:
                    configureContainer( peerDto );
                    environmentUserHelper.handleEnvironmentOwnerCreation( peerDto );
                    break;
                case CHANGE_CONTAINER_STATE:
                    controlContainer( peerDto );
                    break;
                case CONFIGURE_DOMAIN:
                    configureDomain( peerDto );
                    break;
                case DELETE_PEER:
                    deletePeer( peerDto );
                    break;
            }
        }
        catch ( Exception e )
        {
            LOG.error( e.getMessage() );
        }
    }


    private void infoExchange( EnvironmentPeerDto peerDto )
    {
        String exchangeURL =
                String.format( "/rest/v1/environments/%s/exchange-info", peerDto.getEnvironmentInfo().getId() );

        EnvironmentId environmentId = new EnvironmentId( peerDto.getEnvironmentInfo().getId() );
        try
        {
            WebClient client = configManager.getTrustedWebClientWithAuth( exchangeURL, configManager.getHubIp() );

            peerDto = hubEnvironmentManager.getReservedNetworkResource( peerDto );
            peerDto.setPublicKey( hubEnvironmentManager.createPeerEnvironmentKeyPair( environmentId ) );

            byte[] cborData = JsonUtil.toCbor( peerDto );
            byte[] encryptedData = configManager.getMessenger().produce( cborData );
            Response r = client.post( encryptedData );

            if ( r.getStatus() == HttpStatus.SC_OK )
            {
                LOG.debug( "Collected data successfully sent to Hub" );
                byte[] encryptedContent = configManager.readContent( r );
                byte[] plainContent = configManager.getMessenger().consume( encryptedContent );
                EnvironmentPeerDto buildDtoResponse = JsonUtil.fromCbor( plainContent, EnvironmentPeerDto.class );

                PGPPublicKeyRing signedKey = PGPKeyUtil.readPublicKeyRing( buildDtoResponse.getPublicKey().getKey() );
                peerManager.getLocalPeer().updatePeerEnvironmentPubKey( environmentId, signedKey );
            }
        }
        catch ( UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException | PGPException | IOException
                e )
        {
            String mgs = "Could not send exchange data to Hub.";
            hubEnvironmentManager
                    .sendLogToHub( peerDto, mgs, e.getMessage(), LogEvent.REQUEST_TO_HUB, LogType.ERROR, null );
            LOG.error( mgs, e );
        }
        catch ( PeerException e )
        {
            String mgs = "Could not save signed key.";
            hubEnvironmentManager.sendLogToHub( peerDto, mgs, e.getMessage(), LogEvent.SUBUTAI, LogType.ERROR, null );
            LOG.error( mgs, e );
        }
        catch ( EnvironmentCreationException e )
        {
            String mgs = "Environment creation exception";
            hubEnvironmentManager.sendLogToHub( peerDto, mgs, e.getMessage(), LogEvent.SUBUTAI, LogType.ERROR, null );
            LOG.error( mgs, e );
        }
    }


    private void reserveNetwork( EnvironmentPeerDto peerDto )
    {
        try
        {
            hubEnvironmentManager.reserveNetworkResource( peerDto );
            peerDto = hubEnvironmentManager.setupP2P( peerDto );
            updateEnvironmentPeerData( peerDto );
        }
        catch ( EnvironmentCreationException e )
        {
            LOG.error( e.getMessage() );
        }
    }


    private void setupTunnel( EnvironmentPeerDto peerDto )
    {
        String setupTunnelDataURL = String.format( "/rest/v1/environments/%s", peerDto.getEnvironmentInfo().getId() );
        try
        {
            WebClient client =
                    configManager.getTrustedWebClientWithAuth( setupTunnelDataURL, configManager.getHubIp() );
            Response r = client.get();
            byte[] encryptedContent = configManager.readContent( r );
            byte[] plainContent = configManager.getMessenger().consume( encryptedContent );
            EnvironmentDto environmentDto = JsonUtil.fromCbor( plainContent, EnvironmentDto.class );
            peerDto.setSetupTunnel( false );
            hubEnvironmentManager.setupTunnel( peerDto, environmentDto );
            updateEnvironmentPeerData( peerDto );
        }
        catch ( UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException | PGPException | IOException
                e )
        {
            String mgs = "Could not get environment data from Hub.";
            hubEnvironmentManager
                    .sendLogToHub( peerDto, mgs, e.getMessage(), LogEvent.REQUEST_TO_HUB, LogType.ERROR, null );
            LOG.error( mgs, e );
        }
    }


    private void buildContainers( EnvironmentPeerDto peerDto )
    {
        String containerDataURL = String.format( "/rest/v1/environments/%s/container-build-workflow",
                peerDto.getEnvironmentInfo().getId() );
        try
        {
            WebClient client = configManager.getTrustedWebClientWithAuth( containerDataURL, configManager.getHubIp() );
            Response r = client.get();
            byte[] encryptedContent = configManager.readContent( r );
            byte[] plainContent = configManager.getMessenger().consume( encryptedContent );
            EnvironmentNodesDto envNodes = JsonUtil.fromCbor( plainContent, EnvironmentNodesDto.class );

            hubEnvironmentManager.prepareTemplates( peerDto, envNodes );
            EnvironmentNodesDto updatedNodes = hubEnvironmentManager.cloneContainers( peerDto, envNodes );

            byte[] cborData = JsonUtil.toCbor( updatedNodes );
            byte[] encryptedData = configManager.getMessenger().produce( cborData );
            Response response = client.put( encryptedData );

            if ( response.getStatus() == HttpStatus.SC_NO_CONTENT )
            {
                LOG.debug( "env_via_hub: Environment successfully build!!!" );
            }
        }
        catch ( UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException | PGPException | IOException
                e )
        {
            String mgs = "Could not get container creation data from Hub.";
            hubEnvironmentManager
                    .sendLogToHub( peerDto, mgs, e.getMessage(), LogEvent.REQUEST_TO_HUB, LogType.ERROR, null );
            LOG.error( mgs, e );
        }
        catch ( EnvironmentCreationException e )
        {
            LOG.error( e.getMessage() );
        }
    }


    private void configureContainer( EnvironmentPeerDto peerDto )
    {
        String configContainer = String.format( "/rest/v1/environments/%s/container-configuration",
                peerDto.getEnvironmentInfo().getId() );
        try
        {
            EnvironmentDto environmentDto = getEnvironmentDto( peerDto.getEnvironmentInfo().getId() );

            peerDto = hubEnvironmentManager.configureSsh( peerDto, environmentDto );
            hubEnvironmentManager.configureHash( peerDto, environmentDto );

            WebClient clientUpdate =
                    configManager.getTrustedWebClientWithAuth( configContainer, configManager.getHubIp() );

            byte[] cborData = JsonUtil.toCbor( peerDto );
            byte[] encryptedData = configManager.getMessenger().produce( cborData );

            Response response = clientUpdate.put( encryptedData );
            if ( response.getStatus() == HttpStatus.SC_NO_CONTENT )
            {
                LOG.debug( "SSH configuration successfully done" );
            }
        }
        catch ( Exception e )
        {
            String mgs = "Could not configure SSH/Hash";
            LOG.error( mgs, e );
        }
    }


    private void controlContainer( EnvironmentPeerDto peerDto )
    {
        String controlContainerPath =
                String.format( "/rest/v1/environments/%s/peers/%s/container", peerDto.getEnvironmentInfo().getId(),
                        peerDto.getPeerId() );
        LocalPeer localPeer = peerManager.getLocalPeer();

        EnvironmentDto environmentDto = getEnvironmentDto( peerDto.getEnvironmentInfo().getId() );
        if ( environmentDto != null )
        {
            for ( EnvironmentNodesDto nodesDto : environmentDto.getNodes() )
            {
                PeerId peerId = new PeerId( nodesDto.getPeerId() );
                EnvironmentId envId = new EnvironmentId( nodesDto.getEnvironmentId() );
                if ( nodesDto.getPeerId().equals( localPeer.getId() ) )
                {
                    for ( EnvironmentNodeDto nodeDto : nodesDto.getNodes() )
                    {
                        ContainerId containerId =
                                new ContainerId( nodeDto.getContainerId(), nodeDto.getHostName(), peerId, envId );
                        try
                        {
                            if ( nodeDto.getState().equals( ContainerStateDto.STOPPING ) )
                            {
                                localPeer.stopContainer( containerId );
                                nodeDto.setState( ContainerStateDto.STOPPED );
                            }
                            if ( nodeDto.getState().equals( ContainerStateDto.STARTING ) )
                            {
                                localPeer.startContainer( containerId );
                                nodeDto.setState( ContainerStateDto.RUNNING );
                            }
                            if ( nodeDto.getState().equals( ContainerStateDto.ABORTING ) )
                            {
                                localPeer.destroyContainer( containerId );
                                nodeDto.setState( ContainerStateDto.FROZEN );
                            }
                        }
                        catch ( PeerException e )
                        {
                            String mgs = "Could not change container state";
                            hubEnvironmentManager
                                    .sendLogToHub( peerDto, mgs, e.getMessage(), LogEvent.CONTAINER, LogType.ERROR,
                                            containerId.getId() );
                            LOG.error( mgs, e );
                        }
                    }

                    try
                    {
                        WebClient clientUpdate = configManager
                                .getTrustedWebClientWithAuth( controlContainerPath, configManager.getHubIp() );

                        byte[] cborData = JsonUtil.toCbor( nodesDto );
                        byte[] encryptedData = configManager.getMessenger().produce( cborData );

                        Response response = clientUpdate.put( encryptedData );
                        if ( response.getStatus() == HttpStatus.SC_NO_CONTENT )
                        {
                            LOG.debug( "Container successfully updated" );
                        }
                    }
                    catch ( Exception e )
                    {
                        String mgs = "Could not send containers state to hub";
                        hubEnvironmentManager
                                .sendLogToHub( peerDto, mgs, e.getMessage(), LogEvent.REQUEST_TO_HUB, LogType.ERROR,
                                        null );
                        LOG.error( mgs, e );
                    }
                }
            }
        }
    }


    private void configureDomain( EnvironmentPeerDto peerDto )
    {
        LocalPeer localPeer = peerManager.getLocalPeer();
        EnvironmentInfoDto env = peerDto.getEnvironmentInfo();
        String domainUpdatePath =
                String.format( "/rest/v1/environments/%s/peers/%s/domain", peerDto.getEnvironmentInfo().getId(),
                        peerDto.getPeerId() );
        try
        {
            //TODO balanceStrategy should come from HUB
            EnvironmentDto environmentDto = getEnvironmentDto( peerDto.getEnvironmentInfo().getId() );
            boolean assign = !Strings.isNullOrEmpty( env.getDomainName() );
            assert environmentDto != null;
            if ( assign )
            {
                DomainLoadBalanceStrategy balanceStrategy = DomainLoadBalanceStrategy.LOAD_BALANCE;
                localPeer.setVniDomain( env.getVni(), env.getDomainName(), balanceStrategy, env.getSslCertPath() );
                for ( EnvironmentNodesDto nodesDto : environmentDto.getNodes() )
                {
                    if ( nodesDto.getPeerId().equals( localPeer.getId() ) )
                    {
                        for ( EnvironmentNodeDto nodeDto : nodesDto.getNodes() )
                        {
                            try
                            {
                                localPeer.addIpToVniDomain( nodeDto.getIp(), env.getVni() );
                            }
                            catch ( Exception e )
                            {
                                LOG.error( "Could not add container IP to domain: " + nodeDto.getContainerName() );
                            }
                        }
                    }
                }
            }
            else
            {
                localPeer.removeVniDomain( env.getVni() );
            }

            WebClient clientUpdate =
                    configManager.getTrustedWebClientWithAuth( domainUpdatePath, configManager.getHubIp() );
            byte[] cborData = JsonUtil.toCbor( peerDto );
            byte[] encryptedData = configManager.getMessenger().produce( cborData );
            Response response = clientUpdate.put( encryptedData );
            if ( response.getStatus() == HttpStatus.SC_NO_CONTENT )
            {
                LOG.debug( "Domain configuration successfully done" );
            }
        }
        catch ( Exception e )
        {
            String mgs = "Could not configure domain name";
            hubEnvironmentManager.sendLogToHub( peerDto, mgs, e.getMessage(), LogEvent.SUBUTAI, LogType.ERROR, null );
            LOG.error( mgs, e );
        }
    }


    private void deletePeer( EnvironmentPeerDto peerDto )
    {
        String urlFormat = "/rest/v1/environments/%s/peers/%s";

        String containerDestroyStateURL =
                String.format( urlFormat, peerDto.getEnvironmentInfo().getId(), peerDto.getPeerId() );

        LocalPeer localPeer = peerManager.getLocalPeer();

        EnvironmentInfoDto env = peerDto.getEnvironmentInfo();

        try
        {
            EnvironmentId envId = new EnvironmentId( env.getId() );

            localPeer.cleanupEnvironment( envId );
            ReservedNetworkResources  reservedNetworkResources = localPeer.getReservedNetworkResources();
            for ( NetworkResource networkResource: reservedNetworkResources.getNetworkResources())
            {
                if (networkResource.getEnvironmentId().equals( env.getId() ))
                {
                    throw new Exception( "Environment network resources are not cleaned yet." );
                }
            }

            environmentUserHelper.handleEnvironmentOwnerDeletion( peerDto );

            WebClient client =
                    configManager.getTrustedWebClientWithAuth( containerDestroyStateURL, configManager.getHubIp() );

            Response response = client.delete();

            if ( response.getStatus() == HttpStatus.SC_NO_CONTENT )
            {
                LOG.debug( "Environment data cleaned successfully" );
            }
        }
        catch ( Exception e )
        {
            String mgs = "Could not clean environment";
            hubEnvironmentManager.sendLogToHub( peerDto, mgs, e.getMessage(), LogEvent.SUBUTAI, LogType.ERROR, null );
            LOG.error( mgs, e );
        }
    }


    private EnvironmentDto getEnvironmentDto( String envId )
    {
        String envDataPath = String.format( "/rest/v1/environments/%s", envId );
        try
        {
            WebClient client = configManager.getTrustedWebClientWithAuth( envDataPath, configManager.getHubIp() );
            Response r = client.get();
            byte[] encryptedContent = configManager.readContent( r );
            byte[] plainContent = configManager.getMessenger().consume( encryptedContent );
            return JsonUtil.fromCbor( plainContent, EnvironmentDto.class );
        }
        catch ( Exception e )
        {
            LOG.error( "Could not get environment data from Hub", e );
        }
        return null;
    }


    private void updateEnvironmentPeerData( EnvironmentPeerDto peerDto )
    {
        try
        {
            String envPeerDataUrl =
                    String.format( "/rest/v1/environments/%s/peers/%s", peerDto.getEnvironmentInfo().getId(),
                            peerManager.getLocalPeer().getId() );
            WebClient client = configManager.getTrustedWebClientWithAuth( envPeerDataUrl, configManager.getHubIp() );

            byte[] cborData = JsonUtil.toCbor( peerDto );
            byte[] encryptedData = configManager.getMessenger().produce( cborData );
            Response r = client.put( encryptedData );
            if ( r.getStatus() == HttpStatus.SC_OK )
            {
                String mgs = "Environment peer data successfully sent to hub";
                hubEnvironmentManager.sendLogToHub( peerDto, mgs, null, LogEvent.REQUEST_TO_HUB, LogType.DEBUG, null );
                LOG.debug( mgs );
            }
        }
        catch ( Exception e )
        {
            String mgs = "Could not sent environment peer data to hub.";
            hubEnvironmentManager
                    .sendLogToHub( peerDto, mgs, e.getMessage(), LogEvent.REQUEST_TO_HUB, LogType.ERROR, null );
            LOG.error( mgs, e.getMessage() );
        }
    }
}
