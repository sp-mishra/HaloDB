/*
 * Copyright 2018, Oath Inc
 * Licensed under the terms of the Apache License 2.0. Please refer to accompanying LICENSE file for terms.
 */

// This code is a derivative work heavily modified from the OHC project. See NOTICE file for copyright and license.

package com.oath.halodb;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import sun.misc.Unsafe;
import sun.nio.ch.DirectBuffer;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

import static org.testng.Assert.*;

public class UnsTest {
    static final int CAPACITY = 65536;
    static final ByteBuffer directBuffer;
    private static final Unsafe unsafe;

    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe) field.get(null);
            if (unsafe.addressSize() > 8)
                throw new RuntimeException("Address size " + unsafe.addressSize() + " not supported yet (max 8 bytes)");

            directBuffer = ByteBuffer.allocateDirect(CAPACITY);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void fillRandom() {
        Random r = new Random();
        directBuffer.clear();
        while (directBuffer.remaining() >= 4)
            directBuffer.putInt(r.nextInt());
        directBuffer.clear();
    }

    private static void equals(byte[] ref, long adr, int off, int len) {
        for (; len-- > 0; off++)
            assertEquals(unsafe.getByte(adr + off), ref[off]);
    }

    private static void equals(byte[] ref, byte[] arr, int off, int len) {
        for (; len-- > 0; off++)
            assertEquals(arr[off], ref[off]);
    }

    @AfterMethod(alwaysRun = true)
    public void deinit() {
        Uns.clearUnsDebugForTest();
    }

    @Test
    public void testDirectBufferFor() throws Exception {
        fillRandom();

        ByteBuffer buf = Uns.directBufferFor(((DirectBuffer) directBuffer).address(), 0, directBuffer.capacity(), false);

        for (int i = 0; i < CAPACITY; i++) {
            byte b = buf.get();
            byte d = directBuffer.get();
            assertEquals(b, d);

            assertEquals(buf.position(), directBuffer.position());
            assertEquals(buf.limit(), directBuffer.limit());
            assertEquals(buf.remaining(), directBuffer.remaining());
            assertEquals(buf.capacity(), directBuffer.capacity());
        }

        buf.clear();
        directBuffer.clear();

        while (buf.remaining() >= 8) {
            long b = buf.getLong();
            long d = directBuffer.getLong();
            assertEquals(b, d);

            assertEquals(buf.position(), directBuffer.position());
            assertEquals(buf.remaining(), directBuffer.remaining());
        }

        while (buf.remaining() >= 4) {
            int b = buf.getInt();
            int d = directBuffer.getInt();
            assertEquals(b, d);

            assertEquals(buf.position(), directBuffer.position());
            assertEquals(buf.remaining(), directBuffer.remaining());
        }

        for (int i = 0; i < CAPACITY; i++) {
            byte b = buf.get(i);
            byte d = directBuffer.get(i);
            assertEquals(b, d);

            if (i >= CAPACITY - 1)
                continue;

            char bufChar = buf.getChar(i);
            char dirChar = directBuffer.getChar(i);
            short bufShort = buf.getShort(i);
            short dirShort = directBuffer.getShort(i);

            assertEquals(bufChar, dirChar);
            assertEquals(bufShort, dirShort);

            if (i >= CAPACITY - 3)
                continue;

            int bufInt = buf.getInt(i);
            int dirInt = directBuffer.getInt(i);
            float bufFloat = buf.getFloat(i);
            float dirFloat = directBuffer.getFloat(i);

            assertEquals(bufInt, dirInt);
            assertEquals(bufFloat, dirFloat);

            if (i >= CAPACITY - 7)
                continue;

            long bufLong = buf.getLong(i);
            long dirLong = directBuffer.getLong(i);
            double bufDouble = buf.getDouble(i);
            double dirDouble = directBuffer.getDouble(i);

            assertEquals(bufLong, dirLong);
            assertEquals(bufDouble, dirDouble);
        }
    }

    @Test
    public void testAllocate() throws Exception {
        long adr = Uns.allocate(100);
        assertNotEquals(adr, 0L);
        Uns.free(adr);

        adr = Uns.allocateIOException(100);
        Uns.free(adr);
    }

    @Test(expectedExceptions = IOException.class)
    public void testAllocateTooMuch() throws Exception {
        Uns.allocateIOException(Long.MAX_VALUE);
    }

    @Test
    public void testGetTotalAllocated() throws Exception {
        long before = Uns.getTotalAllocated();
        if (before < 0L)
            return;

        // TODO Uns.getTotalAllocated() seems not to respect "small" areas - need to check that ... eventually.
//        long[] adrs = new long[10000];
//        try
//        {
//            for (int i=0;i<adrs.length;i++)
//                adrs[i] = Uns.allocate(100);
//            assertTrue(Uns.getTotalAllocated() > before);
//        }
//        finally
//        {
//            for (long adr : adrs)
//                Uns.free(adr);
//        }

        long adr = Uns.allocate(128 * 1024 * 1024);
        try {
            assertTrue(Uns.getTotalAllocated() > before);
        } finally {
            Uns.free(adr);
        }
    }

    @Test
    public void testCopyMemory() throws Exception {
        byte[] ref = HashTableTestUtils.randomBytes(7777 + 130);
        byte[] arr = new byte[7777 + 130];

        long adr = Uns.allocate(7777 + 130);
        try {
            for (int offset = 0; offset < 10; offset += 13)
                for (int off = 0; off < 10; off += 13) {
                    Uns.copyMemory(ref, off, adr, offset, 7777);

                    equals(ref, adr, offset, 7777);

                    Uns.copyMemory(adr, offset, arr, off, 7777);

                    equals(ref, arr, off, 7777);
                }
        } finally {
            Uns.free(adr);
        }
    }

    @Test
    public void testSetMemory() throws Exception {
        long adr = Uns.allocate(7777 + 130);
        try {
            for (byte b = 0; b < 13; b++)
                for (int offset = 0; offset < 10; offset += 13) {
                    Uns.setMemory(adr, offset, 7777, b);

                    for (int off = 0; off < 7777; off++)
                        assertEquals(unsafe.getByte(adr + offset), b);
                }
        } finally {
            Uns.free(adr);
        }
    }

    @Test
    public void testGetLongFromByteArray() throws Exception {
        byte[] arr = HashTableTestUtils.randomBytes(32);
        ByteOrder order = directBuffer.order();
        directBuffer.order(ByteOrder.nativeOrder());
        try {
            for (int i = 0; i < 14; i++) {
                long u = Uns.getLongFromByteArray(arr, i);
                directBuffer.clear();
                directBuffer.put(arr);
                directBuffer.flip();
                long b = directBuffer.getLong(i);
                assertEquals(b, u);
            }
        } finally {
            directBuffer.order(order);
        }
    }

    @Test
    public void testGetPutLong() throws Exception {
        long adr = Uns.allocate(128);
        try {
            Uns.copyMemory(HashTableTestUtils.randomBytes(128), 0, adr, 0, 128);

            for (int i = 0; i < 14; i++) {
                long l = Uns.getLong(adr, i);
                assertEquals(unsafe.getLong(adr + i), Uns.getLong(adr, i));

                Uns.putLong(adr, i, l);
                assertEquals(unsafe.getLong(adr + i), l);
            }
        } finally {
            Uns.free(adr);
        }
    }

    @Test
    public void testGetPutInt() throws Exception {
        long adr = Uns.allocate(128);
        try {
            Uns.copyMemory(HashTableTestUtils.randomBytes(128), 0, adr, 0, 128);

            for (int i = 0; i < 14; i++) {
                int l = Uns.getInt(adr, i);
                assertEquals(unsafe.getInt(adr + i), Uns.getInt(adr, i));

                Uns.putInt(adr, i, l);
                assertEquals(unsafe.getInt(adr + i), l);
            }
        } finally {
            Uns.free(adr);
        }
    }

    @Test
    public void testGetPutShort() throws Exception {
        long adr = Uns.allocate(128);
        try {
            Uns.copyMemory(HashTableTestUtils.randomBytes(128), 0, adr, 0, 128);

            for (int i = 0; i < 14; i++) {
                short l = Uns.getShort(adr, i);
                assertEquals(unsafe.getShort(adr + i), Uns.getShort(adr, i));

                Uns.putShort(adr, i, l);
                assertEquals(unsafe.getShort(adr + i), l);
            }
        } finally {
            Uns.free(adr);
        }
    }

    @Test
    public void testGetPutByte() throws Exception {
        long adr = Uns.allocate(128);
        try {
            Uns.copyMemory(HashTableTestUtils.randomBytes(128), 0, adr, 0, 128);

            for (int i = 0; i < 14; i++) {
                ByteBuffer buf = Uns.directBufferFor(adr, i, 8, false);
                byte l = Uns.getByte(adr, i);
                assertEquals(buf.get(0), Uns.getByte(adr, i));
                assertEquals(unsafe.getByte(adr + i), Uns.getByte(adr, i));

                Uns.putByte(adr, i, l);
                assertEquals(buf.get(0), l);
                assertEquals(unsafe.getByte(adr + i), l);
            }
        } finally {
            Uns.free(adr);
        }
    }

    @Test
    public void testDecrementIncrement() throws Exception {
        long adr = Uns.allocate(128);
        try {
            for (int i = 0; i < 120; i++) {
                String loop = "at loop #" + i;
                long v = Uns.getInt(adr, i);
                Uns.increment(adr, i);
                assertEquals(Uns.getInt(adr, i), v + 1, loop);
                Uns.increment(adr, i);
                assertEquals(Uns.getInt(adr, i), v + 2, loop);
                Uns.increment(adr, i);
                assertEquals(Uns.getInt(adr, i), v + 3, loop);
                Uns.decrement(adr, i);
                assertEquals(Uns.getInt(adr, i), v + 2, loop);
                Uns.decrement(adr, i);
                assertEquals(Uns.getInt(adr, i), v + 1, loop);
            }

            Uns.putLong(adr, 8, 1);
            assertTrue(Uns.decrement(adr, 8));
            Uns.putLong(adr, 8, 2);
            assertFalse(Uns.decrement(adr, 8));
        } finally {
            Uns.free(adr);
        }
    }

    @Test
    public void testCompare() throws Exception {
        long adr = Uns.allocate(CAPACITY);
        try {
            long adr2 = Uns.allocate(CAPACITY);
            try {

                Uns.setMemory(adr, 5, 11, (byte) 0);
                Uns.setMemory(adr2, 5, 11, (byte) 1);

                assertFalse(Uns.memoryCompare(adr, 5, adr2, 5, 11));

                assertTrue(Uns.memoryCompare(adr, 5, adr, 5, 11));
                assertTrue(Uns.memoryCompare(adr2, 5, adr2, 5, 11));

                Uns.setMemory(adr, 5, 11, (byte) 1);

                assertTrue(Uns.memoryCompare(adr, 5, adr2, 5, 11));
            } finally {
                Uns.free(adr2);
            }
        } finally {
            Uns.free(adr);
        }
    }

    @Test
    public void testCompareManyKeys() {

        Random random = new Random();
        for (int i = 0; i < 128; i++) {
            long adr1 = Uns.allocate(CAPACITY);
            long adr2 = Uns.allocate(CAPACITY);
            try {
                byte[] key = com.oath.halodb.TestUtils.generateRandomByteArray();
                Uns.copyMemory(key, 0, adr1, i, key.length);
                Uns.copyMemory(key, 0, adr2, i, key.length);
                assertTrue(Uns.memoryCompare(adr1, i, adr2, i, key.length));


                int offsetToChange = i + random.nextInt(key.length);
                byte change = (byte) ~Uns.getByte(adr2, offsetToChange);

                Uns.setMemory(adr2, offsetToChange, 1, change);
                assertFalse(Uns.memoryCompare(adr1, i, adr2, i, key.length));
            } finally {
                Uns.free(adr1);
                Uns.free(adr2);
            }
        }
    }

}
