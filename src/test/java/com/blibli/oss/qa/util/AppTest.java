package com.blibli.oss.qa.util;


import com.blibli.oss.qa.util.services.NetworkListener;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;


/**
 * Unit test for simple App.
 */
public class AppTest {
    /**
     * Rigorous Test :-)
     */

    private WebDriver driver;
    private NetworkListener networkListener;

    @BeforeAll
    static void setupClass() {

    }

    @BeforeEach
    public void setup() {

    }

    @Test
    public void shouldAnswerWithTrue() {
//        NetworkInterceptor networkInterceptor = new NetworkInterceptor(driver, new HttpHandler() {
//            @Override
//            public HttpResponse execute(HttpRequest req) throws UncheckedIOException {
//                System.out.println(req.getMethod().name());
//                HttpResponse hr = new HttpResponse();
//                hr.setStatus(200);
//                return hr;
//            }
//        });
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--headless");
        driver = new ChromeDriver(options);
        driver.manage().window().maximize();
        networkListener = new NetworkListener(driver, "har.har");

        networkListener.start();
//        driver.get("http://gosoft.web.id/example/");
        driver.get("https://en.wiktionary.org/wiki/Wiktionary:Main_Page");
        WebElement element = driver.findElement(By.id("searchInput"));
        element.sendKeys("Kiwi/n");


        /**
         * Todo : https://www.browserstack.com/docs/automate/selenium/event-driven-testing#intercept-network ubah ChromeDriver jadi webdriver biasa , trus ganti devtoolsnya
         * Todo : Tambahin test jika buka 2 tab
         * 1. paksa listen di port yg sama
         * 2. beda port cdp - beda har
         */
    }

    @Test
    public void tryUsingRemoteAccess() {
        networkListener.start();
        driver.get("https://en.wiktionary.org/wiki/Wiktionary:Main_Page");
        WebElement element = driver.findElement(By.id("searchInput"));
        element.sendKeys("Kiwi/n");
    }

    @AfterEach
    public void tearDown() {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        driver.quit();
        networkListener.createHarFile();
    }
}
