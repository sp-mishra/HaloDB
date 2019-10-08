/*
 * Copyright 2018, Oath Inc
 * Licensed under the terms of the Apache License 2.0. Please refer to accompanying LICENSE file for terms.
 */

package com.oath.halodb;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;

import java.io.File;
import java.io.IOException;

public class TestBase {

    protected DBDirectory dbDirectory;
    private String directory;
    private HaloDB db;

    @DataProvider(name = "Options")
    public Object[][] optionData() {
        HaloDBOptions options = new HaloDBOptions();
        options.setBuildIndexThreads(2);
        HaloDBOptions withMemoryPool = new HaloDBOptions();
        withMemoryPool.setUseMemoryPool(true);
        withMemoryPool.setMemoryPoolChunkSize(1024 * 1024);
        withMemoryPool.setBuildIndexThreads(2);

        return new Object[][]{
                {options},
                {withMemoryPool}
        };
    }

    HaloDB getTestDB(String directory, HaloDBOptions options) throws HaloDBException {
        this.directory = directory;
        File dir = new File(directory);
        try {
            TestUtils.deleteDirectory(dir);
        } catch (IOException e) {
            throw new HaloDBException(e);
        }
        db = HaloDB.open(dir, options);
        try {
            dbDirectory = DBDirectory.open(new File(directory));
        } catch (IOException e) {
            throw new HaloDBException(e);
        }
        return db;
    }

    HaloDB getTestDBWithoutDeletingFiles(String directory, HaloDBOptions options) throws HaloDBException {
        this.directory = directory;
        File dir = new File(directory);
        db = HaloDB.open(dir, options);
        TestUtils.waitForTombstoneFileMergeComplete(db);
        return db;
    }

    @AfterMethod(alwaysRun = true)
    public void closeDB() throws HaloDBException, IOException {
        if (db != null) {
            db.close();
            db = null;
            File dir = new File(directory);
            if (dbDirectory != null) {
                dbDirectory.close();
                dbDirectory = null;
            }
            TestUtils.deleteDirectory(dir);
        }
    }
}
