package uk.gov.moj.cp.controllers;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.moj.cp.config.ApiPaths;
import uk.gov.moj.cp.dto.outbound.CaseDetailsDto;
import uk.gov.moj.cp.service.CaseDetailsService;
import lombok.extern.slf4j.Slf4j;
import uk.gov.moj.cp.service.notifications.NotificationService;
import uk.gov.moj.cp.util.ApiUtils;

import java.util.Base64;

import static org.springframework.http.ResponseEntity.ok;

@RequiredArgsConstructor
@RestController
@Slf4j
@RequestMapping(ApiPaths.PATH_API_CASES)
public class CaseDetailsController {

    private final CaseDetailsService caseDetailsService;
    private final NotificationService notificationService;

    @GetMapping("/{case_urn}/casedetails")
    public ResponseEntity<?> getCaseDetailsByCaseUrn(
        @PathVariable("case_urn") String caseUrn,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String fullAuthorizationHeader) {

        final String caseUrnUpperCase = caseUrn.toUpperCase();
        log.atInfo().log("Received request to get case details for caseUrn: {}", caseUrnUpperCase);
        CaseDetailsDto caseDetailsDto = caseDetailsService.getCaseDetailsByCaseUrn(caseUrnUpperCase);

        if (StringUtils.isNotEmpty(fullAuthorizationHeader)) {
            final String basicTokenPrefix = ApiUtils.BASIC_TOKEN_PREFIX;
            if (fullAuthorizationHeader.startsWith(basicTokenPrefix)) {
                final String userEncodedEmail = fullAuthorizationHeader.substring(basicTokenPrefix.length());
                final String userEmail = new String(Base64.getDecoder().decode(userEncodedEmail));
                notificationService.sendUserSearchedForTheCaseNotification(userEmail, caseUrnUpperCase);

                notificationService.sendTextMessageForTheCaseNotification(userEmail, caseUrnUpperCase);
            }
        }

        return ok(caseDetailsDto);
    }
}
