package io.subutai.core.registration.cli;


import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;

import io.subutai.core.identity.rbac.cli.SubutaiShellCommandSupport;
import io.subutai.core.registration.api.RegistrationManager;
import io.subutai.core.registration.api.exception.NodeRegistrationException;
import io.subutai.core.registration.api.service.ContainerToken;


@Command( scope = "node", name = "verify", description = "Verifies container token" )
public class VerifyContainerToken extends SubutaiShellCommandSupport
{
    @Argument( index = 0, name = "token", multiValued = false, required = true, description = "Token" )
    private String token;

    @Argument( index = 1, name = "token", multiValued = false, required = true, description = "Token" )
    private String containerHostId;

    @Argument( index = 2, name = "publicKey", multiValued = false, required = true, description = "Container public "
            + "key" )
    private String publicKey;

    private RegistrationManager registrationManager;


    public VerifyContainerToken( final RegistrationManager registrationManager )
    {
        this.registrationManager = registrationManager;
    }


    @Override
    protected Object doExecute() throws Exception
    {
        try
        {
            ContainerToken containerToken = registrationManager.verifyToken( token, containerHostId, publicKey );
            System.out.println( String.format( "Container id: %s", containerToken.getHostId() ) );
            System.out.println( String.format( "Token       : %s", containerToken.getToken() ) );
        }
        catch ( NodeRegistrationException ex )
        {
            System.out.println( "Token verification failed." );
        }
        return null;
    }
}
