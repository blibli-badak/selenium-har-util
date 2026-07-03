package com.blibli.oss.qa.util;

import io.github.bonigarcia.wdm.WebDriverManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class BaseTest {

    protected static void setupChromeDriverBinary() {
        WebDriverManager.chromedriver().setup();
    }

    public String readHarData(String fileName) throws IOException {
        String harFile = Paths.get(".").toAbsolutePath().normalize().toString() + File.separator + fileName;
        System.out.println("Read Har Data " + harFile);
        return new String(Files.readAllBytes(Paths.get(fileName)));
    }

}

