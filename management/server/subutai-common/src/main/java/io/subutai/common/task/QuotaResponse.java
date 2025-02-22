package io.subutai.common.task;


public class QuotaResponse implements TaskResponse
{
    private String resourceHostId;
    private String hostname;
    private boolean succeeded;
    private long elapsedTime;


    public QuotaResponse( final String resourceHostId, final String hostname, final boolean succeeded,
                          final long elapsedTime )
    {
        this.resourceHostId = resourceHostId;
        this.hostname = hostname;
        this.succeeded = succeeded;
        this.elapsedTime = elapsedTime;
    }


    @Override
    public String getResourceHostId()
    {
        return resourceHostId;
    }


    public String getHostname()
    {
        return hostname;
    }


    public boolean hasSucceeded()
    {
        return succeeded;
    }


    @Override
    public long getElapsedTime()
    {
        return elapsedTime;
    }


    @Override
    public String toString()
    {
        return "QuotaResponse{" + "resourceHostId='" + resourceHostId + '\'' + ", hostname='" + hostname + '\''
                + ", succeeded=" + succeeded + ", elapsedTime=" + elapsedTime + '}';
    }
}
