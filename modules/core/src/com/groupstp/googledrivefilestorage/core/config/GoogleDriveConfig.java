package com.groupstp.bills.core.config;

import com.haulmont.cuba.core.config.Config;
import com.haulmont.cuba.core.config.Property;
import com.haulmont.cuba.core.config.Source;
import com.haulmont.cuba.core.config.SourceType;
import com.haulmont.cuba.core.config.defaults.Default;

/**
 * Settings for Google Drive file storage integration
 *
 * @author adiatullin
 */
@Source(type = SourceType.DATABASE)
public interface GoogleDriveConfig extends Config {

    /**
     * @return Google application name
     */
    @Property("google.drive.applicationName")
    @Default("Bills")
    String getApplicationName();

    /**
     * @return Google drive oauth client json (including client secret and id)
     */
    @Property("google.drive.clientOAuthSecretJson")
    @Default("{\"web\":{\"client_id\":\"37177294939-3tio8e050uj2k09god0u4otcipqm9364.apps.googleusercontent.com\",\"project_id\":\"bills-1533798879418\",\"auth_uri\":\"https://accounts.google.com/o/oauth2/auth\",\"token_uri\":\"https://www.googleapis.com/oauth2/v3/token\",\"auth_provider_x509_cert_url\":\"https://www.googleapis.com/oauth2/v1/certs\",\"client_secret\":\"x-81czMPw9XiIu9xKM5DmyGb\",\"redirect_uris\":[\"https://developers.google.com/oauthplayground\"]}}")
    String getClientOAuthSecretJson();

    /**
     * @return refresh authorization token
     */
    @Property("google.drive.refreshToken")
    @Default("1/ammnVsdPKgFrpo0Z9DPzGSdx5uP8lxFQ7dmhdf8eebs")
    String getRefreshToken();

    /**
     * @return path of file storage root folder
     */
    @Property("google.drive.fileStorageFolder")
    @Default("FileStorage")
    String getFileStorageFolder();

    /**
     * @return direct open to view file url
     */
    @Property("google.drive.viewUrlFormat")
    @Default("https://drive.google.com/file/d/%s/view")
    String getViewUrlFormat();
}
