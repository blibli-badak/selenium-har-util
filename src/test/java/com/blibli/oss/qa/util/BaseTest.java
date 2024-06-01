package com.blibli.oss.qa.util;

import com.blibli.oss.qa.util.services.NetworkListener;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;

public class BaseTest {

    public ChromeDriver driver;
    public NetworkListener networkListener;
    public ChromeOptions options;
    public DesiredCapabilities capabilities;

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

    public String readHarData(String fileName) throws IOException {
        String harFile = Paths.get(".").toAbsolutePath().normalize().toString() + ""+File.separator +""+fileName;
        System.out.println("Read Har Data " + harFile);
        return new String(Files.readAllBytes(Paths.get(fileName)));
    }

}

