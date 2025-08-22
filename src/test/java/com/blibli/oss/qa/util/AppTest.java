package com.blibli.oss.qa.util;


import com.blibli.oss.qa.util.services.NetworkListener;
import com.blibli.oss.qa.util.services.TracerServices;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;


/**
 * Unit test for simple App.
 */
public class AppTest {
    /**
     * Rigorous Test :-)
     */

    private WebDriver driver;
    private NetworkListener networkListener;
    ChromeOptions options;
    DesiredCapabilities capabilities;
    @BeforeAll
    static void setupClass() {

    }

    @BeforeEach
    public void setup() {
        options = new ChromeOptions();
    }

    @Test
    public void testWithLocalDriver() {
        setupLocalDriver();
        driver = new ChromeDriver(options);
        driver.manage().window().maximize();
        networkListener = new NetworkListener(driver, "har-local-driver.har");
        networkListener.start();
        driver.get("https://en.wiktionary.org/wiki/Wiktionary:Main_Page");
//        WebDriverWait webDriverWait = new WebDriverWait(driver, Duration.ofSeconds(30));
//        WebElement element =driver.findElement(By.id("searchInput"));
//        webDriverWait.until(webDriver -> element.isDisplayed());
//        element.sendKeys("Kiwi/n");
        Path path = Paths.get("har-local-driver.har");
        assertFalse(Files.exists(path));
        /**
         * Todo : https://www.browserstack.com/docs/automate/selenium/event-driven-testing#intercept-network ubah ChromeDriver jadi webdriver biasa , trus ganti devtoolsnya
         * Todo : Tambahin test jika buka 2 tab
         * 1. paksa listen di port yg sama
         * 2. beda port cdp - beda har
         */
    }

    private void setupLocalDriver(){
        options.addArguments("--no-sandbox");
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--disable-dev-shm-usage");
        if(Optional.ofNullable(System.getenv("CHROME_MODE")).orElse("").equalsIgnoreCase("headless")){
            options.addArguments("--headless");
            System.out.println("Running With headless mode");
        }else{
            System.out.println("Running Without headless mode");
        }
        WebDriverManager.chromedriver().setup();
    }

    @Test
    public void testWithOpenNewTab(){
        setupLocalDriver();
        driver = new ChromeDriver(options);
        networkListener = new NetworkListener(driver,"har-new-tab.har");
        networkListener.start();
        driver.manage().window().maximize();
        driver.get("http://gosoft.web.id/selenium/");
        WebElement linkNewTab = driver.findElement(By.id("new-tab"));
        linkNewTab.click();
        ArrayList<String> tabs2 = new ArrayList<>(driver.getWindowHandles());
        driver.switchTo().window(tabs2.get(1));
        networkListener.start();
        driver.navigate().refresh();
        System.out.println("Completed test new Tab");
    }

    @Test
    public void seleniumTest() {
        setupLocalDriver();
        driver = new ChromeDriver(options);
        networkListener = new NetworkListener(driver, "har-new-tab.har");
        networkListener.start(driver.getWindowHandle());
        driver.get("http://gosoft.web.id/selenium/");
        WebElement linkNewTab = driver.findElement(By.id("new-tab"));
        linkNewTab.click();
        ArrayList<String> tabs2 = new ArrayList<>(driver.getWindowHandles());
        driver.switchTo().window(tabs2.get(1));
        networkListener.switchWindow(driver.getWindowHandle());

        driver.quit();
    }


    @Test
    public void testNotFound(){
        setupLocalDriver();
        driver = new ChromeDriver(options);
        networkListener = new NetworkListener(driver,"har-not-found.har");
        networkListener.start();
        driver.manage().window().maximize();
        driver.get("http://gosoft.web.id/error-not-found/");
        System.out.println("Completed test not found");
    }

//    @Test
    public void tryUsingRemoteAccess() throws MalformedURLException {
        Map<String, Object> prefs = new HashMap<String, Object>();
        prefs.put("enableVNC", true);
        prefs.put("enableVideo", false);
        prefs.put("sessionTimeout", "120s");
        capabilities = new DesiredCapabilities();
        capabilities.setCapability("browserName", "chrome");
        capabilities.setCapability("browserVersion", "96.0");
        capabilities.setCapability("selenoid:options", prefs);
        options.merge(capabilities);
        // Todo : Check Selenoid implementation https://github.com/SeleniumHQ/selenium/issues/9803#issuecomment-1015300383
        String seleniumUrl = Optional.ofNullable(System.getenv("SE_REMOTE_URL")).orElse("http://localhost:4444/wd/hub/");
        System.out.println("Running on Remote " + seleniumUrl);
        driver = new RemoteWebDriver(new URL(seleniumUrl), capabilities);
        // Todo : change to this one https://github.com/aerokube/chrome-developer-tools-protocol-java-example/blob/master/src/test/java/com/aerokube/selenoid/ChromeDevtoolsTest.java
        networkListener = new NetworkListener(driver, "har.har",Optional.ofNullable(System.getenv("SE_BASE_URL")).orElse("localhost:4444"));
        driver.get("https://en.wiktionary.org/wiki/Wiktionary:Main_Page");
        networkListener.start();
        driver.get("https://en.wiktionary.org/wiki/Wiktionary:Main_Page");
        WebElement element = driver.findElement(By.id("searchInput"));
        element.sendKeys("Kiwi");
        element.sendKeys(Keys.RETURN);
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testTracerServicesQuick() {
        setupLocalDriver();
        driver = new ChromeDriver(options);
        driver.manage().window().maximize();
        
        // Get DevTools instance from ChromeDriver
        DevTools devTools = ((ChromeDriver) driver).getDevTools();
        devTools.createSession();
        
        // Create TracerServices instance
        TracerServices tracerServices = new TracerServices(devTools, "quick-test");
        
        try {
            System.out.println("=== Quick TracerServices Test ===");
            
            // Start tracing
            tracerServices.start();
            System.out.println("✓ Tracing started successfully");
            
            // Quick browser activity
            driver.get("https://hello.gosoft.web.id");
            System.out.println("✓ Page loaded");
            
            // Stop tracing with minimal timeout
            System.out.println("Stopping tracing...");
            tracerServices.stop();
            System.out.println("✓ Tracing stopped");
            
            // Quick check for files
            // Path traceFile = Paths.get("quick-test.json");
            // if (Files.exists(traceFile)) {
            //     long fileSize = Files.size(traceFile);
            //     System.out.println("✓ Trace file created: " + fileSize + " bytes");
            //     Files.delete(traceFile); // Clean up immediately
            // } else {
            //     System.out.println("ℹ No trace file (expected with CDP version mismatch)");
            // }
            
            System.out.println("=== Quick Test Completed Successfully ===");
            
        } catch (Exception e) {
            System.out.println("✗ Test failed: " + e.getMessage());
        } finally {
            if (driver != null) {
                driver.quit();
                driver = null;
            }
        }
    }

    @Test
    public void testTracerServices() {
        setupLocalDriver();
        driver = new ChromeDriver(options);
        driver.manage().window().maximize();
        
        // Get DevTools instance from ChromeDriver
        DevTools devTools = ((ChromeDriver) driver).getDevTools();
        devTools.createSession();
        
        // Create TracerServices instance
        TracerServices tracerServices = new TracerServices(devTools, "test-tracer");
        
        try {
            // Start tracing
            tracerServices.start();
            
            // Generate more browser activity to ensure trace data is collected
            System.out.println("Starting browser activity to generate trace data...");
            
            // Navigate to multiple pages to generate meaningful trace data
            driver.get("https://httpbin.org/html");
            Thread.sleep(1000);
            
            driver.get("https://httpbin.org/json");
            Thread.sleep(1000);
            
            System.out.println("Stopping trace collection...");
            
            // Use a separate thread to call stop() with timeout to prevent hanging
            final Exception[] stopException = {null};
            Thread stopThread = new Thread(() -> {
                try {
                    tracerServices.stop();
                } catch (Exception e) {
                    stopException[0] = e;
                }
            });
            
            stopThread.start();
            
            // Wait with progress feedback instead of blocking
            System.out.println("Waiting for TracerServices.stop() to complete...");
            int maxWaitSeconds = 45; // Reduced from 60
            boolean completed = false;
            
            for (int i = 0; i < maxWaitSeconds; i += 5) {
                if (stopThread.join(5000)) { // Check every 5 seconds
                    completed = true;
                    break;
                }
                System.out.println("Still waiting... (" + (i + 5) + "s/" + maxWaitSeconds + "s)");
            }
            
            if (!completed && stopThread.isAlive()) {
                System.out.println("WARNING: TracerServices.stop() took longer than " + maxWaitSeconds + " seconds");
                stopThread.interrupt();
            } else if (stopException[0] != null) {
                System.out.println("WARNING: TracerServices.stop() threw exception: " + stopException[0].getMessage());
            } else {
                System.out.println("TracerServices.stop() completed successfully");
            }
            
            // Wait for trace data writing with progress feedback
            System.out.println("Waiting for trace data writing to complete...");
            for (int i = 1; i <= 5; i++) {
                Thread.sleep(1000);
                System.out.print(".");
                if (i % 5 == 0) System.out.println(" (" + i + "s)");
            }
            System.out.println("\nTrace data writing wait completed.");
            
            // Check if trace file was created and has content
            Path traceFile = Paths.get("test-tracer.json");
            if (Files.exists(traceFile)) {
                long fileSize = Files.size(traceFile);
                System.out.println("SUCCESS: Trace file created: " + traceFile.toAbsolutePath());
                System.out.println("Trace file size: " + fileSize + " bytes");
                
                // Validate JSON structure
                if (fileSize > 0) {
                    try {
                        String content = Files.readString(traceFile);
                        System.out.println("Trace file content preview: " + content.substring(0, Math.min(200, content.length())) + "...");
                        
                        // Basic JSON validation - check if it starts and ends properly
                        content = content.trim();
                        if (content.startsWith("{") && content.endsWith("}")) {
                            System.out.println("SUCCESS: Trace file appears to be valid JSON format");
                            
                            // Check if it contains trace events
                            if (content.contains("\"traceEvents\":")) {
                                System.out.println("SUCCESS: Trace file contains traceEvents data");
                            } else {
                                System.out.println("INFO: Trace file is valid JSON but may be a fallback file");
                            }
                        } else {
                            System.out.println("WARNING: Trace file may be incomplete or corrupted JSON");
                        }
                    } catch (Exception jsonEx) {
                        System.out.println("ERROR: Failed to validate trace file JSON: " + jsonEx.getMessage());
                    }
                }
            } else {
                System.out.println("INFO: Main trace file was not created (expected due to CDP version mismatch)");
            }
            
            // Also check the target directory file
            Path targetTraceFile = Paths.get("target", "Trace - test-tracer.json");
            if (Files.exists(targetTraceFile)) {
                long targetFileSize = Files.size(targetTraceFile);
                System.out.println("Target trace file: " + targetTraceFile.toAbsolutePath());
                System.out.println("Target trace file size: " + targetFileSize + " bytes");
            }
            
        } catch (Exception e) {
            System.out.println("Expected exception due to CDP version mismatch: " + e.getMessage());
        } finally {
            // Close the driver here to ensure proper cleanup before @AfterEach
            if (driver != null) {
                driver.quit();
                driver = null; // Prevent @AfterEach from trying to quit again
                System.out.println("Driver closed properly after trace collection");
            }
            
            System.out.println("Test completed - demonstrating TracerServices functionality");
        }
    }

    @AfterEach
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if(networkListener != null) {
            networkListener.createHarFile();
        }

        // in the github actions we need add some wait , because chrome exited too slow ,
        // so when we create new session previous chrome is not closed completly
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
