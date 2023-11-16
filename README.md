## HAR Util For Selenium 4
[![](https://jitpack.io/v/blibli-badak/selenium-har-util.svg)](https://jitpack.io/#blibli-badak/selenium-har-util)

----
### Background : 
Currently, Selenium 4 is the most popular webdriver for automation. and now they support to communicate with the CDP browser. that able to communicate with CDP.
So we can use CDP to communicate with the browser. one of that feature is intercept network
with this feature we can get the network request and response and create HAR files for the network request.

This util is supposed to be get network request and write a HAR file.

### Requirement:
- Selenium 4 that support CDP 
- ChromeDriver
- Google Chrome or any browser that support CDPSession ( not yet tested in Geckodriver or safari driver)
- Java 8
- Maven

### Instalation

Add Repository for this dependency
```xml
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>
```

And then add the maven dependency same with the lattest version in jitpack [![](https://jitpack.io/v/blibli-badak/selenium-har-util.svg)](https://jitpack.io/#blibli-badak/selenium-har-util)

```xml
        <dependency>
            <groupId>com.github.blibli-badak</groupId>
            <artifactId>selenium-har-util</artifactId>
            <version>1.0.9</version>
        </dependency>
```

if you are use another tools , you can check this link for the instruction https://jitpack.io/#blibli-badak/selenium-har-util 


Create your Driver
```java
driver = new ChromeDriver(options);
```
Integrate with our network listener
```java
NetworkListener networkListener = new NetworkListener(driver, "har.har");
```
Start Capture your network request
```java
networkListener.start();
```
And Run your automation.
After finishing your automation , you can create HAR files by using this method

```java
driver.quit();
networkListener.createHarFile();
```

(Starting from version 1.0.12)
If you want to filter the HAR to only contain certain requests, you can add a string parameter in the createHarFile() function. Example:

```java
driver.quit();
networkListener.createHarFile("en.wiktionary.org");
```

And voila , in your project will be have new file called har.har , and you can inspect it via your favourite HAR viewer or you can open it via inspect element -> Network tab in your browser

### HAR validator
Using Chrome:
- Open Inspect Tab
- Network tab
- Click on import HAR file

Using free web analyzer
- https://toolbox.googleapps.com/apps/har_analyzer/
- http://www.softwareishard.com/har/viewer/

