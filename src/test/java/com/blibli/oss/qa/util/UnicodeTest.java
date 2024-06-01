package com.blibli.oss.qa.util;

import com.blibli.oss.qa.util.services.NetworkListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.chrome.ChromeDriver;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class UnicodeTest extends BaseTest {

    private static String HAR_UNICODE_NAME = "har-unicode.har";

    @BeforeEach
    public void setup() {
        this.setupLocalDriver();
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
