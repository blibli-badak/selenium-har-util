package com.blibli.oss.qa.util.services;

import com.blibli.oss.qa.util.model.HarModel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import de.sstoehr.harreader.model.*;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.NetworkInterceptor;
import org.openqa.selenium.remote.Augmenter;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.http.Filter;
import org.openqa.selenium.remote.http.HttpRequest;
import org.openqa.selenium.remote.http.HttpResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class NetworkListener {
    static final String targetPathFile = System.getProperty("user.dir") + "/target/";
    private final ConcurrentHashMap<List<Long>, HarModel> harModelHashMap = new ConcurrentHashMap<>();
    private WebDriver driver;
    private String baseRemoteUrl;
    private DevTools devTools;
    private String harFile = "";

    private HarCreatorBrowser harCreatorBrowser;

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
            //ignored
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
            //ignored
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
        try {
            if (driver instanceof RemoteWebDriver) {
                this.devTools = getCdpUsingCustomurl();
            } else {
                this.devTools = ((HasDevTools) driver).getDevTools();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        devTools.createSession();
        Filter reportStatusCodes = next -> req -> {
            Long startTime = System.currentTimeMillis();
            // add trycatch
            HttpResponse res = next.execute(req);
            Long endTime = System.currentTimeMillis();
            harModelHashMap.put(Lists.newArrayList(startTime, endTime), new HarModel(req, res));
            return res;
        };
        NetworkInterceptor networkInterceptor = new NetworkInterceptor(driver, reportStatusCodes);
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

    public void createHarFile() {
        createHarFile("");
    }

    public void createHarFile(String filterString){
        Har har = new Har();
        HarLog harLog = new HarLog();
        harLog.setCreator(harCreatorBrowser);
        harLog.setBrowser(harCreatorBrowser);
        List<HarPage> harPages = new ArrayList<>();
        List<HarEntry> harEntries = new ArrayList<>();
        // looping each harModelHashMap
//        String firstKey = harModelHashMap.keySet().stream().min(String::compareTo).get();
//        harPages.add(createHarPage(harModelHashMap.get(firstKey).getHttpRequest(), harModelHashMap.get(firstKey).getHttpResponse()));
        for (Map.Entry<List<Long>, HarModel> entry : harModelHashMap.entrySet()) {
            log.debug("Processing Har Entry   " + entry.getKey() + " Request URL "  + entry.getValue().getHttpRequest().getUri());
            try {
                if(entry.getValue().getHttpRequest().getUri().contains(filterString)){
                    harEntries.add(createHarEntry(entry.getValue().getHttpRequest(),entry.getValue().getHttpResponse(), entry.getKey()));
                }
//                System.out.println(entry.getValue().getHttpResponse().getStatus());
            } catch (Exception e) {
                log.error(e.getMessage() , e);
            }
        }
        log.info("har entry size : %d", harEntries.size());
        harLog.setPages(harPages);
        harLog.setEntries(harEntries);
        har.setLog(harLog);
        createFile(har);
    }

    public void createFile(Har har) {
        ObjectMapper om = new ObjectMapper();
        try {
            String json = om.writeValueAsString(har);
            // write json to file
            Files.write(java.nio.file.Paths.get(harFile), json.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createHarBrowser() {
        harCreatorBrowser = new HarCreatorBrowser();
        harCreatorBrowser.setName("gdn-qa-automation");
        harCreatorBrowser.setVersion("0.0.1");
        harCreatorBrowser.setComment("Created by HAR utils");
    }

    public HarPage createHarPage(HttpRequest request , HttpResponse response) {
        HarPage harPage = new HarPage();
        harPage.setComment("Create by Har Utils");
        HarPageTiming harPageTiming = new HarPageTiming();
        harPageTiming.setOnContentLoad(0);
        harPage.setPageTimings(harPageTiming);
//        harPage.setId("Page_" + counter);
        harPage.setStartedDateTime(new Date());
        harPage.setTitle(request.getUri());
        return harPage;
    }

    public HarEntry createHarEntry(HttpRequest httpRequest, HttpResponse httpResponse, List<Long> time) {
        HarEntryConverter harEntry = new HarEntryConverter(httpRequest, httpResponse, time);
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
