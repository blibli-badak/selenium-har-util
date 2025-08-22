package com.blibli.oss.qa.util.services;

import com.blibli.oss.qa.util.model.CDPIOReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.devtools.Command;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v137.tracing.Tracing;
import org.openqa.selenium.devtools.v137.tracing.model.TraceConfig;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TracerServices {
    // Semaphore to wait for tracing completion - starts with 0 permits (blocked)
    private final Semaphore traceSemaphore = new Semaphore(0);
    private final DevTools devTools;
    private final String profileName;
    private boolean traceCompleted = false;

    private Command<Void> createSessions(DevTools devTools){
        // Ref: https://chromedevtools.github.io/devtools-protocol/tot/Tracing/#method-end
        List<String> incTraceCategories = devTools.send(Tracing.getCategories());
        return Tracing.start(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(Tracing.StartTransferMode.RETURNASSTREAM),
                Optional.empty(),
                Optional.empty(),
                Optional.of(new TraceConfig(
                        Optional.of(TraceConfig.RecordMode.RECORDASMUCHASPOSSIBLE), // Ref: https://chromium.googlesource.com/chromium/src/+/56398249f62df4942576ebbaec482d6fd8cea9a7/base/trace_event/trace_config.h
                        Optional.of(400 * 1024),
                        Optional.of(false),
                        Optional.of(true),
                        Optional.of(true),
                        Optional.of(incTraceCategories),
                        Optional.of(List.of()),
                        Optional.empty(),
                        Optional.empty()
                )),
                Optional.empty(),
                Optional.empty()
        );
    }

    public TracerServices(DevTools devTools , String profileName) {
        this.devTools = devTools;
        this.profileName = profileName;
    }

    // activate tracing session
    // Ref: https://chromedevtools.github.io/devtools-protocol/tot/Tracing/#method-start
    // Ref: https://chromium.googlesource.com/chromium/src/+/56398249f62df4942576ebbaec482d6fd8cea9a7/base/trace_event/trace_config.h
    public void start(){
        log.info("Starting tracing session for profile: {}", profileName);
        devTools.send(createSessions(devTools));
        // Ref: https://chromedevtools.github.io/devtools-protocol/tot/Tracing/#event-dataCollected
        devTools.addListener(Tracing.dataCollected(), maps -> {
            try {
                var objMapper = new ObjectMapper();
                Files.writeString(
                        Paths.get(
                                "target",
                                "Trace - " + profileName + ".json"
                        ),
                        objMapper.writeValueAsString(maps)
                );
                log.debug("Trace data collected and written to target directory");
            } catch (Exception e) {
                log.warn("Error during profile listener on data collected ", e);
            }
        });
        devTools.addListener(Tracing.tracingComplete(), tracingComplete -> {
            log.info("Tracing complete event received for profile: {}", profileName);
            
            // Use a separate thread to handle data reading to avoid blocking the CDP connection
            Thread dataWriterThread = new Thread(() -> {
                try {
                    var readable = new CDPIOReader(
                            devTools,
                            tracingComplete.getStream().orElseThrow(),
                            50 * 1024 * 1024
                    );
                    var reportFile = Paths.get(profileName + ".json").toFile();
                    log.info("Profile path: {}", reportFile.getAbsolutePath());
                    
                    long totalBytesWritten = 0;
                    try (FileOutputStream fos = new FileOutputStream(reportFile)) {
                        while (readable.hasNext()) {
                            try {
                                byte[] chunk = readable.next().get();
                                if (chunk.length == 0) {
                                    log.debug("Received empty chunk, ending trace data reading");
                                    break;
                                }
                                fos.write(chunk, 0, chunk.length);
                                fos.flush(); // Ensure data is written immediately
                                totalBytesWritten += chunk.length;
                                
                                if (totalBytesWritten % (1024 * 1024) == 0) { // Log every MB
                                    log.debug("Written {} MB of trace data", totalBytesWritten / (1024 * 1024));
                                }
                            } catch (Exception chunkException) {
                                log.warn("Error reading chunk at {} bytes: {}", totalBytesWritten, chunkException.getMessage());
                                break; // Stop reading on any chunk error
                            }
                        }
                        fos.flush();
                        log.info("Trace data writing completed. Total bytes written: {}", totalBytesWritten);
                        traceCompleted = true;
                    } catch (Exception fileException) {
                        log.error("Error writing trace file: ", fileException);
                        
                        // Create a minimal valid JSON file as fallback
                        try {
                            String fallbackContent = "{\n  \"traceEvents\": [],\n  \"metadata\": {\n    \"note\": \"Trace data incomplete due to CDP connection error\",\n    \"profile\": \"" + profileName + "\",\n    \"bytesWritten\": " + totalBytesWritten + "\n  }\n}";
                            Files.writeString(Paths.get(profileName + ".json"), fallbackContent);
                            log.info("Created fallback trace file due to data writing error");
                        } catch (Exception fallbackEx) {
                            log.error("Failed to create fallback trace file", fallbackEx);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error during trace data processing: ", e);
                } finally {
                    // Always release the semaphore to prevent hanging
                    traceSemaphore.release();
                    log.debug("Semaphore released for profile: {}", profileName);
                }
            });
            
            dataWriterThread.setName("TraceWriter-" + profileName);
            dataWriterThread.start();
        });
    }

    public void stop() throws Exception {
        try {
            log.info("Stopping tracing session for profile: {}", profileName);
            devTools.send(Tracing.end());
            var startTime = System.currentTimeMillis();
            log.info("Waiting for trace completion...");
            // Reduce timeout to 15 seconds for more responsive testing
            if (traceSemaphore.tryAcquire(15, TimeUnit.SECONDS)) {
                var duration = (System.currentTimeMillis() - startTime) / 1000;
                log.info("Trace profile saved | Duration: {}s | Completed: {} ", duration, traceCompleted);
            } else {
                var duration = (System.currentTimeMillis() - startTime) / 1000;
                log.warn("Trace profile not saved within 15s timeout. This may be due to Chrome DevTools version mismatch (CDP v139 vs v137)");
                log.info("Attempting to create a simple trace file as fallback...");
                
                // Only create fallback if the real trace file doesn't exist or is empty
                var traceFile = Paths.get(profileName + ".json");
                if (!Files.exists(traceFile) || Files.size(traceFile) == 0) {
                    try {
                        String fallbackContent = "{\n  \"traceEvents\": [],\n  \"metadata\": {\n    \"note\": \"Fallback trace file - CDP version mismatch prevented full tracing\",\n    \"profile\": \"" + profileName + "\"\n  }\n}";
                        Files.writeString(traceFile, fallbackContent);
                        log.info("Fallback trace file created: {}", traceFile.toAbsolutePath());
                    } catch (Exception fallbackEx) {
                        log.error("Failed to create fallback trace file", fallbackEx);
                    }
                } else {
                    log.info("Real trace file already exists ({}MB), skipping fallback creation", Files.size(traceFile) / (1024 * 1024));
                }
            }
        } catch (Exception e) {
            log.error("Error during stop tracing ", e);
            throw e;
        }
    }
}
