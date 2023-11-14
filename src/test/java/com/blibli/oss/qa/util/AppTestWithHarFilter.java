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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


/**
 * Unit test for simple App.
 */
public class AppTestWithHarFilter {
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
        networkListener = new NetworkListener(driver, "harFilter.har");
        networkListener.start();
        driver.get("https://en.wiktionary.org/wiki/Wiktionary:Main_Page");
        WebElement element = driver.findElement(By.id("searchInput"));
        element.sendKeys("Kiwi/n");
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

    @AfterEach
    public void tearDownWithFilter() {
        driver.quit();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        //filter parameter for createHarFile() to only print har request that contains the string into the har file
        networkListener.createHarFile("en.wiktionary.org");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
