package com.blibli.oss.qa.util.services;

import com.blibli.oss.qa.util.model.HarModel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.sstoehr.harreader.model.*;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v104.network.Network;
import org.openqa.selenium.devtools.v104.network.model.Request;
import org.openqa.selenium.devtools.v104.network.model.Response;
import org.openqa.selenium.remote.Augmenter;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.http.HttpRequest;
import org.openqa.selenium.remote.http.HttpResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.*;

public class NetworkListener {
    static final String targetPathFile = System.getProperty("user.dir") + "/target/";
    private final ArrayList<HttpRequest> requests = new ArrayList<>();
    private final ArrayList<HttpResponse> responses = new ArrayList<>();
    private final HashMap<String, HarModel> harModelHashMap = new HashMap<>();
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
        devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()));
        devTools.addListener(Network.requestWillBeSent(), request -> {
            HarModel harModel = harModelHashMap.get(String.valueOf(request.getRequestId()));
            if (harModel != null) {
                harModelHashMap.put(String.valueOf(request.getRequestId()), new HarModel(request.getRequest(), harModel.getResponse()));
            } else {
                harModelHashMap.put(String.valueOf(request.getRequestId()), new HarModel(request.getRequest()));
            }
        });
        devTools.addListener(Network.responseReceived(), response -> {
            HarModel harModel = harModelHashMap.get(String.valueOf(response.getRequestId()));
            if (harModel != null) {
                harModelHashMap.put(String.valueOf(response.getRequestId()), new HarModel(harModel.getRequest(), response.getResponse()));
            } else {
                harModelHashMap.put(String.valueOf(response.getRequestId()), new HarModel(response.getResponse()));
            }
        });
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
            System.err.println("Failed to spoof RemoteWebDriver capabilities :sadpanda:");
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
        Har har = new Har();
        HarLog harLog = new HarLog();
        harLog.setCreator(harCreatorBrowser);
        harLog.setBrowser(harCreatorBrowser);
        List<HarPage> harPages = new ArrayList<>();
        List<HarEntry> harEntries = new ArrayList<>();
        // create har page
        String firstHarSetKey = harModelHashMap.entrySet().iterator().next().getKey();
        harPages.add(createHarPage(harModelHashMap.get(firstHarSetKey).getRequest(), harModelHashMap.get(firstHarSetKey).getResponse()));
        System.out.println("request size : " + requests.size() + " response size : " + responses.size());
        // looping each harModelHashMap
        for (Map.Entry<String, HarModel> entry : harModelHashMap.entrySet()) {
            try {
                harEntries.add(createHarEntry(entry.getValue().getRequest(), entry.getValue().getResponse(), entry.getValue().getResponse().getResponseTime().get().toJson().longValue()));
            } catch (Exception e) {
                System.out.println("error processing data: " + e.getMessage() + " Ini errornya ");
                System.out.println(entry.getValue().getResponse());
            }
        }
        System.out.printf("har entry size : %d", harEntries.size());
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
        } catch (JsonProcessingException e) {
            e.printStackTrace();
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

    public HarPage createHarPage(Request request, Response response) {
        HarPage harPage = new HarPage();
        harPage.setComment("Create by Har Utils");
        HarPageTiming harPageTiming = new HarPageTiming();
        harPageTiming.setOnContentLoad(0);
        harPage.setPageTimings(harPageTiming);
        harPage.setStartedDateTime(new Date());
        harPage.setTitle(request.getUrl() != null ? request.getUrl() : response.getUrl());
        return harPage;
    }

    public HarEntry createHarEntry(Request request, Response response, long time) {
        HarEntryConverter harEntry = new HarEntryConverter(request, response, time);
        harEntry.setup();
        return harEntry.getHarEntry();
    }
}
