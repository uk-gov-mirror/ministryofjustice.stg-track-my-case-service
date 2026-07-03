package uk.gov.moj.cp.service.notifications;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import uk.gov.service.notify.Notification;
import uk.gov.service.notify.ReceivedTextMessageList;
import uk.gov.service.notify.SendSmsResponse;
import uk.gov.service.notify.TemplateList;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final EmailService emailService;
    private final NotificationsProperties notificationProperties;

    private static final String USER_EMAIL = "email";
    private static final String CASE_URN = "caseurn";
    private static final String SERVICE_URL = "serviceUrl";

    @SneakyThrows
    public void sendUserSearchedForTheCaseNotification(final String userEmail, final String caseUrn) {
        if (StringUtils.isNoneEmpty(userEmail) && StringUtils.isNoneEmpty(caseUrn)) {
            final String sentToEmail = notificationProperties.getTargetEmail();
            final String serviceUrl = notificationProperties.getServiceUrl();
            emailService.sendMail(
                sentToEmail,
                notificationProperties.getUserLoggedInEmailTemplateId(),
                addProperties(userEmail, caseUrn, serviceUrl),
                null
            );
            log.info("A user {} searched for the case {}", userEmail, caseUrn);
        }
    }

    @SneakyThrows
    public void sendTextMessageForTheCaseNotification(final String userMobile, final String caseUrn) {
        final String targetMobile = notificationProperties.getTargetMobile();
        final String serviceUrl = notificationProperties.getServiceUrl();
        SendSmsResponse sendSmsResponse = null;

        if (StringUtils.isNoneEmpty(userMobile) && StringUtils.isNoneEmpty(caseUrn)) {
            try {
                sendSmsResponse = emailService.sendTextMessage(
                    targetMobile,
                    notificationProperties.getTextMessageTemplateId(),
                    addProperties(userMobile, caseUrn, serviceUrl),
                    null
                );
                log.info("A user {} searched for the case {}", userMobile, caseUrn);
            } catch (Exception e) {
                log.error("A user {} searched for the case {}", userMobile, caseUrn);
            }
        }

        try {
            TemplateList allSmsTemplates = emailService.getAllTemplates("sms");
            log.info("A user {} searched for the case {}", userMobile, caseUrn);
        } catch (Exception e) {
            log.error("A user {} searched for the case {}", userMobile, caseUrn);
        }
        try {
            TemplateList allEmailTemplates = emailService.getAllTemplates("email");
            log.info("A user {} searched for the case {}", userMobile, caseUrn);
        } catch (Exception e) {
            log.error("A user {} searched for the case {}", userMobile, caseUrn);
        }
        try {
            ReceivedTextMessageList receivedTextMessages = emailService.getReceivedTextMessages(sendSmsResponse.getNotificationId().toString());
            log.info("A user {} searched for the case {}", userMobile, caseUrn);
        } catch (Exception e) {
            log.error("A user {} searched for the case {}", userMobile, caseUrn);
        }
        try {
            Notification notification = emailService.getNotificationById(sendSmsResponse.getNotificationId().toString());
            log.info("A user {} searched for the case {}", userMobile, caseUrn);
        } catch (Exception e) {
            log.error("A user {} searched for the case {}", userMobile, caseUrn);
        }
    }

    public Map<String, String> addProperties(String userEmail, String caseUrn, String serviceUrl) {
        Map<String, String> customProps = new HashMap<>();
        customProps.put(USER_EMAIL, userEmail);
        customProps.put(CASE_URN, caseUrn);
        customProps.put(SERVICE_URL, serviceUrl);
        return customProps;
    }
}
