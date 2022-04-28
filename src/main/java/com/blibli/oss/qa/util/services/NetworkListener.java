package com.blibli.oss.qa.util.services;

import com.blibli.oss.qa.util.model.HarModel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.sstoehr.harreader.model.*;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.devtools.*;
import org.openqa.selenium.devtools.noop.NoOpCdpInfo;
import org.openqa.selenium.remote.Augmenter;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.http.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.*;

public class NetworkListener {
    private WebDriver driver;
    private String baseRemoteUrl;
    private DevTools devTools;
    private ArrayList<HttpRequest> requests = new ArrayList<>();
    private ArrayList<HttpResponse> responses = new ArrayList<>();

    private HashMap<Long , HarModel> harModelHashMap = new HashMap<>();

    static final String targetPathFile = System.getProperty("user.dir") + "/target/";
    private String harFile = "";

    private HarCreatorBrowser harCreatorBrowser;

    /**
     * Generate new network listener object
     * @param driver chrome driver that you are using
     * @param harFileName file will be stored under target folder
     */
    public NetworkListener(WebDriver driver , String harFileName) {
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
     * @param driver chrome driver that you are using
     * @param harFileName file will be stored under target folder
     * @param baseRemoteUrl Base Selenoid URl that you are using
     */
    public NetworkListener(WebDriver driver , String harFileName , String baseRemoteUrl) {
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
     * @param harFileName file will be stored under target folder
     */

    public NetworkListener(String harFileName) {
        this.harFile = harFileName;
    }

    public void setDriver(WebDriver driver) {
        this.driver = driver;
    }


    public void start() {
        // main listener to intercept response and continue
        try {
            if(driver instanceof RemoteWebDriver) {
                this.devTools = getCdpUsingCustomurl();
            }else {
                this.devTools = ((HasDevTools)driver).getDevTools();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        devTools.createSession();
        Filter reportStatusCodes = next -> req -> {
            HttpResponse res = next.execute(req);
//            responses.add(res);
//            requests.add(req);
            harModelHashMap.put(Calendar.getInstance().getTimeInMillis(), new HarModel(req, res));
            return res;
        };
        NetworkInterceptor networkInterceptor = new NetworkInterceptor(driver, reportStatusCodes);
    }

    public DevTools getCdpUsingCustomurl(){
    // Before proceeding, reach into the driver and manipulate the capabilities to
    // include the se:cdp and se:cdpVersion keys.
        try {
            Field capabilitiesField = RemoteWebDriver.class.getDeclaredField("capabilities");
            capabilitiesField.setAccessible(true);
            String sessionId =  ((RemoteWebDriver) driver).getSessionId().toString();
            String devtoolsUrl = String.format("ws://%s/devtools/%s/page", baseRemoteUrl ,sessionId);

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

    public void createHarFile() {
        Har har = new Har();
        HarLog harLog = new HarLog();
        harLog.setCreator(harCreatorBrowser);
        harLog.setBrowser(harCreatorBrowser);
        int counter = 0;
        List<HarPage> harPages = new ArrayList<>();
        List<HarEntry> harEntries = new ArrayList<>();
        // create har page
        Long firstKey = harModelHashMap.keySet().stream().min(Long::compareTo).get();
        harPages.add(createHarPage(harModelHashMap.get(firstKey).getHttpRequest(), harModelHashMap.get(firstKey).getHttpResponse(),counter));
        System.out.println("request size : " + requests.size() + " response size : " + responses.size());
        // looping each harModelHashMap
        for (Map.Entry<Long, HarModel> entry : harModelHashMap.entrySet()) {
            try{
                harEntries.add(createHarEntry(entry.getValue().getHttpRequest(),entry.getValue().getHttpResponse(), counter, entry.getKey()));
            }catch (Exception e){
                System.out.println("error processing data: " + e.getMessage() + " Ini errornya ");
                System.out.println(entry.getValue().getHttpResponse());
            }

            counter++;
        }
        System.out.println("Counter : " + counter);
        System.out.printf("har entry size : %d", harEntries.size());
        harLog.setEntries(harEntries);
        harLog.setPages(harPages);
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

    public HarPage createHarPage(HttpRequest request , HttpResponse response, long counter) {
        HarPage harPage = new HarPage();
        harPage.setComment("Create by Har Utils");
        HarPageTiming harPageTiming = new HarPageTiming();
        harPageTiming.setOnContentLoad(0);
        harPage.setPageTimings(harPageTiming);
        harPage.setId("Page_" + counter);
        harPage.setStartedDateTime(new Date());
        harPage.setTitle(request.getUri());
        return harPage;
    }

    public HarEntry createHarEntry(HttpRequest request , HttpResponse response, int counter , long time) {
        HarEntryConverter harEntry = new HarEntryConverter(request,response,counter , time);
        harEntry.setup();
        return harEntry.getHarEntry();
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
}
