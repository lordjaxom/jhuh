/*
 * Copyright 2000-2024 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.pro.licensechecker;

import com.vaadin.open.Open;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Period;
import java.util.function.Consumer;

public class LicenseChecker {

    static final int DEFAULT_KEY_URL_HANDLER_TIMEOUT_SECONDS = 60;

    private static boolean strictOffline = false;

    static String loggedLicenseOwner = null;

    public interface Callback {

        public void ok();

        public void failed(Exception e);
    }

    static Consumer<String> systemBrowserUrlHandler = url -> {
        try {
            getLogger().info(
                    "Opening system browser to validate license. If the browser is not opened, please open "
                            + url + " manually");
            Open.open(url);
            getLogger().info(
                    "For CI/CD build servers, you need to download a server license key, which can only be "
                            + "used for production builds. You can download a server license key from "
                            + "https://vaadin.com/myaccount/licenses.\n"
                            + "If you are working offline in development mode, please visit "
                            + OfflineKeyValidator.getOfflineUrl(MachineId.get())
                            + " for an offline development mode license.");
        } catch (Exception e) {
            getLogger().error(
                    "Error opening system browser to validate license. Please open "
                            + url + " manually",
                    e);
        }

    };

    /**
     * @deprecated use
     *             {@link #checkLicenseFromStaticBlock(String, String, BuildType)}
     */
    @Deprecated
    public static void checkLicenseFromStaticBlock(String productName,
                                                   String productVersion) {
        checkLicenseFromStaticBlock(productName, productVersion,
                BuildType.DEVELOPMENT);
    }

    /**
     * Checks the license for the given product version from a {@code static}
     * block.
     *
     * @param productName
     *            the name of the product to check
     * @param productVersion
     *            the version of the product to check
     * @param buildType
     *            the type of build: production or development
     *
     * @throws ExceptionInInitializerError
     *             if the license check fails
     */
    public static void checkLicenseFromStaticBlock(String productName,
                                                   String productVersion, BuildType buildType) {
        try {
            checkLicense(productName, productVersion, buildType);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * @deprecated use {@link #checkLicense(String, String, BuildType)}
     */
    @Deprecated
    public static void checkLicense(String productName, String productVersion) {
        checkLicense(productName, productVersion, BuildType.DEVELOPMENT,
                systemBrowserUrlHandler);
    }

    /**
     * Checks the license for the given product version.
     *
     * @param productName
     *            the name of the product to check
     * @param productVersion
     *            the version of the product to check
     * @param buildType
     *            the type of build: development or production
     *
     * @throws LicenseException
     *             if the license check fails
     */
    public static void checkLicense(String productName, String productVersion,
                                    BuildType buildType) {
        checkLicense(productName, productVersion, buildType,
                systemBrowserUrlHandler);
    }

    /**
     * Checks the license for the given pro key and product version.
     *
     * @param productName
     *            the name of the product to check
     * @param productVersion
     *            the version of the product to check
     * @param buildType
     *            the type of build: development or production
     * @param noKeyUrlHandler
     *            a handler that is invoked to open the vaadin.com URL to
     *            download the key file. Used when no key file is avialable.
     * @param machineId
     *            the identifier of machine which owns pro key
     * @param proKey
     *            the pro key to be validated
     * @param offlineKey
     *            the offline key to be validated
     *
     * @throws LicenseException
     *             if the license check fails
     */
    public static void checkLicense(String productName, String productVersion,
                                    BuildType buildType, Consumer<String> noKeyUrlHandler,
                                    String machineId, ProKey proKey, SubscriptionKey subscriptionKey,
                                    OfflineKey offlineKey) {
        checkLicense(new Product(productName, productVersion), buildType,
                noKeyUrlHandler, machineId, proKey, subscriptionKey, offlineKey,
                new OnlineKeyValidator(), new OfflineKeyValidator(),
                new VaadinComIntegration(),
                DEFAULT_KEY_URL_HANDLER_TIMEOUT_SECONDS);
    }

    /**
     * @deprecated use
     *             {@link #checkLicense(String, String, BuildType, Consumer)}
     */
    public static void checkLicense(String productName, String productVersion,
                                    Consumer<String> noKeyUrlHandler) {
        checkLicense(productName, productVersion, BuildType.DEVELOPMENT,
                noKeyUrlHandler);
    }

    /**
     * Checks the license for the given product version.
     *
     * @param productName
     *            the name of the product to check
     * @param productVersion
     *            the version of the product to check
     * @param buildType
     *            the type of build: development or production
     * @param noKeyUrlHandler
     *            a handler that is invoked to open the vaadin.com URL to
     *            download the key file. Used when no key file is avialable.
     *
     * @throws LicenseException
     *             if the license check fails
     */
    public static void checkLicense(String productName, String productVersion,
                                    BuildType buildType, Consumer<String> noKeyUrlHandler) {
        checkLicense(productName, productVersion, buildType, noKeyUrlHandler,
                DEFAULT_KEY_URL_HANDLER_TIMEOUT_SECONDS);
    }

    /**
     * Checks the license for the given product version.
     *
     * @param productName
     *            the name of the product to check
     * @param productVersion
     *            the version of the product to check
     * @param buildType
     *            the type of build: development or production
     * @param noKeyUrlHandler
     *            a handler that is invoked to open the vaadin.com URL to
     *            download the key file. Used when no key file is avialable.
     * @param timeoutKeyUrlHandler
     *            timeout for the key url handler
     * @throws LicenseException
     *             if the license check fails
     */
    public static void checkLicense(String productName, String productVersion,
                                    BuildType buildType, Consumer<String> noKeyUrlHandler,
                                    int timeoutKeyUrlHandler) {
        checkLicense(new Product(productName, productVersion), buildType,
                noKeyUrlHandler, timeoutKeyUrlHandler);
    }

    /**
     * Checks the license for the given product version. Returns {@code true} if
     * the license is valid, {@code false} otherwise.
     *
     * @param productName
     *            the name of the product to check
     * @param productVersion
     *            the version of the product to check
     * @param buildType
     *           the type of build: development or production
     * @return {@code true} if the license is valid, {@code false} otherwise
     */
    public static boolean isValidLicense(String productName, String productVersion,
                                         BuildType buildType) {
        try {
            checkLicense(productName, productVersion, buildType, null);
            return true;
        } catch (LicenseException e) {
            return false;
        }
    }

    /**
     * @deprecated use
     *             {@link #checkLicenseAsync(String, String, BuildType, Callback)}
     */
    @Deprecated
    public static void checkLicenseAsync(String productName,
                                         String productVersion, Callback callback) {
        checkLicenseAsync(productName, productVersion, BuildType.DEVELOPMENT,
                callback);
    }

    /**
     * Checks the license for the given product version in the background and
     * invokes the callback when done.
     *
     * @param productName
     *            the name of the product to check
     * @param productVersion
     *            the version of the product to check
     * @param buildType
     *            the type of build: development or production
     * @param callback
     *            the callback to invoke with the result
     */
    public static void checkLicenseAsync(String productName,
                                         String productVersion, BuildType buildType, Callback callback) {
        checkLicenseAsync(productName, productVersion, buildType, callback,
                systemBrowserUrlHandler);
    }

    /**
     * Checks the license for the given pro key and product version in the
     * background and invokes the callback when done.
     *
     * @param productName
     *            the name of the product to check
     * @param productVersion
     *            the version of the product to check
     * @param buildType
     *            the type of build: development or production
     * @param callback
     *            the callback to invoke with the result
     * @param noKeyUrlHandler
     *            a handler that is invoked to open the vaadin.com URL to
     *            download the key file. Used when no key file is avialable.
     * @param machineId
     *            the identifier of machine which owns pro key
     * @param proKey
     *            the pro key to be validated
     * @param offlineKey
     *            the offline key to be validated
     *
     * @throws LicenseException
     *             if the license check fails
     */
    public static void checkLicenseAsync(String productName,
                                         String productVersion, BuildType buildType, Callback callback,
                                         Consumer<String> noKeyUrlHandler, String machineId, ProKey proKey,
                                         SubscriptionKey subscriptionKey, OfflineKey offlineKey) {
        checkLicenseAsync(new Product(productName, productVersion), buildType,
                callback, noKeyUrlHandler, machineId, proKey, subscriptionKey,
                offlineKey, new OnlineKeyValidator(), new OfflineKeyValidator(),
                new VaadinComIntegration(),
                DEFAULT_KEY_URL_HANDLER_TIMEOUT_SECONDS);
    }

    /**
     * @deprecated use
     *             {@link #checkLicenseAsync(String, String, BuildType, Callback, Consumer)}
     */
    @Deprecated
    public static void checkLicenseAsync(String productName,
                                         String productVersion, Callback callback,
                                         Consumer<String> noKeyUrlHandler) {
    }

    /**
     * Checks the license for the given product version in the background and
     * invokes the callback when done.
     *
     * @param productName
     *            the name of the product to check
     * @param productVersion
     *            the version of the product to check
     * @param buildType
     *            the type of build: development or production
     * @param callback
     *            the callback to invoke with the result
     * @param noKeyUrlHandler
     *            a handler that is invoked to open the vaadin.com URL to
     *            download the key file. Used when no key file is avialable.
     */
    public static void checkLicenseAsync(String productName,
                                         String productVersion, BuildType buildType, Callback callback,
                                         Consumer<String> noKeyUrlHandler) {
        checkLicenseAsync(productName, productVersion, buildType, callback,
                noKeyUrlHandler, DEFAULT_KEY_URL_HANDLER_TIMEOUT_SECONDS);
    }

    /**
     * Checks the license for the given product version in the background and
     * invokes the callback when done.
     *
     * @param productName
     *            the name of the product to check
     * @param productVersion
     *            the version of the product to check
     * @param buildType
     *            the type of build: development or production
     * @param callback
     *            the callback to invoke with the result
     * @param noKeyUrlHandler
     *            a handler that is invoked to open the vaadin.com URL to
     *            download the key file. Used when no key file is avialable.
     * @param timeoutKeyUrlHandler
     *            timeout for the key url handler
     */
    public static void checkLicenseAsync(String productName,
                                         String productVersion, BuildType buildType, Callback callback,
                                         Consumer<String> noKeyUrlHandler, int timeoutKeyUrlHandler) {
        checkLicenseAsync(new Product(productName, productVersion), buildType,
                callback, noKeyUrlHandler, MachineId.get(), LocalProKey.get(),
                LocalSubscriptionKey.get(), LocalOfflineKey.get(),
                new OnlineKeyValidator(), new OfflineKeyValidator(),
                new VaadinComIntegration(), timeoutKeyUrlHandler);
    }

    // For testing
    static void checkLicenseAsync(Product product, BuildType buildType,
                                  Callback callback, Consumer<String> noKeyUrlHandler,
                                  String machineId, ProKey proKey, SubscriptionKey subscriptionKey,
                                  OfflineKey offlineKey, OnlineKeyValidator proKeyValidator,
                                  OfflineKeyValidator offlineKeyValidator,
                                  VaadinComIntegration vaadinComIntegration,
                                  int timeoutKeyUrlHandler) {
        new Thread(() -> {
            try {
                checkLicense(product, buildType, noKeyUrlHandler, machineId,
                        proKey, subscriptionKey, offlineKey, proKeyValidator,
                        offlineKeyValidator, vaadinComIntegration,
                        timeoutKeyUrlHandler);
                callback.ok();
            } catch (Exception e) {
                callback.failed(e);
            }
        }).start();
    }

    /**
     * The internal method all license check methods end up calling.
     * <p>
     * Checking works like:
     * <ol>
     * <li>If there is a local pro key, check with the license server that the
     * proKey has access to the given product
     * <li>If there is no local pro key but there is an offline key, check that
     * this offline key is valid and allows access to the given product
     * <li>If there was neither a local proKey nor an offline key, open the
     * vaadin.com URL for fetching a pro key for the user. Wait for the user to
     * log in, store the pro key and then validate it for the select product
     * like in step 1.
     * </ol>
     *
     * @param product
     *            the name and version of the product to check
     * @param buildType
     *            the type of build: development or production
     * @param noKeyUrlHandler
     *            a handler that is invoked to open the vaadin.com URL to
     *            download the key file. Used when no key file is avialable.
     * @param timeoutKeyUrlHandler
     *            timeout for the key url handler
     */
    static void checkLicense(Product product, BuildType buildType,
                             Consumer<String> noKeyUrlHandler, int timeoutKeyUrlHandler) {
        getLogger().debug("Checking license for " + product);
        checkLicense(product, buildType, noKeyUrlHandler, MachineId.get(),
                LocalProKey.get(), LocalSubscriptionKey.get(),
                LocalOfflineKey.get(), new OnlineKeyValidator(),
                new OfflineKeyValidator(), new VaadinComIntegration(),
                timeoutKeyUrlHandler);
    }

    // Version for testing only
    static void checkLicense(Product product, BuildType buildType,
                             Consumer<String> noKeyUrlHandler, String machineId, ProKey proKey,
                             SubscriptionKey subscriptionKey, OfflineKey offlineKey,
                             OnlineKeyValidator proKeyValidator,
                             OfflineKeyValidator offlineKeyValidator,
                             VaadinComIntegration vaadinComIntegration,
                             int timeoutKeyUrlHandler) {
    }

    private static boolean canWorkOffline(Product product, BuildType buildType,
                                          String machineId, ProKey proKey) {
        if (History.isRecentlyValidated(product, Period.ofDays(2), buildType,
                proKey)) {
            // Offline but has validated during the last 2 days. Allow
            return true;
        } else if (strictOffline) {
            // Need an offline license
            throw new LicenseException(
                    OfflineKeyValidator.getMissingOfflineKeyMessage(machineId));
        } else {
            // Allow usage offline
            getLogger().warn(
                    "Unable to validate the license, please check your internet connection");
            return true;
        }
    }

    static Logger getLogger() {
        return LoggerFactory.getLogger(LicenseChecker.class);
    }

    public static void setStrictOffline(boolean strictOffline) {
        LicenseChecker.strictOffline = strictOffline;
    }

}
