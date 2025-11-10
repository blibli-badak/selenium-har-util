package com.blibli.oss.qa.util.services;

import com.blibli.oss.qa.util.model.Constant;
import com.blibli.oss.qa.util.model.HarModel;
import com.blibli.oss.qa.util.model.RequestResponsePair;
import com.blibli.oss.qa.util.model.RequestResponseStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.sstoehr.harreader.model.Har;
import de.sstoehr.harreader.model.HarCreatorBrowser;
import de.sstoehr.harreader.model.HarEntry;
import de.sstoehr.harreader.model.HarLog;
import de.sstoehr.harreader.model.HarPage;
import de.sstoehr.harreader.model.HarPageTiming;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v137.network.Network;
import org.openqa.selenium.devtools.v137.network.model.*;
import org.openqa.selenium.remote.Augmenter;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class NetworkListener {
    static final String TARGET_PATH_FILE = System.getProperty("user.dir") + "/target/";
    private final ConcurrentHashMap<List<Long>, HarModel> harModelHashMap = new ConcurrentHashMap<>();
    private WebDriver driver;
    private String baseRemoteUrl;
    private DevTools devTools;
    private String harFile = "";
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
                String requestId = String.valueOf(requestConsumer.getRequestId());
                Request request = requestConsumer.getRequest();
                requestResponseStorage.addRequest(requestId, request, new Date());
            });

            devTools.addListener(Network.responseReceived(), responseConsumer -> {
                Response response = responseConsumer.getResponse();
                String responseBody =
                    devTools.send(Network.getResponseBody(responseConsumer.getRequestId()))
                        .getBody();
                requestResponseStorage.addResponse(response, responseBody);
            });

            devTools.addListener(Network.loadingFailed(), loadingFailedConsumer -> {
                requestResponseStorage.addLoadingFailed(loadingFailedConsumer);
            });

            devTools.addListener(Network.responseReceivedExtraInfo(), responseReceivedExtraInfoConsumer -> {
                requestResponseStorage.addresponseReceivedExtraInfo(responseReceivedExtraInfoConsumer);
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

    public WebDriver getDriver() {
        return driver;
    }

    public void setDriver(WebDriver driver) {
        this.driver = driver;
    }

    public void switchWindow(String windowHandle) {
        start(windowHandle);
        driver.navigate().refresh();
    }

    public void createHarFile() {
        Har har = new Har();
        HarLog harLog = new HarLog();
        harLog.setCreator(harCreatorBrowser);
        harLog.setBrowser(harCreatorBrowser);
        List<HarPage> harPages = new ArrayList<>();
        List<HarEntry> harEntries = new ArrayList<>();

        windowHandleStorageMap.forEach((windowHandle, reqResStorage) -> {
            harPages.add(createHarPage(windowHandle));
            reqResStorage.getRequestResponsePairs().forEach(pair -> {
                harEntries.addAll(saveHarEntry(pair, windowHandle));
            });
        });
            log.info("har entry size : {}", harEntries.size());
        harLog.setPages(harPages);
        harLog.setEntries(harEntries);
        har.setLog(harLog);
        createFile(har);
    }

    public void createHarFile(String filter) {
        System.out.println("---- createHarFile - Filter ----");
        Har har = new Har();
        HarLog harLog = new HarLog();
        harLog.setCreator(harCreatorBrowser);
        harLog.setBrowser(harCreatorBrowser);
        List<HarPage> harPages = new ArrayList<>();
        List<HarEntry> harEntries = new ArrayList<>();

        windowHandleStorageMap.forEach((windowHandle, reqResStorage) -> {
            harPages.add(createHarPage(windowHandle));
            reqResStorage.getRequestResponsePairs().forEach(pair -> {
                if (pair.getRequest().getUrl().contains(filter)) {
                    harEntries.addAll(saveHarEntry(pair,windowHandle));
                }
            });
        });
    log.info("har entry size : {}", harEntries.size());
        harLog.setPages(harPages);

        harLog.setEntries(harEntries);
        har.setLog(harLog);
        createFile(har);
    }

    private List<HarEntry> saveHarEntry(RequestResponsePair pair, String windowHandle){
        List<HarEntry> result = new ArrayList<>();
        List<Long> time = new ArrayList<>();
        if (pair.getResponse() != null) {
            pair.getResponse().getTiming().ifPresent(timing -> {
                time.add(pair.getRequestOn().getTime());
                time.add(timing.getReceiveHeadersEnd().longValue());
            });
            result.add(createHarEntry(pair.getRequest(),
                    pair.getResponse(),
                    time,
                    windowHandle,
                    pair.getResponseBody(),
                    pair.getLoadingFailed(),
                    pair.getResponseReceivedExtraInfo()));
        } else {
            log.info("Response is null for {}", pair.getRequest().getUrl());
            result.add(createHarEntry(pair.getRequest(),
                    null,
                    time,
                    windowHandle,
                    null,
                    pair.getLoadingFailed(),
                    pair.getResponseReceivedExtraInfo()));
        }
        return result;
    }

    private void createFile(Har har) {
        ObjectMapper om = new ObjectMapper();
        try {
            String json = new String(om.writeValueAsString(har).getBytes(), charset);
            // write json to file
            Files.write(java.nio.file.Paths.get(harFile), json.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Setup Charset on the file generation
     * @param charset default will be UTF-8 , you can get from here https://docs.oracle.com/javase/8/docs/api/java/nio/charset/Charset.html
     */
    public void setCharset(String charset){
        this.charset = charset;
    }

    private void createHarBrowser() {
        harCreatorBrowser = new HarCreatorBrowser();
        harCreatorBrowser.setName("gdn-qa-automation");
        harCreatorBrowser.setVersion("0.0.1");
        harCreatorBrowser.setComment("Created by HAR utils");
    }

    public HarPage createHarPage(String title) {
        HarPage harPage = new HarPage();
        harPage.setComment("Create by Har Utils");
        HarPageTiming harPageTiming = new HarPageTiming();
        harPageTiming.setOnContentLoad(0);
        harPage.setPageTimings(harPageTiming);
        harPage.setStartedDateTime(new Date());
        harPage.setId(title);
        return harPage;
    }

    public HarEntry createHarEntry(Request request,
                                   Response response,
                                   List<Long> time,
                                   String pagref,
                                   String responseBody,
                                   LoadingFailed loadingFailed,
                                   ResponseReceivedExtraInfo responseReceivedExtraInfo) {
        HarEntryConverter harEntry =
                new HarEntryConverter(request, response,loadingFailed, responseReceivedExtraInfo, time, pagref, responseBody);
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

    public DevTools getDevTools() {
        return devTools;
    }
}
