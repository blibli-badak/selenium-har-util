package com.blibli.oss.qa.util;

import com.blibli.oss.qa.util.model.RequestResponsePair;
import com.blibli.oss.qa.util.model.RequestResponseStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.sstoehr.harreader.HarReader;
import de.sstoehr.harreader.model.Har;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.devtools.v142.network.model.Request;
import org.openqa.selenium.devtools.v142.network.model.Response;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Thread-safety tests for HAR file creation.
 * Verifies that ConcurrentModificationException is not thrown when the browser
 * continues sending data while the HAR file is being written.
 */
public class ThreadSafeHarCreationTest {

    private RequestResponseStorage storage;
    private static final String TEST_HAR_FILE = "test-thread-safe.har";

    @BeforeEach
    public void setup() {
        storage = new RequestResponseStorage();
        // Clean up test file if it exists
        try {
            Files.deleteIfExists(Paths.get(TEST_HAR_FILE));
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Test that RequestResponseStorage uses CopyOnWriteArrayList
     * which allows safe concurrent modifications.
     */
    @Test
    public void testRequestResponseStorageUsesCopyOnWriteArrayList() {
        // Verify that the list is CopyOnWriteArrayList
        assertTrue(storage.getRequestResponsePairs() instanceof CopyOnWriteArrayList,
                "RequestResponseStorage should use CopyOnWriteArrayList for thread safety");
    }

    /**
     * Test concurrent read and write operations on RequestResponseStorage.
     * Simulates the scenario where the browser sends new requests while
     * the HAR file is being serialized.
     */
    @Test
    public void testConcurrentAddAndIterateRequests() throws InterruptedException {
        int numThreads = 5;
        int requestsPerThread = 20;
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads + 1);
        AtomicInteger iterationCount = new AtomicInteger(0);
        AtomicInteger exceptionCount = new AtomicInteger(0);

        // Start threads that add requests
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < requestsPerThread; j++) {
                        RequestResponsePair pair = new RequestResponsePair(
                                "request-" + threadId + "-" + j,
                                null,
                                new Date(),
                                null
                        );
                        storage.getRequestResponsePairs().add(pair);
                        Thread.yield();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Start a thread that iterates over the list (simulating HAR serialization)
        executorService.submit(() -> {
            try {
                startLatch.await();
                Thread.sleep(50); // Let some requests accumulate
                for (int iteration = 0; iteration < 10; iteration++) {
                    try {
                        // This should not throw ConcurrentModificationException
                        int size = 0;
                        for (RequestResponsePair pair : storage.getRequestResponsePairs()) {
                            size++;
                        }
                        iterationCount.incrementAndGet();
                    } catch (Exception e) {
                        exceptionCount.incrementAndGet();
                        fail("ConcurrentModificationException should not occur: " + e.getMessage());
                    }
                    Thread.sleep(10);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = endLatch.await(10, java.util.concurrent.TimeUnit.SECONDS);

        executorService.shutdown();

        assertTrue(completed, "Test did not complete in time");
        assertTrue(iterationCount.get() > 0, "Should have completed iterations");
        assertEquals(0, exceptionCount.get(), "Should not have any exceptions");
        assertEquals(numThreads * requestsPerThread, storage.getRequestResponsePairs().size(),
                "Should have collected all requests");
    }

    /**
     * Test that snapshot creation prevents ConcurrentModificationException
     * during iteration (as done in createHarFile methods).
     */
    @Test
    public void testSnapshotIterationDuringSerialization() throws InterruptedException {
        int numThreads = 3;
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads + 1);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads + 1);
        AtomicInteger successfulSnapshots = new AtomicInteger(0);
        AtomicInteger exceptionCount = new AtomicInteger(0);

        // Add initial requests
        for (int i = 0; i < 10; i++) {
            storage.getRequestResponsePairs().add(
                    new RequestResponsePair("initial-" + i, null, new Date(), null)
            );
        }

        // Threads that continuously add requests
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 50; j++) {
                        storage.getRequestResponsePairs().add(
                                new RequestResponsePair("request-" + threadId + "-" + j, null, new Date(), null)
                        );
                        Thread.sleep(1);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Snapshot-based iteration (as used in createHarFile)
        executorService.submit(() -> {
            try {
                startLatch.await();
                for (int iteration = 0; iteration < 20; iteration++) {
                    try {
                        // Create snapshot like the actual code does
                        var snapshot = new java.util.ArrayList<>(storage.getRequestResponsePairs());
                        int snapshotSize = snapshot.size();
                        snapshot.forEach(pair -> assertNotNull(pair));
                        successfulSnapshots.incrementAndGet();
                    } catch (Exception e) {
                        exceptionCount.incrementAndGet();
                        fail("Exception during snapshot iteration: " + e.getMessage());
                    }
                    Thread.sleep(5);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = endLatch.await(15, java.util.concurrent.TimeUnit.SECONDS);

        executorService.shutdown();

        assertTrue(completed, "Test did not complete in time");
        assertTrue(successfulSnapshots.get() > 0, "Should have successful snapshots");
        assertEquals(0, exceptionCount.get(), "Should not have any exceptions");
    }

    /**
     * Test that NetworkListener uses ConcurrentHashMap for windowHandleStorageMap.
     * This verifies the thread-safety improvement at the NetworkListener level.
     */
    @Test
    public void testNetworkListenerUsesThreadSafeMap() throws NoSuchFieldException, IllegalAccessException {
        // Create a NetworkListener instance (with minimal setup)
        com.blibli.oss.qa.util.services.NetworkListener listener = 
                new com.blibli.oss.qa.util.services.NetworkListener("dummy.har");

        // Use reflection to access the private windowHandleStorageMap field
        Field mapField = com.blibli.oss.qa.util.services.NetworkListener.class.getDeclaredField("windowHandleStorageMap");
        mapField.setAccessible(true);
        Map<String, RequestResponseStorage> map = (Map<String, RequestResponseStorage>) mapField.get(listener);

        // Verify it's a ConcurrentHashMap
        assertTrue(map instanceof ConcurrentHashMap,
                "NetworkListener.windowHandleStorageMap should use ConcurrentHashMap for thread safety");
    }

    /**
     * Test concurrent map and list operations together.
     * Simulates the complete scenario of multiple windows being monitored
     * while HAR file is being written.
     */
    @Test
    public void testConcurrentWindowHandleAndRequestStorage() throws InterruptedException {
        Map<String, RequestResponseStorage> windowMap = new ConcurrentHashMap<>();
        int numWindows = 3;
        int threadsPerWindow = 2;
        int requestsPerThread = 10;

        // Initialize windows
        for (int w = 0; w < numWindows; w++) {
            windowMap.put("window-" + w, new RequestResponseStorage());
        }

        ExecutorService executorService = Executors.newFixedThreadPool(numWindows * threadsPerWindow + 1);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numWindows * threadsPerWindow + 1);
        AtomicInteger exceptions = new AtomicInteger(0);

        // Threads adding requests to different windows
        for (int w = 0; w < numWindows; w++) {
            for (int t = 0; t < threadsPerWindow; t++) {
                final int windowId = w;
                final int threadId = t;
                executorService.submit(() -> {
                    try {
                        startLatch.await();
                        RequestResponseStorage storage = windowMap.get("window-" + windowId);
                        for (int i = 0; i < requestsPerThread; i++) {
                            storage.getRequestResponsePairs().add(
                                    new RequestResponsePair(
                                            "win-" + windowId + "-thread-" + threadId + "-req-" + i,
                                            null,
                                            new Date(),
                                            null
                                    )
                            );
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }
        }

        // Thread that iterates over all windows (simulating createHarFile)
        executorService.submit(() -> {
            try {
                startLatch.await();
                Thread.sleep(50);
                for (int iteration = 0; iteration < 5; iteration++) {
                    try {
                        // Snapshot the map (as done in createHarFile)
                        var snapshot = new java.util.HashMap<>(windowMap);
                        snapshot.forEach((windowHandle, storage) -> {
                            // Snapshot the list
                            var listSnapshot = new java.util.ArrayList<>(storage.getRequestResponsePairs());
                            listSnapshot.forEach(pair -> assertNotNull(pair));
                        });
                    } catch (Exception e) {
                        exceptions.incrementAndGet();
                        e.printStackTrace();
                    }
                    Thread.sleep(10);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = endLatch.await(15, java.util.concurrent.TimeUnit.SECONDS);

        executorService.shutdown();

        assertTrue(completed, "Test did not complete in time");
        assertEquals(0, exceptions.get(), "Should not have any exceptions");
        
        // Verify all data was collected
        int totalRequests = 0;
        for (RequestResponseStorage s : windowMap.values()) {
            totalRequests += s.getRequestResponsePairs().size();
        }
        assertEquals(numWindows * threadsPerWindow * requestsPerThread, totalRequests,
                "Should have collected all requests");
    }

    /**
     * Test that no exception is thrown when creating a snapshot
     * while the underlying list is being modified.
     */
    @Test
    public void testSnapshotCreationWithConcurrentModification() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);
        AtomicInteger snapshotCount = new AtomicInteger(0);
        AtomicInteger exceptions = new AtomicInteger(0);

        // Add initial data
        for (int i = 0; i < 100; i++) {
            storage.getRequestResponsePairs().add(
                    new RequestResponsePair("initial-" + i, null, new Date(), null)
            );
        }

        // Thread 1: Continuously create snapshots
        executorService.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 100; i++) {
                    try {
                        var snapshot = new java.util.ArrayList<>(storage.getRequestResponsePairs());
                        assertTrue(snapshot.size() >= 100);
                        snapshotCount.incrementAndGet();
                    } catch (Exception e) {
                        exceptions.incrementAndGet();
                        throw e;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                endLatch.countDown();
            }
        });

        // Thread 2: Continuously add more items
        executorService.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 500; i++) {
                    storage.getRequestResponsePairs().add(
                            new RequestResponsePair("concurrent-" + i, null, new Date(), null)
                    );
                }
            } catch (Exception e) {
                exceptions.incrementAndGet();
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = endLatch.await(10, java.util.concurrent.TimeUnit.SECONDS);

        executorService.shutdown();

        assertTrue(completed, "Test did not complete in time");
        assertEquals(0, exceptions.get(), "Should not have any exceptions");
        assertTrue(snapshotCount.get() > 0, "Should have created snapshots");
        assertEquals(600, storage.getRequestResponsePairs().size(), 
                "Should have 600 items (100 initial + 500 concurrent)");
    }
}
