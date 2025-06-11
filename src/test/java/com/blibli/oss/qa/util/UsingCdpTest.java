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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.Optional;

public class UsingCdpTest  extends BaseTest{

    private NetworkListener networkListener;
    private ChromeDriver driver;
    private ChromeOptions options;
    private DesiredCapabilities capabilities;

    public void setupLocalDriver(){
        options = new ChromeOptions();
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

    public void testLoginFeature() {
        setupLocalDriver();
        driver = new ChromeDriver(options);
        driver.manage().window().maximize();
        networkListener = new NetworkListener(driver , driver.getDevTools(), "har-with-cdp.har");
        networkListener.start();
        driver.get("https://stackoverflow.com/users/login");
        WebDriverWait webDriverWait = new WebDriverWait(driver, Duration.ofSeconds(10));

        webDriverWait.until(ExpectedConditions.visibilityOfElementLocated(By.id("email"))).sendKeys("Kiwi");
        webDriverWait.until(ExpectedConditions.visibilityOfElementLocated(By.id("password"))).sendKeys("Kiwi");

        webDriverWait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//button[@id='submit-button']"))).click();

        webDriverWait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//p[@class='flex--item s-input-message js-error-message ']")));
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
