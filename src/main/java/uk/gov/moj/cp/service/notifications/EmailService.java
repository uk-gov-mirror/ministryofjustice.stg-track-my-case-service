package uk.gov.moj.cp.service.notifications;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.service.notify.Notification;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.ReceivedTextMessageList;
import uk.gov.service.notify.SendEmailResponse;
import uk.gov.service.notify.SendSmsResponse;
import uk.gov.service.notify.TemplateList;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final NotificationClient notificationClient;

    public SendEmailResponse sendMail(
        String targetEmail,
        String emailTemplate,
        Map<String, String> parameters,
        String reference
    ) throws NotificationClientException {
        return notificationClient.sendEmail(emailTemplate, targetEmail, parameters, reference);
    }

    public SendSmsResponse sendTextMessage(
        String targetMobile,
        String smsTemplate,
        Map<String, String> parameters,
        String reference
    ) throws NotificationClientException {
        return notificationClient.sendSms(smsTemplate, targetMobile, parameters, reference);
    }

    public TemplateList getAllTemplates(String templateType) throws NotificationClientException {
        return notificationClient.getAllTemplates(templateType);
    }

    public ReceivedTextMessageList getReceivedTextMessages(String olderThanId) throws NotificationClientException {
        return notificationClient.getReceivedTextMessages(olderThanId);
    }

    public Notification getNotificationById(String notificationId) throws NotificationClientException {
        return notificationClient.getNotificationById(notificationId);
    }
}
