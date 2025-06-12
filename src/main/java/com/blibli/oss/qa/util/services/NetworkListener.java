package com.blibli.oss.qa.util.services;

import com.blibli.oss.qa.util.model.Constant;
import com.blibli.oss.qa.util.model.RequestResponseStorage;
import de.sstoehr.harreader.HarWriter;
import de.sstoehr.harreader.HarWriterException;
import de.sstoehr.harreader.model.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v137.network.Network;
import org.openqa.selenium.devtools.v137.network.model.Request;
import org.openqa.selenium.devtools.v137.network.model.Response;
import org.openqa.selenium.remote.Augmenter;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.util.*;

@Slf4j
public class NetworkListener {
    @Setter
    @Getter
    private WebDriver driver;
    private String baseRemoteUrl;
    private DevTools devTools;
    private String harFile;
    /**
     * -- SETTER --
     *  Setup Charset on the file generation
     *
     * @param charset default will be UTF-8 , you can get from here https://docs.oracle.com/javase/8/docs/api/java/nio/charset/Charset.html
     */
    @Setter
    private String charset = Constant.DEFAULT_UNICODE;

    private HarCreatorBrowser harCreatorBrowser;

    private final Map<String, RequestResponseStorage> windowHandleStorageMap = new HashMap<>();
    private RequestResponseStorage requestResponseStorage;


    /**
     * Generate new network listener object
     *
     * @param driver      chrome driver that you are using
     * @param harFileName file will be stored under target folder
     */
    public NetworkListener(WebDriver driver, String harFileName) {
        this.driver = driver;
        this.harFile = harFileName;
        try {
            Files.delete(java.nio.file.Paths.get(harFile));
        } catch (IOException e) {
            // Since it's expected to be failed , log level info is good
            log.info("Not able to find prevous har file " + e.getMessage());
        }
        devTools = ((ChromiumDriver) driver).getDevTools();
        createHarBrowser();
    }
    public NetworkListener(WebDriver driver , DevTools devTools , String harFileName){
        this.devTools = devTools;
        this.driver = driver;
        this.harFile = harFileName;
        try {
            Files.delete(java.nio.file.Paths.get(harFile));
        } catch (IOException e) {
            // Since it's expected to be failed , log level info is good
            log.info("Not able to find prevous har file " + e.getMessage());
        }
        createHarBrowser();
    }


    /**
     * Generate new network listener object
     *
     * @param driver        chrome driver that you are using
     * @param harFileName   file will be stored under target folder
     * @param baseRemoteUrl Base Selenoid URl that you are using
     */
    public NetworkListener(WebDriver driver, String harFileName, String baseRemoteUrl) {
        this.driver = driver;
        this.harFile = harFileName;
        this.baseRemoteUrl = baseRemoteUrl;
        try {
            Files.delete(java.nio.file.Paths.get(harFile));
        } catch (IOException e) {
            // Since it's expected to be failed , log level info is good
            log.info("Not able to find prevous har file " + e.getMessage());
        }
        createHarBrowser();
    }


    /**
     * Generate new network listener object
     *
     * @param harFileName file will be stored under target folder
     */

    public NetworkListener(String harFileName) {
        this.harFile = harFileName;
    }

    private static String convertInputStreamToString(InputStream inputStream) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line = bufferedReader.readLine();
            while (line != null) {
                stringBuilder.append(line);
                line = bufferedReader.readLine();
            }
        }
        return stringBuilder.toString();
    }

    public void start() {
        // main listener to intercept response and continue
//        initializeCdp();
//        Filter filterResponses = next -> req -> {
//            Long startTime = System.currentTimeMillis();
//            HttpResponse res = next.execute(req);
//            Long endTime = System.currentTimeMillis();
//            harModelHashMap.put(Lists.newArrayList(startTime, endTime), new HarModel(req, res));
//            return res;
//        };
//        NetworkInterceptor networkInterceptor = new NetworkInterceptor(driver, filterResponses);
        start(driver.getWindowHandle());
    }

    public void start(String windowHandle) {
        initializeCdp();
        devTools.createSession(windowHandle);
        devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()));
        devTools.clearListeners();

        requestResponseStorage = windowHandleStorageMap.get(windowHandle);
        if (requestResponseStorage == null) {
            requestResponseStorage = new RequestResponseStorage();
            windowHandleStorageMap.put(windowHandle, requestResponseStorage);

            devTools.addListener(Network.requestWillBeSent(), requestConsumer -> {
                Request request = requestConsumer.getRequest();
                requestResponseStorage.addRequest(request, new Date());
            });

            devTools.addListener(Network.responseReceived(), responseConsumer -> {
                Response response = responseConsumer.getResponse();
                String responseBody =
                    devTools.send(Network.getResponseBody(responseConsumer.getRequestId()))
                        .getBody();
                requestResponseStorage.addResponse(response, responseBody);
            });
        }
    }

    private void initializeCdp(){
        if(this.devTools != null ){
            devTools.createSessionIfThereIsNotOne();
            return;
        }
        try {
            if (driver instanceof RemoteWebDriver) {
                this.devTools = getCdpUsingCustomurl();
            } else {
                this.devTools = ((HasDevTools) driver).getDevTools();
            }
            devTools.createSessionIfThereIsNotOne();
        } catch (Exception e) {
            log.error("CDP Can't be initialized " , e);
        }
    }

    public DevTools getCdpUsingCustomurl() {
        // Before proceeding, reach into the driver and manipulate the capabilities to
        // include the se:cdp and se:cdpVersion keys.
        try {
            Field capabilitiesField = RemoteWebDriver.class.getDeclaredField("capabilities");
            capabilitiesField.setAccessible(true);
            String sessionId = ((RemoteWebDriver) driver).getSessionId().toString();
            String devtoolsUrl = String.format("ws://%s/devtools/%s/page", baseRemoteUrl, sessionId);

            MutableCapabilities mutableCapabilities = (MutableCapabilities) capabilitiesField.get(driver);
            mutableCapabilities.setCapability("se:cdp", devtoolsUrl);
            mutableCapabilities.setCapability("se:cdpVersion", mutableCapabilities.getBrowserVersion());
        } catch (Exception e) {
            log.info("Failed to spoof RemoteWebDriver capabilities :sadpanda:");
        }

        // Proceed to "augment" the driver and get a dev tools client ...
        RemoteWebDriver augmenteDriver = (RemoteWebDriver) new Augmenter().augment(driver);
        DevTools devTools = ((HasDevTools) augmenteDriver).getDevTools();
        this.driver = augmenteDriver;
        return devTools;
    }

    public void switchWindow(String windowHandle) {
        start(windowHandle);
        driver.navigate().refresh();
    }

    public void createHarFile() {
        List<HarPage> harPages = new ArrayList<>();
        List<HarEntry> harEntries = new ArrayList<>();

        windowHandleStorageMap.forEach((windowHandle, reqResStorage) -> {
            harPages.add(createHarPage(windowHandle));
            reqResStorage.getRequestResponsePairs().forEach(pair -> {
                List<Long> time = new ArrayList<>();
                if (pair.getResponse() != null) {
                    pair.getResponse().getTiming().ifPresent(timing -> {
                        time.add(pair.getRequestOn().getTime());
                        time.add(timing.getReceiveHeadersEnd().longValue());
                    });
                    harEntries.add(createHarEntry(pair.getRequest(),
                        pair.getResponse(),
                        time,
                        windowHandle,
                        pair.getResponseBody()));
                } else {
                    log.info("Response is null for %s", pair.getRequest().getUrl());
                    harEntries.add(createHarEntry(pair.getRequest(),
                        null,
                        time,
                        windowHandle,
                        null));
                }
            });
        });
        log.info("har entry size : %d", harEntries.size());
        HarLog harLog = HarLog.builder()
                .creator(harCreatorBrowser)
                .browser(harCreatorBrowser)
                .pages(harPages)
                .entries(harEntries)
                        .build();
        Har har = Har.builder().log(harLog).build();
        createFile(har);
    }

    public void createHarFile(String filter) {
        List<HarPage> harPages = new ArrayList<>();
        List<HarEntry> harEntries = new ArrayList<>();

        windowHandleStorageMap.forEach((windowHandle, reqResStorage) -> {
            harPages.add(createHarPage(windowHandle));
            reqResStorage.getRequestResponsePairs().forEach(pair -> {
                List<Long> time = new ArrayList<>();
                if (pair.getRequest().getUrl().contains(filter) && pair.getResponse() != null) {
                    pair.getResponse().getTiming().ifPresent(timing -> {
                        time.add(pair.getRequestOn().getTime());
                        time.add(timing.getReceiveHeadersEnd().longValue());
                    });
                    harEntries.add(createHarEntry(pair.getRequest(),
                        pair.getResponse(),
                        time,
                        windowHandle,
                        pair.getResponseBody()));
                }
            });
        });
        log.info("har entry size : %d", harEntries.size());
        HarLog harLog = HarLog.builder()
                .pages(harPages)
                .entries(harEntries)
                .build();
        Har har = Har.builder().log(harLog).build();
        createFile(har);
    }

    private void createFile(Har har) {
        HarWriter harWriter = new HarWriter();
        try {
            harWriter.writeTo(new File(harFile), har);
        } catch (HarWriterException e) {
            throw new RuntimeException(e);
        }
    }

    private void createHarBrowser() {
        harCreatorBrowser = HarCreatorBrowser.builder()
                .name("gdn-qa-automation")
                .version(getClass().getPackage().getImplementationVersion())
                .comment("Created by HAR utils")
                        .build();
    }

    public HarPage createHarPage(String title) {
        HarPageTiming harPageTiming = HarPageTiming.builder()
                .onContentLoad(0)
                .build();
        return HarPage.builder()
                .comment("Create by Har Utils")
                .pageTimings(harPageTiming)
                .startedDateTime(ZonedDateTime.now())
                .id(title)
                .build();
    }

    public HarEntry createHarEntry(Request request,
        Response response,
        List<Long> time,
        String pagref,
        String responseBody) {
        HarEntryConverter harEntry =
            new HarEntryConverter(request, response, time, pagref, responseBody);
        harEntry.setup();
        return harEntry.getHarEntry();
    }


    /**
     * @param networkListener - NetworkListener
     * @param driver browser driver
     * @param tabIndex num tab of your destination, 0 is first tab index
     */
    public static void switchTab(NetworkListener networkListener, WebDriver driver, Integer tabIndex) {
        driver.switchTo().window(new ArrayList<>(driver.getWindowHandles()).get(tabIndex));
        networkListener.start();
        driver.navigate().refresh();
    }

}
