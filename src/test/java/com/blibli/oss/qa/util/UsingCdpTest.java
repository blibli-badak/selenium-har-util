package com.blibli.oss.qa.util;

import com.blibli.oss.qa.util.services.NetworkListener;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.Optional;

public class UsingCdpTest  extends BaseTest{


    @BeforeEach
    public void setup() {
    }

    @Test
    public void testWithLocalDriver() {
        setupLocalDriver();
        driver = new ChromeDriver(options);
        driver.manage().window().maximize();
        networkListener = new NetworkListener(driver , driver.getDevTools(), "har-with-cdp.har");
        networkListener.start();
        driver.get("https://en.wiktionary.org/wiki/Wiktionary:Main_Page");
        WebDriverWait webDriverWait = new WebDriverWait(driver, Duration.ofSeconds(30));
        WebElement element =driver.findElement(By.id("searchInput"));
        webDriverWait.until(webDriver -> element.isDisplayed());
        element.sendKeys("Kiwi/n");
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
