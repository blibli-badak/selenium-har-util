package com.blibli.oss.qa.util;

import com.blibli.oss.qa.util.services.NetworkListener;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class UnicodeTest extends BaseTest {
    private NetworkListener networkListener;
    private static String HAR_UNICODE_NAME = "har-unicode.har";
    private ChromeDriver driver;
    private ChromeOptions options;
    private DesiredCapabilities capabilities;

    @BeforeEach
    public void setup() {
        this.setupLocalDriver();
    }
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


    @Test
    public void testUnicode() throws InterruptedException, IOException {
        driver = new ChromeDriver(options);
        driver.manage().window().maximize();
        networkListener = new NetworkListener(driver, driver.getDevTools(), HAR_UNICODE_NAME);
        networkListener.setCharset("UTF-8");
        networkListener.start();
        driver.get("https://gosoft.web.id/selenium/hotlist.php");
        Thread.sleep(5000); // make sure the web are loaded
        networkListener.createHarFile();
        if (driver!=null){
            driver.close();
        }
        Thread.sleep(1000); // make sure the file are written
        String harFile = this.readHarData(HAR_UNICODE_NAME);
        System.out.println(harFile);
        assertTrue(harFile.contains("接口路径不存在 请前往"));
    }


}
