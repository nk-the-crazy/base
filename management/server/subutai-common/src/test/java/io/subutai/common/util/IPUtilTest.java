package io.subutai.common.util;


import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import io.subutai.common.util.IPUtil;

import static org.junit.Assert.assertTrue;


@RunWith( MockitoJUnitRunner.class )
public class IPUtilTest
{
    private IPUtil ipUtil;


    @Before
    public void setUp() throws Exception
    {
        ipUtil = new IPUtil();
    }


    @Test
    public void testIsValidIPRange() throws Exception
    {
        assertTrue( ipUtil.isValidIPRange( "*", "*", "172.5.5.5.5" ) );
        //assertFalse( ipUtil.isValidIPRange( "asd", "asd", "asd" ) );
        assertTrue( ipUtil.isValidIPRange( "10.10.10.1", "10.10.10.10", "10.10.10.5" ) );
    }
}