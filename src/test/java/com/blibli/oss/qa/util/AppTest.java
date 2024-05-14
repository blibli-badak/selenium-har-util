package com.blibli.oss.qa.util;


import com.blibli.oss.qa.util.services.NetworkListener;
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
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


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
        WebDriverWait webDriverWait = new WebDriverWait(driver, Duration.ofSeconds(30));
        WebElement element =driver.findElement(By.id("searchInput"));
        webDriverWait.until(webDriver -> element.isDisplayed());
        element.sendKeys("Kiwi/n");
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

    @AfterEach
    public void tearDown() {
        driver.quit();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        networkListener.createHarFile();
        // in the github actions we need add some wait , because chrome exited too slow ,
        // so when we create new session previous chrome is not closed completly
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
