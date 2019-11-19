package ngsep.gbs;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadPoolManager {
	private static final long TIMEOUT_MILLISECONDS = 1000;
	
	private int maxTaskCount;
	private final int numThreads;
	private ThreadPoolExecutor pool;
	
	public ThreadPoolManager(int numberOfThreads, int maxTaskCount) {
		this.maxTaskCount = maxTaskCount;
		this.numThreads = numberOfThreads;
		this.pool = new ThreadPoolExecutor(0, numThreads, TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
	}
	
	/**
	 * Adds task to the threadPoolExecutor for this instance. If the task queue limit is exceeded,
	 * the pool will be relaunched afterwards, after waiting for all queued tasks to finish.
	 * @param task task to add to the pool
	 * @throws InterruptedException if the relauch process is interrupted
	 */
	public void queueTask(Runnable task) throws InterruptedException {
		int taskCount = pool.getQueue().size();
		while (taskCount == maxTaskCount) {
			Thread.yield();
			taskCount = pool.getQueue().size();
		}
		pool.execute(task);
	}
	
	/**
	 * Terminates the pool, shutting it down and waiting for it to finish all queued tasks.
	 * @throws InterruptedException if the shutdown operation is interrupted
	 */
	public void terminatePool() throws InterruptedException  {
		pool.shutdown();
    	pool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    	if(!pool.isTerminated()) {
			throw new InterruptedException("The ThreadPoolExecutor was not shutdown after an await Termination call");
		}
	}
	
	public boolean getStatus() {
		return pool.isTerminated();
	}
	
	
	/**
	 * Shuts down the pool, waiting for all queued tasks to finish. Then, it creates a new one.
	 * @throws InterruptedException if the shutdown operation is interrupted or if the pool was not shutdown correctly.
	 */
	private void relaunchPool() throws InterruptedException {
		this.terminatePool();
    	//Create new pool
		pool = new ThreadPoolExecutor(numThreads, numThreads*2, TIMEOUT_MILLISECONDS, TimeUnit.MICROSECONDS, new LinkedBlockingQueue<Runnable>());
	}
}
