package com.oath.halodb;

import org.testng.Assert;
import org.testng.annotations.Test;

public class HaloDBOptionsTest extends TestBase {

    @Test
    public void testDefaultOptions() throws HaloDBException {
        String directory = TestUtils.getTestDirectory("HaloDBOptionsTest", "testDefaultOptions");

        HaloDB db = getTestDB(directory, new HaloDBOptions());
        Assert.assertFalse(db.stats().getOptions().isSyncWrite());
        Assert.assertFalse(db.stats().getOptions().isCompactionDisabled());
        Assert.assertEquals(db.stats().getOptions().getBuildIndexThreads(), 1);
    }
}
