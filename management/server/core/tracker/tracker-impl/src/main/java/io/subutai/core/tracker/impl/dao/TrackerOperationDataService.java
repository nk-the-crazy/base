package io.subutai.core.tracker.impl.dao;


import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.subutai.common.tracker.TrackerOperationView;
import io.subutai.core.tracker.impl.TrackerOperationImpl;
import io.subutai.core.tracker.impl.TrackerOperationViewImpl;
import io.subutai.core.tracker.impl.entity.TrackerOperationEntity;


public class TrackerOperationDataService
{
    private static final Logger LOGGER = LoggerFactory.getLogger( TrackerOperationDataService.class );
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private EntityManagerFactory emf;


    public TrackerOperationDataService( final EntityManagerFactory emf )
    {
        this.emf = emf;
        EntityManager em = emf.createEntityManager();
        em.close();
    }


    public List<TrackerOperationEntity> getAll()
    {
        List<TrackerOperationEntity> result = Lists.newArrayList();
        EntityManager em = emf.createEntityManager();
        try
        {
            em.getTransaction().begin();

            result = em.createQuery( "SELECT to FROM TrackerOperationEntity to", TrackerOperationEntity.class )
                       .getResultList();

            em.getTransaction().commit();
        }
        catch ( PersistenceException e )
        {
            LOGGER.error( "Error getting all TrackerOperations.", e );
            if ( em.getTransaction().isActive() )
            {
                em.getTransaction().rollback();
            }
        }
        finally
        {
            em.close();
        }
        return result;
    }


    public TrackerOperationView getTrackerOperation( String source, final UUID operationTrackId )
    {
        TrackerOperationEntity result = null;
        source = source.toUpperCase();
        EntityManager em = emf.createEntityManager();
        try
        {
            em.getTransaction().begin();

            TypedQuery<TrackerOperationEntity> query =
                    em.createNamedQuery( TrackerOperationEntity.QUERY_GET_OPERATION, TrackerOperationEntity.class );
            query.setParameter( "source", source );
            query.setParameter( "operationTrackId", operationTrackId.toString() );

            List<TrackerOperationEntity> operations = query.getResultList();

            if ( operations != null && operations.size() > 0 )
            {
                result = operations.get( 0 );
            }

            em.getTransaction().commit();
        }
        catch ( PersistenceException e )
        {
            LOGGER.error( "Error .", e );
            if ( em.getTransaction().isActive() )
            {
                em.getTransaction().rollback();
            }
        }
        finally
        {
            em.close();
        }
        if ( result != null )
        {
            return createTrackerOperation( result.getInfo() );
        }
        else
        {
            return null;
        }
    }


    public TrackerOperationView getTrackerUserOperation( String source, final UUID operationTrackId, long userId )
    {
        TrackerOperationEntity result = null;
        source = source.toUpperCase();
        EntityManager em = emf.createEntityManager();
        try
        {
            em.getTransaction().begin();

            TypedQuery<TrackerOperationEntity> query =
                    em.createNamedQuery( TrackerOperationEntity.QUERY_GET_OPERATION, TrackerOperationEntity.class );
            query.setParameter( "source", source );
            query.setParameter( "operationTrackId", operationTrackId.toString() );
            query.setParameter( "userId", userId );

            List<TrackerOperationEntity> operations = query.getResultList();

            if ( operations != null && operations.size() > 0 )
            {
                result = operations.get( 0 );
            }

            em.getTransaction().commit();
        }
        catch ( PersistenceException e )
        {
            LOGGER.error( "Error .", e );
            if ( em.getTransaction().isActive() )
            {
                em.getTransaction().rollback();
            }
        }
        finally
        {
            em.close();
        }
        if ( result != null )
        {
            return createTrackerOperation( result.getInfo() );
        }
        else
        {
            return null;
        }
    }

    private TrackerOperationViewImpl createTrackerOperation( String infoClob )
    {
        if ( infoClob != null && infoClob.length() > 0 )
        {
            TrackerOperationImpl po = GSON.fromJson( infoClob, TrackerOperationImpl.class );
            return new TrackerOperationViewImpl( po );
        }
        return null;
    }


    public void saveTrackerOperation( String source, final TrackerOperationImpl po, long userId ) throws SQLException
    {
        source = source.toUpperCase();
        EntityManager em = emf.createEntityManager();
        try
        {
            em.getTransaction().begin();

            TrackerOperationEntity entity = extractFromTrackerOperationImpl( source, po, userId );
            em.merge( entity );

            em.getTransaction().commit();
        }
        catch ( PersistenceException e )
        {
            LOGGER.error( "Error merging TrackerOperationEntity.", e );
            if ( em.getTransaction().isActive() )
            {
                em.getTransaction().rollback();
            }
            throw new SQLException( e );
        }
        finally
        {
            em.close();
        }
    }


    private TrackerOperationEntity extractFromTrackerOperationImpl( String source, TrackerOperationImpl po, long userId )
    {
        source = source.toUpperCase();
        return new TrackerOperationEntity( source, po.getId().toString(), po.createDate().getTime(),
                GSON.toJson( po ), userId );
    }


    public List<TrackerOperationView> getTrackerOperations( String source, final Date fromDate, final Date toDate,
                                                            final int limit ) throws SQLException
    {
        source = source.toUpperCase();
        List<TrackerOperationView> result = Lists.newArrayList();
        EntityManager em = emf.createEntityManager();
        try
        {
            em.getTransaction().begin();

            TypedQuery<String> query = em.createQuery(
                    "select to.info from TrackerOperationEntity to where to.source = :source and to.ts >= :fromDate "
                            + "and to.ts <= :toDate  order by to.ts desc", String.class );
            query.setParameter( "source", source );
            query.setParameter( "fromDate", fromDate.getTime() );
            query.setParameter( "toDate", toDate.getTime() );
            query.setMaxResults( limit );
            List<String> infoList = query.getResultList();
            for ( final String info : infoList )
            {
                result.add( createTrackerOperation( info ) );
            }

            em.getTransaction().commit();
        }
        catch ( Exception e )
        {
            LOGGER.error( "Error in getTrackerOperations.", e );
            if ( em.getTransaction().isActive() )
            {
                em.getTransaction().rollback();
            }
            throw new SQLException( e );
        }
        finally
        {
            em.close();
        }
        return result;
    }


    public List<String> getTrackerOperationSources() throws SQLException
    {
        List<String> result = Lists.newArrayList();
        EntityManager em = emf.createEntityManager();
        try
        {
            em.getTransaction().begin();

            TypedQuery<String> query =
                    em.createQuery( "select distinct to.source from TrackerOperationEntity to", String.class );
            result.addAll( query.getResultList() );

            em.getTransaction().commit();
        }
        catch ( PersistenceException e )
        {
            LOGGER.error( "Error getting trackerOperationSources.", e );
            if ( em.getTransaction().isActive() )
            {
                em.getTransaction().rollback();
            }
            throw new SQLException( e );
        }
        finally
        {
            em.close();
        }
        return result;
    }

    public List<TrackerOperationView> getRecentUserOperations( String source, final Date fromDate, final Date toDate,
                                                               int limit, long userId ) throws SQLException
    {
        source = source.toUpperCase();
        List<TrackerOperationView> result = Lists.newArrayList();
        EntityManager em = emf.createEntityManager();
        try
        {
            em.getTransaction().begin();

            TypedQuery<String> query = em.createQuery(
                    "select to.info from TrackerOperationEntity to where to.source = :source and to.ts >= :fromDate "
                            + "and to.ts <= :toDate and to.userId = :userId order by to.ts desc", String.class );
            query.setParameter( "source", source );
            query.setParameter( "fromDate", fromDate.getTime() );
            query.setParameter( "toDate", toDate.getTime() );
            query.setParameter( "userId", userId );
            query.setMaxResults( limit );
            List<String> infoList = query.getResultList();
            for ( final String info : infoList )
            {
                result.add( createTrackerOperation( info ) );
            }

            em.getTransaction().commit();
        }
        catch ( Exception e )
        {
            LOGGER.error( "Error in getTrackerOperations.", e );
            if ( em.getTransaction().isActive() )
            {
                em.getTransaction().rollback();
            }
            throw new SQLException( e );
        }
        finally
        {
            em.close();
        }
        return result;
    }

    public void setOperationViewState( String source, final UUID operationTrackId, boolean viewState ) throws SQLException
    {
        EntityManager em = emf.createEntityManager();
        source = source.toUpperCase();

        try {
            TrackerOperationEntity result;

            em.getTransaction().begin();

            TypedQuery<TrackerOperationEntity> query =
                    em.createNamedQuery( TrackerOperationEntity.QUERY_GET_OPERATION, TrackerOperationEntity.class );
            query.setParameter( "source", source );
            query.setParameter( "operationTrackId", operationTrackId.toString() );

            List<TrackerOperationEntity> operations = query.getResultList();

            if ( operations != null && operations.size() > 0 )
            {
                result = operations.get( 0 );
                result.setViewState( viewState );

                em.merge( result );
            }

            em.getTransaction().commit();
        }
        catch ( Exception e )
        {
            LOGGER.error( "Error in getTrackerOperations.", e );
            if ( em.getTransaction().isActive() )
            {
                em.getTransaction().rollback();
            }

            throw new SQLException( e );
        }
        finally
        {
            em.close();
        }
    }

    public void setOperationsViewState( boolean viewState, long userId ) throws SQLException
    {
        EntityManager em = emf.createEntityManager();
        try
        {
            em.getTransaction().begin();

            Query query = em.createQuery(
                    "update TrackerOperationEntity to set to.viewState = :viewState where to.userId = :userId" );
            query.setParameter( "viewState", viewState );
            query.setParameter( "userId", userId );
            query.executeUpdate();

            em.getTransaction().commit();
        }
        catch ( Exception e )
        {
            LOGGER.error( "Error in getTrackerOperations.", e );
            if ( em.getTransaction().isActive() )
            {
                em.getTransaction().rollback();
            }
            throw new SQLException( e );
        }
        finally
        {
            em.close();
        }
    }

    public List<TrackerOperationView> getNewOperations(long userId ) throws SQLException
    {
        List<TrackerOperationView> result = Lists.newArrayList();
        EntityManager em = emf.createEntityManager();
        try
        {
            em.getTransaction().begin();

            TypedQuery<String> query = em.createQuery(
                    "select to.info from TrackerOperationEntity to where to.viewState = true and to.userId = :userId order by to.ts desc", String.class );
            query.setParameter( "userId", userId );

            List<String> infoList = query.getResultList();
            for ( final String info : infoList )
            {
                result.add( createTrackerOperation( info ) );
            }

            em.getTransaction().commit();
        }
        catch ( Exception e )
        {
            LOGGER.error( "Error in getTrackerOperations.", e );
            if ( em.getTransaction().isActive() )
            {
                em.getTransaction().rollback();
            }
            throw new SQLException( e );
        }
        finally
        {
            em.close();
        }

        return result;
    }
}
