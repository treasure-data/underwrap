package com.treasuredata.underwrap;

import org.xnio.XnioWorker;

import java.lang.reflect.Field;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

public class UnderwrapMetrics
{
    private final XnioWorker worker;

    public UnderwrapMetrics(XnioWorker worker)
    {
        this.worker = worker;
    }

    // Latest Undertow (1.4.1x) depends on Xnio 3.3.x, and this version doesn't have methods to
    // get metrics (for example, `XnioWorker#getMXBean()`).

    // Methods below forcibly get metric values from XnioWorker internal, instead of calling
    // protected/unexisting methods of XnioWorker.
    // https://github.com/xnio/xnio/blob/3.x/api/src/main/java/org/xnio/XnioWorker.java#L889-L923

    public int getCoreWorkerPoolSize()
    {
        // return coreSize;
        try {
            Field field = XnioWorker.class.getDeclaredField("coreSize");
            field.setAccessible(true);
            return field.getInt(worker);
        }
        catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("BUG: incompatible accesse", e);
        }
        // TODO: use `worker.getMXBean().getCoreWorkerPoolSize()` when Xnio 3.5 or later available
    }

    public int getBusyWorkerThreadCount()
    {
        // return taskPool.getActiveCount();
        try {
            Field field = XnioWorker.class.getDeclaredField("taskPool");
            field.setAccessible(true);
            return ((ThreadPoolExecutor) field.get(worker)).getActiveCount();
        }
        catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("BUG: incompatible accesse", e);
        }
        // TODO: use `worker.getMXBean().getMaxWorkerPoolSize()` when Xnio 3.5 or later available
    }

    public int getMaxWorkerPoolSize()
    {
        // return taskPool.getMaximumPoolSize();
        try {
            Field field = XnioWorker.class.getDeclaredField("taskPool");
            field.setAccessible(true);
            return ((ThreadPoolExecutor) field.get(worker)).getMaximumPoolSize();
        }
        catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("BUG: incompatible accesse", e);
        }
        // TODO: use `worker.getMXBean().getBusyWorkerThreadCount()` when Xnio 3.5 or later available
    }

    public int getWorkerQueueSize()
    {
        // return taskQueue.size();
        try {
            Field field = XnioWorker.class.getDeclaredField("taskQueue");
            field.setAccessible(true);
            return ((BlockingQueue) field.get(worker)).size();
        }
        catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("BUG: incompatible accesse", e);
        }
        // TODO: use `worker.getMXBean().getWorkerQueueSize()` when Xnio 3.5 or later available
    }
}
