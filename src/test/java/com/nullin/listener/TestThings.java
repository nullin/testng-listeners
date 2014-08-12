package com.nullin.listener;

import java.util.Random;

import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * Simple test to start up some tests in parallel to manually/visually test the
 * listener
 *
 * @author nullin
 */
@Listeners(SimpleExecStatusListener.class)
public class TestThings {

    @Test
    public void test1() throws InterruptedException {
        Thread.sleep(5000);
        System.out.println("test1");
    }

    @Test
    public void test2() throws InterruptedException {
        Thread.sleep(5000);
        System.out.println("test2");
    }

    @Test
    public void test3() throws InterruptedException {
        Thread.sleep(10000);
        System.out.println("test3");
    }

    @DataProvider(name = "DP", parallel = true)
    public Object[][] getData() {
        int NUM  = 100;
        Object[][] data = new Object[NUM][2];
        Random random = new Random();
        for (int i = 0 ; i < NUM ; i++) {
            data[i][0] = i;
            data[i][1] = random.nextInt(5000) + 5000;
        }
        return data;
    }

    @Test(dataProvider = "DP", threadPoolSize = 3)
    public void dpTest(int id, int time) throws InterruptedException {
        System.out.println("Running dp test " + id + ". Sleep : " + time);
        Thread.sleep(time);
        Random r = new Random();
        int i = r.nextInt(1000);
        if (i % 5 == 0) {
            throw new RuntimeException(":-(");
        }

        if (i % 3 == 0) {
            throw new SkipException("Skipped!!");
        }
    }

    @AfterClass
    public void doom() throws InterruptedException {
        Thread.sleep(500);
    }

}
