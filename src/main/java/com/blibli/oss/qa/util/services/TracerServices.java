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
    // todo : add semaphore to prevent concurrent access to tracing
    private final Semaphore traceSemaphore = new Semaphore(1);
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
                traceSemaphore.release();
            } catch (Exception e) {
                log.warn("Error during profile listener on data collected ", e);
            }
        });
        devTools.addListener(Tracing.tracingComplete(), tracingComplete -> {
            var readable = new CDPIOReader(
                    devTools,
                    tracingComplete.getStream().orElseThrow(),
                    50 * 1024 * 1024
            );
            var reportFile = Paths.get(
                     profileName + ".json"
            ).toFile();
            log.info("Profile path: {0}", new Object[]{ reportFile.getName() });
            try (
                    FileOutputStream fos = new FileOutputStream(reportFile)
            ) {
                while (readable.hasNext()) {
                    byte[] chunk = readable.next().get();
                    if (chunk.length == 0) break;
                    fos.write(chunk, 0, chunk.length);
                }
                traceCompleted = true;
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                traceSemaphore.release();
            }
        });
    }

    public void stop() throws Exception {
        try {
            devTools.send(Tracing.end());
            var startTime = System.currentTimeMillis();
            if (traceSemaphore.tryAcquire(15, TimeUnit.MINUTES)) {
                var duration = (System.currentTimeMillis() - startTime) / 1000;
                log.info("Trace profile saved | Duration: {0}s | Completed: {1} ", new Object[]{duration, traceCompleted});
            } else {
                log.info("Trace profile not saved within given time");
            }
        } catch (Exception e) {
            log.error("Error during stop tracing ", e);
            throw e;
        }
    }
}
