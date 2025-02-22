package io.subutai.core.lxc.quota.impl;


import com.google.common.collect.Lists;

import io.subutai.common.command.RequestBuilder;
import io.subutai.common.quota.ContainerResource;
import io.subutai.common.resource.ContainerResourceType;


/**
 * Quota manager commands
 */
public class Commands
{
    private static final String QUOTA_BINDING = "subutai quota";


    public RequestBuilder getReadQuotaCommand( String containerHostname, ContainerResourceType containerResourceType )
    {
        return new RequestBuilder( QUOTA_BINDING )
                .withCmdArgs( Lists.newArrayList( containerHostname, containerResourceType.getKey() ) );
    }


    public RequestBuilder getWriteQuotaCommand( final String containerHostname, final ContainerResource resourceValue,
                                                final Integer threshold )
    {
        return new RequestBuilder( QUOTA_BINDING ).withCmdArgs(
                Lists.newArrayList( containerHostname, resourceValue.getContainerResourceType().getKey(), "-s",
                        resourceValue.getWriteValue(), "-t", threshold.toString() ) );
    }


    public RequestBuilder getReadCpuSetCommand( String containerHostname )
    {
        return new RequestBuilder( QUOTA_BINDING ).withCmdArgs( Lists.newArrayList( containerHostname, "cpuset" ) );
    }


    public RequestBuilder getWriteCpuSetCommand( String containerHostname, String cpuset )
    {
        return new RequestBuilder( QUOTA_BINDING )
                .withCmdArgs( Lists.newArrayList( containerHostname, "cpuset", "-s", cpuset ) );
    }
}
