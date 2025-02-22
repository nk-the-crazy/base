package io.subutai.core.peer.impl;


import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;

import io.subutai.common.command.CommandCallback;
import io.subutai.common.command.CommandException;
import io.subutai.common.command.CommandRequest;
import io.subutai.common.command.CommandResult;
import io.subutai.common.command.CommandResultImpl;
import io.subutai.common.command.CommandStatus;
import io.subutai.common.command.RequestBuilder;
import io.subutai.common.environment.Containers;
import io.subutai.common.environment.CreateEnvironmentContainerGroupRequest;
import io.subutai.common.environment.CreateEnvironmentContainerResponseCollector;
import io.subutai.common.environment.HostAddresses;
import io.subutai.common.environment.PrepareTemplatesRequest;
import io.subutai.common.environment.PrepareTemplatesResponseCollector;
import io.subutai.common.environment.SshPublicKeys;
import io.subutai.common.host.ContainerHostState;
import io.subutai.common.host.HostId;
import io.subutai.common.host.HostInterfaces;
import io.subutai.common.metric.ProcessResourceUsage;
import io.subutai.common.metric.ResourceHostMetrics;
import io.subutai.common.network.NetworkResourceImpl;
import io.subutai.common.network.UsedNetworkResources;
import io.subutai.common.peer.AlertEvent;
import io.subutai.common.peer.ContainerHost;
import io.subutai.common.peer.ContainerId;
import io.subutai.common.peer.EnvironmentContainerHost;
import io.subutai.common.peer.EnvironmentId;
import io.subutai.common.peer.Host;
import io.subutai.common.peer.MessageRequest;
import io.subutai.common.peer.MessageResponse;
import io.subutai.common.peer.Payload;
import io.subutai.common.peer.PeerException;
import io.subutai.common.peer.PeerInfo;
import io.subutai.common.peer.RecipientType;
import io.subutai.common.peer.RemotePeer;
import io.subutai.common.peer.Timeouts;
import io.subutai.common.protocol.P2PConfig;
import io.subutai.common.protocol.P2PCredentials;
import io.subutai.common.protocol.P2pIps;
import io.subutai.common.protocol.ReverseProxyConfig;
import io.subutai.common.protocol.TemplateKurjun;
import io.subutai.common.quota.ContainerQuota;
import io.subutai.common.resource.HistoricalMetrics;
import io.subutai.common.resource.PeerResources;
import io.subutai.common.security.PublicKeyContainer;
import io.subutai.common.util.CollectionUtil;
import io.subutai.common.util.JsonUtil;
import io.subutai.core.messenger.api.Message;
import io.subutai.core.messenger.api.MessageException;
import io.subutai.core.messenger.api.Messenger;
import io.subutai.core.peer.impl.command.BlockingCommandCallback;
import io.subutai.core.peer.impl.command.CommandResponseListener;
import io.subutai.core.peer.impl.request.MessageResponseListener;
import io.subutai.core.security.api.SecurityManager;


/**
 * Remote Peer implementation
 */
@PermitAll
public class RemotePeerImpl implements RemotePeer
{
    private static final Logger LOG = LoggerFactory.getLogger( RemotePeerImpl.class );

    private final String localPeerId;
    private final SecurityManager securityManager;
    protected final PeerInfo peerInfo;
    protected final Messenger messenger;
    private final CommandResponseListener commandResponseListener;
    private final MessageResponseListener messageResponseListener;
    private final Object provider;
    protected JsonUtil jsonUtil = new JsonUtil();


    public RemotePeerImpl( String localPeerId, SecurityManager securityManager, final PeerInfo peerInfo,
                           final Messenger messenger, CommandResponseListener commandResponseListener,
                           MessageResponseListener messageResponseListener, Object provider )
    {
        Preconditions.checkArgument( !Strings.isNullOrEmpty( localPeerId ) );
        Preconditions.checkNotNull( securityManager );
        Preconditions.checkNotNull( peerInfo );
        Preconditions.checkNotNull( messenger );
        Preconditions.checkNotNull( commandResponseListener );
        Preconditions.checkNotNull( messageResponseListener );

        this.localPeerId = localPeerId;
        this.securityManager = securityManager;
        this.peerInfo = peerInfo;
        this.messenger = messenger;
        this.commandResponseListener = commandResponseListener;
        this.messageResponseListener = messageResponseListener;
        this.provider = provider;
    }


    @Override
    public String getId()
    {
        return peerInfo.getId();
    }


    @Override
    public PeerInfo check() throws PeerException
    {
        PeerInfo response = new PeerWebClient( peerInfo, provider ).getInfo();

        if ( !peerInfo.getId().equals( response.getId() ) )
        {
            throw new PeerException( String.format(
                    "Remote peer check failed. Id of the remote peer %s changed. Please verify the remote peer.",
                    peerInfo.getId() ) );
        }

        return response;
    }


    @Override
    public boolean isOnline()
    {
        try
        {
            check();
            return true;
        }
        catch ( PeerException e )
        {
            LOG.error( e.getMessage(), e );
            return false;
        }
    }


    @Override
    public boolean isLocal()
    {
        return false;
    }


    @Override
    public String getName()
    {
        return peerInfo.getName();
    }


    @Override
    public String getOwnerId()
    {
        return peerInfo.getOwnerId();
    }


    @Override
    public PeerInfo getPeerInfo()
    {
        return peerInfo;
    }


    @Override
    public TemplateKurjun getTemplate( final String templateName ) throws PeerException
    {
        Preconditions.checkArgument( !Strings.isNullOrEmpty( templateName ), "Invalid template name" );

        return new PeerWebClient( peerInfo, provider ).getTemplate( templateName );
    }


    //********** ENVIRONMENT SPECIFIC REST *************************************


    @RolesAllowed( "Environment-Management|Update" )
    @Override
    public void startContainer( final ContainerId containerId ) throws PeerException
    {
        Preconditions.checkNotNull( containerId, "Container id is null" );
        Preconditions.checkArgument( containerId.getPeerId().getId().equals( peerInfo.getId() ) );

        new EnvironmentWebClient( peerInfo, provider ).startContainer( containerId );
    }


    @RolesAllowed( "Environment-Management|Update" )
    @Override
    public void stopContainer( final ContainerId containerId ) throws PeerException
    {
        Preconditions.checkNotNull( containerId, "Container id is null" );
        Preconditions.checkArgument( containerId.getPeerId().getId().equals( peerInfo.getId() ) );

        new EnvironmentWebClient( peerInfo, provider ).stopContainer( containerId );
    }


    @RolesAllowed( "Environment-Management|Delete" )
    @Override
    public void destroyContainer( final ContainerId containerId ) throws PeerException
    {
        Preconditions.checkNotNull( containerId, "Container id is null" );
        Preconditions.checkArgument( containerId.getPeerId().getId().equals( peerInfo.getId() ) );

        new EnvironmentWebClient( peerInfo, provider ).destroyContainer( containerId );
    }


    @RolesAllowed( "Environment-Management|Delete" )
    @Override
    public boolean isConnected( final HostId hostId )
    {
        Preconditions.checkNotNull( hostId, "Host id is null" );

        try
        {
            return hostId instanceof ContainerId && ContainerHostState.RUNNING
                    .equals( getContainerState( ( ContainerId ) hostId ) );
        }
        catch ( PeerException e )
        {
            LOG.error( "Error getting container state #isConnected", e );
            return false;
        }
    }


    @PermitAll
    @Override
    public ProcessResourceUsage getProcessResourceUsage( final ContainerId containerId, int pid ) throws PeerException
    {
        Preconditions.checkNotNull( containerId, "Container id is null" );
        Preconditions.checkArgument( containerId.getPeerId().getId().equals( peerInfo.getId() ) );
        Preconditions.checkArgument( pid > 0, "Process pid must be greater than 0" );

        return new EnvironmentWebClient( peerInfo, provider ).getProcessResourceUsage( containerId, pid );
    }


    @Override
    public ContainerHostState getContainerState( final ContainerId containerId ) throws PeerException
    {
        Preconditions.checkNotNull( containerId, "Container id is null" );
        Preconditions.checkArgument( containerId.getPeerId().getId().equals( peerInfo.getId() ) );

        return new EnvironmentWebClient( peerInfo, provider ).getState( containerId );
    }


    @Override
    public Containers getEnvironmentContainers( final EnvironmentId environmentId ) throws PeerException
    {
        Preconditions.checkNotNull( environmentId, "Environment id is null" );

        return new PeerWebClient( peerInfo, provider ).getEnvironmentContainers( environmentId );
    }


    @Override
    public Set<Integer> getCpuSet( final ContainerHost containerHost ) throws PeerException
    {
        Preconditions.checkNotNull( containerHost, "Container host is null" );
        Preconditions.checkArgument( containerHost instanceof EnvironmentContainerHost );

        return new EnvironmentWebClient( peerInfo, provider ).getCpuSet( containerHost.getContainerId() );
    }


    @RolesAllowed( "Environment-Management|Update" )
    @Override
    public void setCpuSet( final ContainerHost containerHost, final Set<Integer> cpuSet ) throws PeerException
    {
        Preconditions.checkNotNull( containerHost, "Container host is null" );
        Preconditions.checkArgument( containerHost instanceof EnvironmentContainerHost );
        Preconditions.checkArgument( !CollectionUtil.isCollectionEmpty( cpuSet ), "Empty cpu set" );

        new EnvironmentWebClient( peerInfo, provider ).setCpuSet( containerHost.getContainerId(), cpuSet );
    }


    @RolesAllowed( "Environment-Management|Update" )
    @Override
    public SshPublicKeys generateSshKeyForEnvironment( final EnvironmentId environmentId ) throws PeerException
    {
        Preconditions.checkNotNull( environmentId, "Environment id is null" );

        return new EnvironmentWebClient( peerInfo, provider ).generateSshKeysForEnvironment( environmentId );
    }


    @RolesAllowed( "Environment-Management|Update" )
    @Override
    public void configureSshInEnvironment( final EnvironmentId environmentId, final SshPublicKeys sshPublicKeys )
            throws PeerException
    {
        Preconditions.checkNotNull( environmentId, "Environment id is null" );
        Preconditions.checkNotNull( sshPublicKeys, "SshPublicKey is null" );
        Preconditions.checkArgument( !sshPublicKeys.isEmpty(), "No ssh keys" );

        new EnvironmentWebClient( peerInfo, provider ).configureSshInEnvironment( environmentId, sshPublicKeys );
    }


    @Override
    public void addSshKey( final EnvironmentId environmentId, final String sshPublicKey ) throws PeerException
    {
        Preconditions.checkNotNull( environmentId, "Environment id is null" );
        Preconditions.checkArgument( !Strings.isNullOrEmpty( sshPublicKey ), "Invalid ssh key" );

        new EnvironmentWebClient( peerInfo, provider ).addSshKey( environmentId, sshPublicKey );
    }


    @Override
    public void removeSshKey( final EnvironmentId environmentId, final String sshPublicKey ) throws PeerException
    {
        Preconditions.checkNotNull( environmentId, "Environment id is null" );
        Preconditions.checkArgument( !Strings.isNullOrEmpty( sshPublicKey ), "Invalid ssh key" );

        new EnvironmentWebClient( peerInfo, provider ).removeSshKey( environmentId, sshPublicKey );
    }


    @RolesAllowed( "Environment-Management|Update" )
    @Override
    public void configureHostsInEnvironment( final EnvironmentId environmentId, final HostAddresses hostAddresses )
            throws PeerException
    {
        Preconditions.checkNotNull( environmentId, "Environment id is null" );
        Preconditions.checkNotNull( hostAddresses, "Invalid HostAdresses" );
        Preconditions.checkArgument( !hostAddresses.isEmpty(), "No host addresses" );

        new EnvironmentWebClient( peerInfo, provider ).configureHostsInEnvironment( environmentId, hostAddresses );
    }


    @Override
    public ContainerQuota getQuota( final ContainerId containerId ) throws PeerException
    {
        Preconditions.checkNotNull( containerId, "Container id is null" );
        Preconditions.checkArgument( containerId.getPeerId().getId().equals( peerInfo.getId() ) );

        return new EnvironmentWebClient( peerInfo, provider ).getQuota( containerId );
    }


    @Override
    public void setQuota( final ContainerId containerId, final ContainerQuota containerQuota ) throws PeerException
    {
        Preconditions.checkNotNull( containerId, "Container id is null" );
        Preconditions.checkArgument( containerId.getPeerId().getId().equals( peerInfo.getId() ) );
        Preconditions.checkNotNull( containerQuota, "Container quota is null" );

        new EnvironmentWebClient( peerInfo, provider ).setQuota( containerId, containerQuota );
    }


    @Override
    public CommandResult execute( final RequestBuilder requestBuilder, final Host host ) throws CommandException
    {
        return execute( requestBuilder, host, null );
    }


    @Override
    public CommandResult execute( final RequestBuilder requestBuilder, final Host host, final CommandCallback callback )
            throws CommandException
    {
        Preconditions.checkNotNull( requestBuilder, "Invalid request" );
        Preconditions.checkNotNull( host, "Invalid host" );

        BlockingCommandCallback blockingCommandCallback = getBlockingCommandCallback( callback );

        executeAsync( requestBuilder, host, blockingCommandCallback, blockingCommandCallback.getCompletionSemaphore() );

        CommandResult commandResult = blockingCommandCallback.getCommandResult();

        if ( commandResult == null )
        {
            commandResult = new CommandResultImpl( null, null, null, CommandStatus.TIMEOUT );
        }

        return commandResult;
    }


    protected BlockingCommandCallback getBlockingCommandCallback( CommandCallback callback )
    {
        return new BlockingCommandCallback( callback );
    }


    @Override
    public void executeAsync( final RequestBuilder requestBuilder, final Host host, final CommandCallback callback )
            throws CommandException
    {
        executeAsync( requestBuilder, host, callback, null );
    }


    @Override
    public void executeAsync( final RequestBuilder requestBuilder, final Host host ) throws CommandException
    {
        executeAsync( requestBuilder, host, null );
    }


    protected void executeAsync( final RequestBuilder requestBuilder, final Host host, final CommandCallback callback,
                                 Semaphore semaphore ) throws CommandException
    {
        Preconditions.checkNotNull( requestBuilder, "Invalid request" );
        Preconditions.checkNotNull( host, "Invalid host" );

        if ( !host.isConnected() )
        {
            throw new CommandException( "Host disconnected." );
        }

        if ( !( host instanceof ContainerHost ) )
        {
            throw new CommandException( "Operation not allowed" );
        }

        EnvironmentId environmentId = ( ( EnvironmentContainerHost ) host ).getEnvironmentId();
        CommandRequest request = new CommandRequest( requestBuilder, host.getId(), environmentId.getId() );
        //cache callback
        commandResponseListener.addCallback( request.getRequestId(), callback, requestBuilder.getTimeout(), semaphore );

        //send command request to remote peer counterpart
        try
        {
            //*********construct Secure Header ****************************
            Map<String, String> headers = Maps.newHashMap();
            //************************************************************************


            sendRequest( request, RecipientType.COMMAND_REQUEST.name(), Timeouts.COMMAND_REQUEST_MESSAGE_TIMEOUT,
                    headers );
        }
        catch ( PeerException e )
        {
            throw new CommandException( e );
        }
    }


    @Override
    public <T, V> V sendRequest( final T request, final String recipient, final int requestTimeout,
                                 Class<V> responseType, int responseTimeout, final Map<String, String> headers )
            throws PeerException
    {
        Preconditions.checkArgument( responseTimeout > 0, "Invalid response timeout" );
        Preconditions.checkNotNull( responseType, "Invalid response type" );

        //send request
        MessageRequest messageRequest = sendRequestInternal( request, recipient, requestTimeout, headers );

        //wait for response here
        MessageResponse messageResponse =
                messageResponseListener.waitResponse( messageRequest, requestTimeout, responseTimeout );

        LOG.debug( String.format( "%s", messageResponse ) );
        if ( messageResponse != null )
        {
            if ( messageResponse.getException() != null )
            {
                throw new PeerException( messageResponse.getException() );
            }
            else if ( messageResponse.getPayload() != null )
            {
                LOG.debug( String.format( "Trying get response object: %s", responseType ) );
                final V message = messageResponse.getPayload().getMessage( responseType );
                LOG.debug( String.format( "Response object: %s", message ) );
                return message;
            }
        }

        return null;
    }


    @Override
    public <T> void sendRequest( final T request, final String recipient, final int requestTimeout,
                                 final Map<String, String> headers ) throws PeerException
    {

        sendRequestInternal( request, recipient, requestTimeout, headers );
    }


    protected <T> MessageRequest sendRequestInternal( final T request, final String recipient, final int requestTimeout,
                                                      final Map<String, String> headers ) throws PeerException
    {
        Preconditions.checkNotNull( request, "Invalid request" );
        Preconditions.checkArgument( !Strings.isNullOrEmpty( recipient ), "Invalid recipient" );
        Preconditions.checkArgument( requestTimeout > 0, "Invalid request timeout" );

        MessageRequest messageRequest = new MessageRequest( new Payload( request, localPeerId ), recipient, headers );
        Message message = messenger.createMessage( messageRequest );

        messageRequest.setMessageId( message.getId() );

        try
        {
            messenger.sendMessage( this, message, RecipientType.PEER_REQUEST_LISTENER.name(), requestTimeout, headers );
        }
        catch ( MessageException e )
        {
            throw new PeerException( e );
        }

        return messageRequest;
    }


    @RolesAllowed( "Environment-Management|Write" )
    @Override
    public CreateEnvironmentContainerResponseCollector createEnvironmentContainerGroup(
            final CreateEnvironmentContainerGroupRequest request ) throws PeerException
    {
        Preconditions.checkNotNull( request, "Invalid request" );


        //*********construct Secure Header ****************************
        Map<String, String> headers = Maps.newHashMap();
        //************************************************************************

        CreateEnvironmentContainerResponseCollector response =
                sendRequest( request, RecipientType.CREATE_ENVIRONMENT_CONTAINER_GROUP_REQUEST.name(),
                        Timeouts.CREATE_CONTAINER_REQUEST_TIMEOUT, CreateEnvironmentContainerResponseCollector.class,
                        Timeouts.CREATE_CONTAINER_RESPONSE_TIMEOUT, headers );

        if ( response != null )
        {
            return response;
        }
        else
        {
            throw new PeerException( "Command timed out" );
        }
    }


    @RolesAllowed( "Environment-Management|Write" )
    @Override
    public PrepareTemplatesResponseCollector prepareTemplates( final PrepareTemplatesRequest request )
            throws PeerException
    {
        Preconditions.checkNotNull( request, "Invalid request" );


        //*********construct Secure Header ****************************
        Map<String, String> headers = Maps.newHashMap();
        //************************************************************************

        PrepareTemplatesResponseCollector response =
                sendRequest( request, RecipientType.PREPARE_TEMPLATE_REQUEST.name(),
                        Timeouts.CREATE_CONTAINER_REQUEST_TIMEOUT, PrepareTemplatesResponseCollector.class,
                        Timeouts.CREATE_CONTAINER_RESPONSE_TIMEOUT, headers );

        if ( response != null )
        {
            return response;
        }
        else
        {
            throw new PeerException( "Command timed out" );
        }
    }


    @Override
    public UsedNetworkResources getUsedNetworkResources() throws PeerException
    {
        return new PeerWebClient( peerInfo, provider ).getUsedNetResources();
    }


    @RolesAllowed( "Environment-Management|Write" )
    @Override
    public void setupTunnels( final P2pIps p2pIps, final String environmentId ) throws PeerException
    {
        Preconditions.checkNotNull( p2pIps, "Invalid peer ips set" );
        Preconditions.checkNotNull( environmentId, "Invalid environment id" );

        new PeerWebClient( peerInfo, provider ).setupTunnels( p2pIps, environmentId );
    }


    @Override
    public PublicKeyContainer createPeerEnvironmentKeyPair( EnvironmentId environmentId ) throws PeerException
    {
        Preconditions.checkNotNull( environmentId, "Invalid environmentId" );

        return new PeerWebClient( peerInfo, provider ).createEnvironmentKeyPair( environmentId );
    }


    @Override
    public void updatePeerEnvironmentPubKey( final EnvironmentId environmentId, final PGPPublicKeyRing publicKeyRing )
            throws PeerException
    {
        Preconditions.checkNotNull( environmentId, "Invalid environmentId" );
        Preconditions.checkNotNull( publicKeyRing, "Public key ring is null" );


        try
        {
            String exportedPubKeyRing =
                    securityManager.getEncryptionTool().armorByteArrayToString( publicKeyRing.getEncoded() );
            final PublicKeyContainer publicKeyContainer =
                    new PublicKeyContainer( environmentId.getId(), publicKeyRing.getPublicKey().getFingerprint(),
                            exportedPubKeyRing );
            new PeerWebClient( peerInfo, provider ).updateEnvironmentPubKey( publicKeyContainer );
        }
        catch ( IOException | PGPException e )
        {
            throw new PeerException( e.getMessage() );
        }
    }


    @Override
    public void addPeerEnvironmentPubKey( final String keyId, final PGPPublicKeyRing pek ) throws PeerException
    {
        Preconditions.checkNotNull( keyId, "Invalid key ID" );
        Preconditions.checkNotNull( pek, "Public key ring is null" );


        try
        {
            String exportedPubKeyRing = securityManager.getEncryptionTool().armorByteArrayToString( pek.getEncoded() );
            new PeerWebClient( peerInfo, provider ).addPeerEnvironmentPubKey( keyId, exportedPubKeyRing );
        }
        catch ( IOException | PGPException e )
        {
            throw new PeerException( e.getMessage() );
        }
    }


    @Override
    public HostInterfaces getInterfaces() throws PeerException
    {
        return new PeerWebClient( peerInfo, provider ).getInterfaces();
    }


    @Override
    public void reserveNetworkResource( final NetworkResourceImpl networkResource ) throws PeerException
    {
        Preconditions.checkNotNull( networkResource );

        new PeerWebClient( peerInfo, provider ).reserveNetworkResource( networkResource );
    }


    @Override
    public void resetSwarmSecretKey( final P2PCredentials p2PCredentials ) throws PeerException
    {
        Preconditions.checkNotNull( p2PCredentials, "Invalid p2p credentials" );

        new PeerWebClient( peerInfo, provider )
                .resetP2PSecretKey( p2PCredentials.getP2pHash(), p2PCredentials.getP2pSecretKey(),
                        p2PCredentials.getP2pTtlSeconds() );
    }


    @Override
    public void joinP2PSwarm( final P2PConfig config ) throws PeerException
    {
        Preconditions.checkNotNull( config, "Invalid p2p config" );

        new PeerWebClient( peerInfo, provider ).joinP2PSwarm( config );
    }


    @Override
    public void joinOrUpdateP2PSwarm( final P2PConfig config ) throws PeerException
    {
        Preconditions.checkNotNull( config, "Invalid p2p config" );

        new PeerWebClient( peerInfo, provider ).joinOrUpdateP2PSwarm( config );
    }


    @Override
    public void cleanupEnvironment( final EnvironmentId environmentId ) throws PeerException
    {
        Preconditions.checkNotNull( environmentId, "Invalid environment ID" );
        new PeerWebClient( peerInfo, provider ).cleanupEnvironment( environmentId );
    }


    @Override
    public HostId getResourceHostIdByContainerId( final ContainerId containerId ) throws PeerException
    {
        Preconditions.checkNotNull( containerId, "Container id is null" );
        Preconditions.checkNotNull( containerId.getEnvironmentId(), "Environment id is null" );
        Preconditions.checkArgument( containerId.getPeerId().getId().equals( peerInfo.getId() ) );

        return new EnvironmentWebClient( peerInfo, provider ).getResourceHostIdByContainerId( containerId );
    }


    @Override
    public ResourceHostMetrics getResourceHostMetrics() throws PeerException
    {
        return new PeerWebClient( peerInfo, provider ).getResourceHostMetrics();
    }


    @Override
    public void alert( final AlertEvent alert ) throws PeerException
    {
        Preconditions.checkNotNull( alert );

        new PeerWebClient( peerInfo, provider ).alert( alert );
    }


    @Override
    public HistoricalMetrics getHistoricalMetrics( final String hostname, final Date startTime, final Date endTime )
            throws PeerException
    {
        Preconditions.checkArgument( !Strings.isNullOrEmpty( hostname ) );
        Preconditions.checkNotNull( startTime );
        Preconditions.checkNotNull( endTime );

        return new PeerWebClient( peerInfo, provider ).getHistoricalMetrics( hostname, startTime, endTime );
    }


    @Override
    public PeerResources getResourceLimits( final String peerId ) throws PeerException
    {
        Preconditions.checkArgument( !Strings.isNullOrEmpty( peerId ) );

        return new PeerWebClient( peerInfo, provider ).getResourceLimits( peerId );
    }


    @Override
    public void addReverseProxy( final ReverseProxyConfig reverseProxyConfig ) throws PeerException
    {
        new EnvironmentWebClient( peerInfo, provider ).addReverseProxy( reverseProxyConfig );
    }


    @Override
    public boolean equals( final Object o )
    {
        if ( this == o )
        {
            return true;
        }
        if ( !( o instanceof RemotePeerImpl ) )
        {
            return false;
        }

        final RemotePeerImpl that = ( RemotePeerImpl ) o;

        return getId().equals( that.getId() );
    }


    @Override
    public int hashCode()
    {
        return getId().hashCode();
    }
}
