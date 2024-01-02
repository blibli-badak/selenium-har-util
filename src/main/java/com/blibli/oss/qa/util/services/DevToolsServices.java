package com.blibli.oss.qa.util.services;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.remote.Augmenter;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.lang.reflect.Field;

@Slf4j
public class DevToolsServices {
    private String baseRemoteUrl;
    private WebDriver driver;

    private DevTools devTools;
    public DevToolsServices(WebDriver driver) {
        this.driver = driver;
    }
    public DevToolsServices(WebDriver driver , String baseRemoteUrl) {
        this.baseRemoteUrl = baseRemoteUrl;
        this.driver = driver;
    }

    public DevTools getDevTools(){
        try {
            if (driver instanceof RemoteWebDriver) {
                this.devTools = getCdpUsingCustomurl();
            } else {
                this.devTools = ((HasDevTools) driver).getDevTools();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return this.devTools;
    }

    private DevTools getCdpUsingCustomurl() {
        // Before proceeding, reach into the driver and manipulate the capabilities to
        // include the se:cdp and se:cdpVersion keys.
        try {
            Field capabilitiesField = RemoteWebDriver.class.getDeclaredField("capabilities");
            capabilitiesField.setAccessible(true);
            String sessionId = ((RemoteWebDriver) driver).getSessionId().toString();
            String devtoolsUrl = String.format("ws://%s/devtools/%s/page", baseRemoteUrl, sessionId);

            MutableCapabilities mutableCapabilities = (MutableCapabilities) capabilitiesField.get(driver);
            mutableCapabilities.setCapability("se:cdp", devtoolsUrl);
            mutableCapabilities.setCapability("se:cdpVersion", mutableCapabilities.getBrowserVersion());
        } catch (Exception e) {
            log.info("Failed to spoof RemoteWebDriver capabilities :sadpanda:");
        }

        // Proceed to "augment" the driver and get a dev tools client ...
        RemoteWebDriver augmenteDriver = (RemoteWebDriver) new Augmenter().augment(driver);
        DevTools devTools = ((HasDevTools) augmenteDriver).getDevTools();
        this.driver = augmenteDriver;
        return devTools;
    }


}
