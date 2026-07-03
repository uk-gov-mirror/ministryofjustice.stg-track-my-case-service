package uk.gov.moj.cp.service.notifications;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "notifications")
public class NotificationsProperties {

    private String govNotifyApiKey;

    private String userLoggedInEmailTemplateId;

    private String textMessageTemplateId;

    private String serviceUrl;

    private String targetEmail;

    private String targetMobile;

}
